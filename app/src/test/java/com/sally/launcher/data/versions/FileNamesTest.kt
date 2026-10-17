package com.sally.launcher.data.versions

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {

    @Test fun safeKeepsOnlyTheFileName() {
        assertEquals("c.apk", FileNames.safe("a b/c.apk", "x.apk"))
        assertEquals("passwd", FileNames.safe("../../etc/passwd", "x.apk"))
        assertEquals("evil.apk", FileNames.safe("C:\\dir\\evil.apk", "x.apk"))
    }

    @Test fun safeReplacesUnsafeCharacters() {
        assertEquals("split_config.arm64_v8a.apk", FileNames.safe("split config.arm64_v8a.apk", "x.apk"))
    }

    @Test fun safeFallsBackWhenEmpty() {
        assertEquals("x.apk", FileNames.safe(null, "x.apk"))
        assertEquals("x.apk", FileNames.safe("...", "x.apk"))
    }

    @Test fun uniqueAddsCounterBeforeExtension() {
        val taken = setOf("a.apk", "a-2.apk")
        assertEquals("a-3.apk", FileNames.unique("a.apk") { it in taken })
        assertEquals("b.apk", FileNames.unique("b.apk") { it in taken })
    }

    @Test fun uniqueHandlesNamesWithoutExtension() {
        assertEquals("file-2", FileNames.unique("file") { it == "file" })
    }
}
