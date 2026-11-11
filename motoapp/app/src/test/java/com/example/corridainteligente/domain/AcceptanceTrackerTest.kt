package com.example.corridainteligente.domain

import com.example.corridainteligente.data.Criteria
import org.junit.Assert.*
import org.junit.Test

class AcceptanceTrackerTest {
    private val form = OfferForm("20", "1", "9", "2", "18")
    private val analysis = Evaluator.evaluate(form.toOffer()!!, Criteria())

    private fun stable(tracker: AcceptanceTracker) {
        tracker.observe(form, analysis, false, 0, 1)
        tracker.observe(form, analysis, false, 500, 2)
    }

    @Test fun requiresTwoFreshAcceptanceFrames() {
        val tracker = AcceptanceTracker()
        stable(tracker)
        assertNull(tracker.observe(null, null, true, 1000, 3))
        assertNull(tracker.observe(null, null, true, 1500, 3))
        assertNotNull(tracker.observe(null, null, true, 2000, 4))
    }

    @Test fun repeatedOfferFrameCannotConfirmOffer() {
        val tracker = AcceptanceTracker()
        tracker.observe(form, analysis, false, 0, 1)
        tracker.observe(form, analysis, false, 500, 1)
        tracker.observe(null, null, true, 1000, 2)
        assertNull(tracker.observe(null, null, true, 1500, 3))
    }

    @Test fun replacementClearsPreviousOfferBeforeAcceptance() {
        val tracker = AcceptanceTracker()
        stable(tracker)
        tracker.observe(form.copy(fare = "25"), analysis, true, 1000, 3)
        tracker.observe(null, null, true, 1500, 4)
        assertNull(tracker.observe(null, null, true, 2000, 5))
    }

    @Test fun incompleteReplacementCannotAcceptPreviousOffer() {
        val tracker = AcceptanceTracker()
        stable(tracker)
        tracker.observe(form.copy(fare = ""), null, true, 1000, 3)
        tracker.observe(null, null, true, 1500, 4)
        assertNull(tracker.observe(null, null, true, 2000, 5))
    }

    @Test fun signalAlreadyOnOfferDoesNotCountAsAcceptance() {
        val tracker = AcceptanceTracker()
        tracker.observe(form, analysis, true, 0, 1)
        tracker.observe(form, analysis, true, 500, 2)
        tracker.observe(null, null, true, 1000, 3)
        assertNull(tracker.observe(null, null, true, 1500, 4))
    }

    @Test fun expiredOfferCannotBeAccepted() {
        val tracker = AcceptanceTracker()
        stable(tracker)
        tracker.observe(null, null, true, 61_000, 3)
        assertNull(tracker.observe(null, null, true, 62_000, 4))
    }

    @Test fun retryKeepsEventAndAcknowledgementClearsIt() {
        val tracker = AcceptanceTracker()
        stable(tracker)
        tracker.observe(null, null, true, 1000, 3)
        val event = tracker.observe(null, null, true, 1500, 4)
        assertNotNull(event)
        assertEquals(event, tracker.observe(null, null, true, 2000, 5))
        tracker.recorded()
        assertNull(tracker.observe(null, null, true, 2500, 6))
    }

    @Test fun identicalOfferAfterDisappearanceGetsNewEvent() {
        val tracker = AcceptanceTracker()
        stable(tracker)
        tracker.observe(null, null, false, 1000, 3)
        tracker.observe(form, analysis, false, 1500, 4)
        tracker.observe(form, analysis, false, 2000, 5)
        tracker.observe(null, null, true, 2500, 6)
        assertNotNull(tracker.observe(null, null, true, 3000, 7))
    }
}
