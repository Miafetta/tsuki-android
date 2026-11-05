package cafe.miafetta.tsuki.ui.glass

import org.junit.Assert.*
import org.junit.Test

class MiGlassBackendTest {
    @Test fun capabilitiesAreSharedAcrossInstances() {
        val methods = MiGlassCapability.methods
        assertEquals(8, methods.size)
        repeat(10) { assertSame(methods, MiGlassCapability.methods) }
    }

    @Test fun rectangleInitializesFullyWithoutCapsuleEnhancement() {
        val calls = linkedMapOf<String, List<Any>>()
        val session = GlassSession(rounded = false) { name, args ->
            calls[name] = args.toList()
            true
        }
        assertTrue(session.update(true, 30f, 1080, 280))
        assertEquals(initialization, calls.keys.toList())
        assertEquals(listOf(0, 12288), calls["setMiBackgroundBlurEnhanceFlag"])
        assertEquals(listOf(1080f, 280f), calls["setMiGlassSdfMaxSize"])
        val params = calls.getValue("setMiGlass").single() as FloatArray
        assertEquals(42, params.size)
        assertEquals(1f, params[19], 0f)
        assertEquals(2f, miGlassParams()[19], 0f)
    }
    @Test fun selectionHonorsDisableAndNativePriority() {
        assertEquals("opaque", glassBackend(false, true, true, true))
        assertEquals("inactive", glassBackend(true, false, true, true))
        assertEquals("MiGlass", glassBackend(true, true, true, true))
        assertEquals("miuix-blur", glassBackend(true, true, false, true))
        assertEquals("opaque", glassBackend(true, true, false, false))
    }
    private val initialization = listOf("clearMiBackgroundBlendColor", "setMiBackgroundBlurMode", "setMiViewBlurMode",
        "setMiViewMaterialType", "setMiGlass", "setMiGlassBlurRadius", "setMiBackgroundBlurEnhanceFlag", "setMiGlassSdfMaxSize")
    private val cleanup = listOf("setMiViewMaterialType", "setMiGlassBlurRadius", "setMiBackgroundBlurEnhanceFlag",
        "clearMiBackgroundBlendColor", "setMiViewBlurMode", "setMiBackgroundBlurMode")

    @Test fun everyPartialFailureCleansBeforeFallbackAndIsNotRetried() {
        initialization.forEach { failure ->
            val calls = mutableListOf<String>()
            val session = GlassSession { name, _ ->
                calls.add(name)
                name != failure
            }
            assertFalse(session.update(true, 24f, 100, 40))
            assertEquals(cleanup, calls.takeLast(6))
            assertTrue(session.failed)
            assertFalse(session.applied)
            val count = calls.size
            repeat(10) { assertFalse(session.update(true, 24f, 100, 40)) }
            assertEquals(count, calls.size)
        }
    }
    @Test fun fullInitializationIsRequiredAndStableFramesDoNotCallNative() {
        val calls = mutableListOf<String>()
        val session = GlassSession { name, _ ->
            calls.add(name)
            true
        }
        assertTrue(session.update(true, 24f, 100, 40))
        assertEquals(initialization, calls)
        repeat(10) { assertTrue(session.update(true, 24f, 100, 40)) }
        assertEquals(initialization, calls)
        assertTrue(session.update(true, 24f, 120, 40))
        assertEquals("setMiGlassSdfMaxSize", calls.last())
    }
    @Test fun runtimeFailureClearsAndDisablingAlwaysClears() {
        for (failure in listOf("setMiGlassBlurRadius", "setMiGlassSdfMaxSize")) {
            var fail = false
            val calls = mutableListOf<String>()
            val session = GlassSession { name, _ ->
                calls.add(name)
                !(fail && name == failure)
            }
            assertTrue(session.update(true, 24f, 100, 40))
            fail = true
            assertFalse(session.update(true, 20f, 120, 40))
            assertEquals(cleanup, calls.takeLast(6))
            assertFalse(session.update(false, 20f, 120, 40))
            assertEquals(cleanup, calls.takeLast(6))
        }
    }
    @Test fun disabledOrUnmeasuredNeverInitializes() {
        val calls = mutableListOf<String>()
        val session = GlassSession { name, _ ->
            calls.add(name)
            true
        }
        assertFalse(session.update(false, 24f, 100, 40))
        assertFalse(session.update(true, 24f, 0, 40))
        assertFalse(calls.contains("setMiGlass"))
        assertTrue(session.update(true, 24f, 100, 40))
        assertFalse(session.update(false, 24f, 100, 40))
        assertFalse(session.applied)
        assertEquals(cleanup, calls.takeLast(6))
    }
}
