package com.sally.launcher.runtime

import com.sally.launcher.data.versions.ElfCheck
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Isi yang dibutuhkan dari satu library: nama sistemnya dan library lain yang diperlukannya. */
data class ElfInfo(val soname: String?, val needed: List<String>)

class ElfFormatException(message: String) : Exception(message)

/** Sumber byte dengan akses acak, supaya berkas ratusan MB tidak perlu dibaca utuh. */
interface ElfSource {
    val length: Long
    fun read(position: Long, size: Int): ByteArray
}

class BytesElfSource(private val bytes: ByteArray) : ElfSource {
    override val length: Long get() = bytes.size.toLong()
    override fun read(position: Long, size: Int): ByteArray {
        if (position < 0 || size < 0 || position + size > bytes.size) {
            throw ElfFormatException("Baca di luar berkas")
        }
        return bytes.copyOfRange(position.toInt(), position.toInt() + size)
    }
}

class FileElfSource(file: File) : ElfSource, Closeable {
    private val raf = RandomAccessFile(file, "r")
    override val length: Long = raf.length()
    override fun read(position: Long, size: Int): ByteArray {
        if (position < 0 || size < 0 || position + size > length) {
            throw ElfFormatException("Baca di luar berkas")
        }
        val out = ByteArray(size)
        raf.seek(position)
        raf.readFully(out)
        return out
    }
    override fun close() = raf.close()
}

/**
 * Membaca tabel dinamis ELF 64-bit little-endian (DT_NEEDED dan DT_SONAME) tanpa memuat library.
 * Dipakai untuk menentukan urutan muat yang benar dari isi library itu sendiri, bukan dari
 * daftar nama yang ditulis tangan per versi game.
 */
object ElfReader {
    private const val PT_LOAD = 1
    private const val PT_DYNAMIC = 2
    private const val DT_NULL = 0L
    private const val DT_NEEDED = 1L
    private const val DT_STRTAB = 5L
    private const val DT_SONAME = 14L

    private const val EHDR_SIZE = 64
    private const val PHDR_SIZE = 56
    private const val MAX_PHDRS = 256
    private const val MAX_DYNAMIC_BYTES = 1 shl 20
    private const val MAX_NAME_CHARS = 4096
    private const val CHUNK = 256

    private class Segment(val offset: Long, val vaddr: Long, val fileSize: Long)

    @Throws(IOException::class, ElfFormatException::class)
    fun read(file: File): ElfInfo = FileElfSource(file).use { read(it) }

    @Throws(ElfFormatException::class)
    fun read(source: ElfSource): ElfInfo {
        if (source.length < EHDR_SIZE) throw ElfFormatException("Berkas terlalu kecil untuk ELF")
        val header = source.read(0, EHDR_SIZE)
        if (!ElfCheck.isArm64(header)) throw ElfFormatException("Bukan ELF arm64 64-bit")

        val h = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        val phOff = h.getLong(0x20)
        val phEntSize = h.getShort(0x36).toInt() and 0xFFFF
        val phNum = h.getShort(0x38).toInt() and 0xFFFF
        if (phEntSize < PHDR_SIZE || phNum == 0 || phNum > MAX_PHDRS) {
            throw ElfFormatException("Tabel program header tidak wajar")
        }

        val table = ByteBuffer.wrap(source.read(phOff, phEntSize * phNum)).order(ByteOrder.LITTLE_ENDIAN)
        val loads = ArrayList<Segment>()
        var dynamic: Segment? = null
        for (i in 0 until phNum) {
            val base = i * phEntSize
            val type = table.getInt(base)
            val offset = table.getLong(base + 8)
            val vaddr = table.getLong(base + 16)
            val fileSize = table.getLong(base + 32)
            when (type) {
                PT_LOAD -> loads.add(Segment(offset, vaddr, fileSize))
                PT_DYNAMIC -> dynamic = Segment(offset, vaddr, fileSize)
            }
        }
        val dyn = dynamic ?: return ElfInfo(null, emptyList())
        if (dyn.fileSize <= 0 || dyn.fileSize > MAX_DYNAMIC_BYTES) {
            throw ElfFormatException("Ukuran tabel dinamis tidak wajar")
        }

        val entries = ByteBuffer.wrap(source.read(dyn.offset, dyn.fileSize.toInt())).order(ByteOrder.LITTLE_ENDIAN)
        val neededOffsets = ArrayList<Long>()
        var strTabVaddr = -1L
        var sonameOffset = -1L
        var pos = 0
        while (pos + 16 <= entries.limit()) {
            val tag = entries.getLong(pos)
            val value = entries.getLong(pos + 8)
            pos += 16
            if (tag == DT_NULL) break
            when (tag) {
                DT_NEEDED -> neededOffsets.add(value)
                DT_STRTAB -> strTabVaddr = value
                DT_SONAME -> sonameOffset = value
            }
        }
        if (neededOffsets.isEmpty() && sonameOffset < 0) return ElfInfo(null, emptyList())
        if (strTabVaddr < 0) throw ElfFormatException("DT_STRTAB tidak ada")
        val strTab = vaddrToOffset(loads, strTabVaddr)
            ?: throw ElfFormatException("DT_STRTAB di luar segmen yang dimuat")

        val needed = neededOffsets.map { readName(source, strTab + it) }
        val soname = if (sonameOffset >= 0) readName(source, strTab + sonameOffset) else null
        return ElfInfo(soname, needed)
    }

    private fun vaddrToOffset(loads: List<Segment>, vaddr: Long): Long? {
        for (s in loads) {
            if (vaddr >= s.vaddr && vaddr < s.vaddr + s.fileSize) return vaddr - s.vaddr + s.offset
        }
        return null
    }

    /** Baca string C bertahap; berhenti di NUL atau di batas panjang. */
    private fun readName(source: ElfSource, start: Long): String {
        val out = StringBuilder()
        var pos = start
        while (out.length < MAX_NAME_CHARS) {
            if (pos >= source.length) throw ElfFormatException("Nama library terpotong")
            val size = minOf(CHUNK.toLong(), source.length - pos).toInt()
            val chunk = source.read(pos, size)
            for (b in chunk) {
                if (b.toInt() == 0) return out.toString()
                out.append((b.toInt() and 0xFF).toChar())
            }
            pos += size
        }
        throw ElfFormatException("Nama library terlalu panjang")
    }
}
