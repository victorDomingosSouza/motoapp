package com.example.corridainteligente.domain

import org.junit.Assert.*
import org.junit.Test

class ScreenChangeDetectorTest {
    @Test fun screenReplacementInvalidatesInFlightOcr() {
        val detector = ScreenChangeDetector()
        detector.sample(IntArray(100), 0.1f)
        val capturedGeneration = detector.generation
        assertTrue(detector.sample(IntArray(100) { 200 }, 0.1f))
        assertNotEquals(capturedGeneration, detector.generation)
    }

    @Test fun identicalFramesKeepGenerationStable() {
        val detector = ScreenChangeDetector()
        detector.sample(IntArray(100) { 100 }, 0.1f)
        assertFalse(detector.sample(IntArray(100) { 100 }, 0.1f))
        assertEquals(0L, detector.generation)
    }

    @Test fun gradualChangesAccumulateUntilThreshold() {
        val detector = ScreenChangeDetector()
        detector.sample(IntArray(100), 0.1f)
        assertFalse(detector.sample(IntArray(100) { if (it < 5) 100 else 0 }, 0.1f))
        assertTrue(detector.sample(IntArray(100) { if (it < 11) 100 else 0 }, 0.1f))
    }

    @Test fun overlayMaskChangeResetsBaselineWithoutInvalidatingOcr() {
        val detector = ScreenChangeDetector()
        detector.sample(IntArray(100), 0.1f)
        assertFalse(detector.sample(IntArray(100) { 200 }, 0.1f, resetBaseline = true))
        assertEquals(0L, detector.generation)
        assertFalse(detector.sample(IntArray(100) { 200 }, 0.1f))
    }

    @Test fun mapAnimationDoesNotInvalidateOfferOcr() {
        val detector = ScreenChangeDetector(ignoreTopHalf = true)
        detector.sample(IntArray(100), 0.18f)
        assertFalse(detector.sample(IntArray(100) { if (it < 50) 200 else 0 }, 0.18f))
        assertEquals(0L, detector.generation)
    }

    @Test fun smallCountdownChangeDoesNotInvalidateOfferOcr() {
        val detector = ScreenChangeDetector(ignoreTopHalf = true)
        detector.sample(IntArray(100), 0.18f)
        assertFalse(detector.sample(IntArray(100) { if (it == 70) 200 else 0 }, 0.18f))
        assertEquals(0L, detector.generation)
    }

    @Test fun replacingOfferCardStillInvalidatesOldOcr() {
        val detector = ScreenChangeDetector(ignoreTopHalf = true)
        detector.sample(IntArray(100), 0.18f)
        assertTrue(detector.sample(IntArray(100) { if (it >= 50) 200 else 0 }, 0.18f))
        assertEquals(1L, detector.generation)
    }
}
