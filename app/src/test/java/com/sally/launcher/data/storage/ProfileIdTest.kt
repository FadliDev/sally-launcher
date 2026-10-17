package com.sally.launcher.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileIdTest {

    @Test fun nullAndBlankFallBackToDefault() {
        assertEquals(ProfileId.DEFAULT, ProfileId.sanitize(null))
        assertEquals(ProfileId.DEFAULT, ProfileId.sanitize("   "))
    }

    @Test fun lowercasesAndReplacesUnsafeCharacters() {
        assertEquals("1.21.80_beta", ProfileId.sanitize("1.21.80 Beta"))
    }

    @Test fun blocksPathTraversal() {
        assertEquals("default", ProfileId.sanitize("../.."))
        assertEquals("etc_passwd", ProfileId.sanitize("../etc/passwd"))
    }

    @Test fun limitsLength() {
        assertEquals(64, ProfileId.sanitize("a".repeat(200)).length)
    }
}
