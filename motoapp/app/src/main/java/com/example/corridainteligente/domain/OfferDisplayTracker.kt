package com.example.corridainteligente.domain

/** Mantém a faixa durante pequenas falhas de OCR e confirma ofertas novas em capturas distintas. */
class OfferDisplayTracker(private val absenceGraceMs: Long = 1500L) {
    var visible: OfferForm? = null
        private set
    private var candidate: OfferForm? = null
    private var candidateReads = 0
    private var lastFrame = Long.MIN_VALUE
    private var lastValidAt = 0L
    private var absentReads = 0

    val needsConfirmation get() = candidate != null || (visible != null && absentReads > 0)

    fun observe(form: OfferForm?, now: Long, frame: Long, partial: OfferForm? = form): OfferForm? {
        if (frame <= lastFrame) return visible
        lastFrame = frame
        if (form == null) {
            val oldFare = visible?.fare?.replace(',', '.')?.toDoubleOrNull()
            val newFare = partial?.fare?.replace(',', '.')?.toDoubleOrNull()
            if (newFare != null && oldFare != null && newFare != oldFare) visible = null
            candidate = null
            candidateReads = 0
            absentReads++
            if (absentReads >= 2 && now - lastValidAt >= absenceGraceMs) visible = null
            return visible
        }
        absentReads = 0
        lastValidAt = now
        val old = visible
        if (old != null && sameMetrics(form, old)) {
            visible = form.copy(stopCount = maxOf(form.stopCount, old.stopCount), negotiable = form.negotiable || old.negotiable)
            return visible
        }
        visible = null
        if (form == candidate) candidateReads++ else {
            candidate = form
            candidateReads = 1
        }
        if (candidateReads >= 2) {
            visible = form
            candidate = null
            candidateReads = 0
        }
        return visible
    }

    private fun sameMetrics(first: OfferForm, second: OfferForm): Boolean {
        val a = first.toOffer()?.copy(stopCount = 0, negotiable = false)
        val b = second.toOffer()?.copy(stopCount = 0, negotiable = false)
        return a != null && a == b
    }

    fun clear() {
        visible = null
        candidate = null
        candidateReads = 0
        absentReads = 0
    }
}
