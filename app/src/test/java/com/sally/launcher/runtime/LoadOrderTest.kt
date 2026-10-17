package com.sally.launcher.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LoadOrderTest {

    private val system = setOf("libc.so", "libm.so", "liblog.so", "libandroid.so")
    private fun plan(libs: Map<String, ElfInfo>) = LoadOrder.compute(libs) { it in system }

    private fun lib(soname: String?, vararg needed: String) = ElfInfo(soname, needed.toList())

    @Test fun dependenciesComeFirst() {
        val result = plan(
            mapOf(
                "libminecraftpe.so" to lib("libminecraftpe.so", "libc++_shared.so", "libfmod.so", "libc.so"),
                "libfmod.so" to lib("libfmod.so", "libc++_shared.so", "libm.so"),
                "libc++_shared.so" to lib("libc++_shared.so", "libc.so"),
            ),
        )
        assertEquals(listOf("libc++_shared.so", "libfmod.so", "libminecraftpe.so"), result.order)
        assertTrue(result.unresolved.isEmpty())
        assertEquals(setOf("libc++_shared.so", "libfmod.so"), result.requires["libminecraftpe.so"])
    }

    @Test fun matchesDependencyBySonameWhenFileNameDiffers() {
        val result = plan(
            mapOf(
                "a.so" to lib("a.so", "libreal.so"),
                "renamed.so" to lib("libreal.so"),
            ),
        )
        assertEquals(listOf("renamed.so", "a.so"), result.order)
    }

    @Test fun reportsUnresolvedNonSystemDependencies() {
        val result = plan(
            mapOf(
                "libminecraftpe.so" to lib("libminecraftpe.so", "libmaesdk.so", "libPlayFabMultiplayer.so", "libc.so"),
                "libother.so" to lib("libother.so", "libmaesdk.so"),
            ),
        )
        assertEquals(setOf("libmaesdk.so", "libPlayFabMultiplayer.so"), result.unresolved.keys)
        assertEquals(listOf("libminecraftpe.so", "libother.so"), result.unresolved.getValue("libmaesdk.so"))
        assertEquals(setOf("libminecraftpe.so", "libother.so"), result.blockedByUnresolved)
    }

    @Test fun orderIsDeterministicForIndependentLibraries() {
        val libs = mapOf("libb.so" to lib("libb.so"), "liba.so" to lib("liba.so"), "libc2.so" to lib("libc2.so"))
        assertEquals(listOf("liba.so", "libb.so", "libc2.so"), plan(libs).order)
    }

    @Test fun detectsCycles() {
        try {
            plan(mapOf("a.so" to lib("a.so", "b.so"), "b.so" to lib("b.so", "a.so")))
            fail("harus melempar LoadCycleException")
        } catch (e: LoadCycleException) {
            assertEquals(listOf("a.so", "b.so", "a.so"), e.chain)
        }
    }

    @Test fun reachableFollowsOnlyDependenciesOfRoot() {
        val libs = mapOf(
            "libminecraftpe.so" to lib("libminecraftpe.so", "libfmod.so", "libreal.so", "libc.so"),
            "libfmod.so" to lib("libfmod.so", "libc++_shared.so"),
            "libc++_shared.so" to lib("libc++_shared.so"),
            "renamed.so" to lib("libreal.so"),
            "libpairipcore.so" to lib("libpairipcore.so", "libc.so"),
        )
        assertEquals(
            setOf("libminecraftpe.so", "libfmod.so", "libc++_shared.so", "renamed.so"),
            LoadOrder.reachable(libs, "libminecraftpe.so"),
        )
    }

    @Test fun reachableWithoutRootKeepsEverything() {
        val libs = mapOf("a.so" to lib("a.so"), "b.so" to lib("b.so"))
        assertEquals(setOf("a.so", "b.so"), LoadOrder.reachable(libs, "libminecraftpe.so"))
    }

    @Test fun reachableSurvivesCycles() {
        val libs = mapOf("a.so" to lib("a.so", "b.so"), "b.so" to lib("b.so", "a.so"))
        assertEquals(setOf("a.so", "b.so"), LoadOrder.reachable(libs, "a.so"))
    }

    @Test fun selfReferenceIsIgnored() {
        val result = plan(mapOf("a.so" to lib("a.so", "a.so")))
        assertEquals(listOf("a.so"), result.order)
    }
}
