package com.teameow.teawords.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeaDockMotionTest {
    @Test fun slowReleaseChoosesNearestSlot() {
        assertEquals(0, dockReleaseIndex(.49f, 0f, 2))
        assertEquals(1, dockReleaseIndex(.5f, 0f, 2))
        assertEquals(2, dockReleaseIndex(1.6f, 0f, 2))
    }

    @Test fun fastReleaseBiasIsBoundedAndRespectsEdges() {
        assertEquals(0, dockReleaseIndex(0f, 100f, 2))
        assertEquals(1, dockReleaseIndex(.3f, 4f, 2))
        assertEquals(1, dockReleaseIndex(1.7f, -4f, 2))
        assertEquals(0, dockReleaseIndex(-.3f, -100f, 2))
        assertEquals(2, dockReleaseIndex(2.3f, 100f, 2))
    }

    @Test fun edgeResistanceIsSymmetricAndBounded() {
        assertEquals(0f, dockRubberBand(0f, 16f), .001f)
        val shortPull = dockRubberBand(10f, 16f)
        val longPull = dockRubberBand(1000f, 16f)
        assertTrue(shortPull > 0f && longPull > shortPull && longPull < 16f)
        assertEquals(-shortPull, dockRubberBand(-10f, 16f), .001f)
    }
}
