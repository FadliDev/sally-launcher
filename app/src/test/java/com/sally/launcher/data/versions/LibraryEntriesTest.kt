package com.sally.launcher.data.versions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryEntriesTest {

    @Test fun acceptsDirectArm64Libraries() {
        assertEquals("libminecraftpe.so", LibraryEntries.libName("lib/arm64-v8a/libminecraftpe.so"))
        assertEquals("libc++_shared.so", LibraryEntries.libName("lib/arm64-v8a/libc++_shared.so"))
    }

    @Test fun rejectsOtherAbisAndNonLibraries() {
        assertNull(LibraryEntries.libName("lib/armeabi-v7a/libx.so"))
        assertNull(LibraryEntries.libName("lib/x86_64/libx.so"))
        assertNull(LibraryEntries.libName("lib/arm64-v8a/readme.txt"))
        assertNull(LibraryEntries.libName("assets/lib/arm64-v8a/libx.so"))
    }

    @Test fun rejectsPathTricks() {
        assertNull(LibraryEntries.libName("lib/arm64-v8a/../../x.so"))
        assertNull(LibraryEntries.libName("lib/arm64-v8a/sub/libx.so"))
        assertNull(LibraryEntries.libName("lib/arm64-v8a/.hidden.so"))
        assertNull(LibraryEntries.libName("lib/arm64-v8a/..so"))
    }

    private fun header(machine: Int, cls: Int = 2, data: Int = 1): ByteArray =
        ByteArray(20).also {
            it[0] = 0x7F; it[1] = 'E'.code.toByte(); it[2] = 'L'.code.toByte(); it[3] = 'F'.code.toByte()
            it[4] = cls.toByte(); it[5] = data.toByte()
            it[18] = (machine and 0xFF).toByte(); it[19] = (machine shr 8).toByte()
        }

    @Test fun elfCheckAcceptsAarch64() {
        assertTrue(ElfCheck.isArm64(header(0xB7)))
    }

    @Test fun elfCheckRejectsWrongMachineClassOrSize() {
        assertFalse(ElfCheck.isArm64(header(0x28)))          // ARM 32-bit
        assertFalse(ElfCheck.isArm64(header(0x3E)))          // x86_64
        assertFalse(ElfCheck.isArm64(header(0xB7, cls = 1))) // 32-bit class
        assertFalse(ElfCheck.isArm64(ByteArray(10)))
    }
}
