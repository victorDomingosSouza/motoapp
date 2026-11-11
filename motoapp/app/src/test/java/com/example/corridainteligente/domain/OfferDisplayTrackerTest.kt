package com.example.corridainteligente.domain

import org.junit.Assert.*
import org.junit.Test

class OfferDisplayTrackerTest {
    private val form = OfferForm("19.5", "1.6", "9.5", "6", "25")

    private fun stable(tracker: OfferDisplayTracker) {
        assertNull(tracker.observe(form, 0, 1))
        assertEquals(form, tracker.observe(form, 100, 2))
    }

    @Test fun repeatedOfferSurvivesMapAndCountdownUpdates() {
        val tracker = OfferDisplayTracker()
        stable(tracker)
        repeat(20) { index ->
            assertEquals(form, tracker.observe(form, 200L + index * 100, 3L + index))
        }
    }

    @Test fun briefIncompleteReadsDoNotBlink() {
        val tracker = OfferDisplayTracker()
        stable(tracker)
        assertEquals(form, tracker.observe(null, 200, 3))
        assertEquals(form, tracker.observe(null, 500, 4))
        assertEquals(form, tracker.observe(form, 600, 5))
    }

    @Test fun sustainedAbsenceHidesOffer() {
        val tracker = OfferDisplayTracker()
        stable(tracker)
        tracker.observe(null, 500, 3)
        assertNull(tracker.observe(null, 1700, 4))
        assertFalse(tracker.needsConfirmation)
    }

    @Test fun idleScreenDoesNotRequestContinuousOcr() {
        val tracker = OfferDisplayTracker()
        tracker.observe(null, 100, 1)
        assertFalse(tracker.needsConfirmation)
    }

    @Test fun newOfferImmediatelyRemovesOldValuesAndRequiresConfirmation() {
        val tracker = OfferDisplayTracker()
        stable(tracker)
        val next = form.copy(fare = "30")
        assertNull(tracker.observe(next, 200, 3))
        assertEquals(next, tracker.observe(next, 300, 4))
    }

    @Test fun incompleteReplacementWithDifferentFareRemovesOldOffer() {
        val tracker = OfferDisplayTracker()
        stable(tracker)
        assertNull(tracker.observe(null, 200, 3, OfferForm(fare = "30")))
    }

    @Test fun repeatedFrameCannotConfirmNewOffer() {
        val tracker = OfferDisplayTracker()
        tracker.observe(form, 0, 1)
        assertNull(tracker.observe(form, 100, 1))
    }

    @Test fun formattingAndMissingLabelsDoNotReplaceSameOffer() {
        val tracker = OfferDisplayTracker()
        val labeled = form.copy(stopCount = 1, negotiable = true)
        tracker.observe(labeled, 0, 1)
        tracker.observe(labeled, 100, 2)
        val visible = tracker.observe(form.copy(fare = "19,50"), 200, 3)!!
        assertEquals(1, visible.stopCount)
        assertTrue(visible.negotiable)
    }
}
