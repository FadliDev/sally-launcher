package com.sally.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** ELF sintetis kecil: cukup untuk menguji pembacaan tabel dinamis tanpa library asli. */
internal object ElfBuilder {
    private const val EHDR = 64
    private const val PHDR = 56

    fun build(soname: String?, needed: List<String>, vaddrBias: Long = 0L, machine: Int = 0xB7): ByteArray {
        val str = ByteArrayOutputStream().apply { write(0) }
        fun add(name: String): Long {
            val at = str.size().toLong()
            str.write(name.toByteArray(Charsets.ISO_8859_1))
            str.write(0)
            return at
        }
        val neededAt = needed.map { add(it) }
        val sonameAt = soname?.let { add(it) }
        val strBytes = str.toByteArray()

        val strOff = EHDR + 2 * PHDR
        val dynOff = (strOff + strBytes.size + 7) / 8 * 8
        val entries = ArrayList<Pair<Long, Long>>()
        neededAt.forEach { entries.add(1L to it) }
        sonameAt?.let { entries.add(14L to it) }
        entries.add(5L to (vaddrBias + strOff))
        entries.add(0L to 0L)
        val dynSize = entries.size * 16
        val total = dynOff + dynSize

        val buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
        buf.put(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte(), 2, 1, 1))
        buf.putShort(18, machine.toShort())
        buf.putLong(0x20, EHDR.toLong())   // e_phoff
        buf.putShort(0x36, PHDR.toShort()) // e_phentsize
        buf.putShort(0x38, 2)              // e_phnum

        fun phdr(index: Int, type: Int, offset: Long, vaddr: Long, size: Long) {
            val b = EHDR + index * PHDR
            buf.putInt(b, type)
            buf.putLong(b + 8, offset)
            buf.putLong(b + 16, vaddr)
            buf.putLong(b + 32, size)
        }
        phdr(0, 1, 0, vaddrBias, total.toLong())                       // PT_LOAD
        phdr(1, 2, dynOff.toLong(), vaddrBias + dynOff, dynSize.toLong()) // PT_DYNAMIC

        buf.position(strOff)
        buf.put(strBytes)
        var p = dynOff
        for ((tag, value) in entries) {
            buf.putLong(p, tag)
            buf.putLong(p + 8, value)
            p += 16
        }
        return buf.array()
    }
}

class ElfReaderTest {

    private fun read(bytes: ByteArray) = ElfReader.read(BytesElfSource(bytes))

    @Test fun readsNeededAndSoname() {
        val info = read(ElfBuilder.build("libfoo.so", listOf("libc.so", "libbar.so")))
        assertEquals("libfoo.so", info.soname)
        assertEquals(listOf("libc.so", "libbar.so"), info.needed)
    }

    @Test fun worksWhenVirtualAddressDiffersFromFileOffset() {
        val info = read(ElfBuilder.build("libx.so", listOf("liby.so"), vaddrBias = 0x1000))
        assertEquals("libx.so", info.soname)
        assertEquals(listOf("liby.so"), info.needed)
    }

    @Test fun libraryWithoutSonameOrNeeded() {
        val info = read(ElfBuilder.build(null, emptyList()))
        assertNull(info.soname)
        assertEquals(emptyList<String>(), info.needed)
    }

    @Test fun rejectsWrongMachine() {
        try {
            read(ElfBuilder.build("a.so", emptyList(), machine = 0x3E))
            fail("harus menolak ELF x86_64")
        } catch (_: ElfFormatException) {
        }
    }

    @Test fun rejectsTruncatedAndGarbage() {
        try {
            read(ByteArray(10))
            fail("terlalu kecil")
        } catch (_: ElfFormatException) {
        }
        val good = ElfBuilder.build("a.so", listOf("b.so"))
        try {
            read(good.copyOf(good.size - 40)) // potong di tengah tabel dinamis
            fail("terpotong")
        } catch (_: ElfFormatException) {
        }
    }
}
