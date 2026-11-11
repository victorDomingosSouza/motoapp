package com.example.corridainteligente.domain

import java.util.UUID

/** Confirma o desaparecimento da oferta e o sinal de aceite em capturas distintas. */
class AcceptanceTracker(private val windowMs: Long = 60_000L) {
    data class Event(val id: String, val form: OfferForm, val analysis: Analysis)

    private var lastFrame = Long.MIN_VALUE
    private var candidate: OfferForm? = null
    private var reads = 0
    private var pending: Event? = null
    private var lastSeen = 0L
    private var signalOnOffer = false
    private var acceptanceReads = 0
    private var confirmed: Event? = null

    val hasPending get() = pending != null

    fun observe(form: OfferForm?, analysis: Analysis?, signal: Boolean, now: Long, frame: Long): Event? {
        confirmed?.let { return it }
        if (frame <= lastFrame) return null
        lastFrame = frame
        if (now - lastSeen > windowMs) pending = null

        if (form != null) {
            if (analysis == null) {
                pending = null
                candidate = null
                reads = 0
                acceptanceReads = 0
                return null
            }
            acceptanceReads = 0
            if (candidate != form) {
                candidate = form
                reads = 0
                pending = null
                signalOnOffer = signal
            }
            signalOnOffer = signalOnOffer || signal
            reads++
            lastSeen = now
            if (reads >= 2 && pending == null) pending = Event(UUID.randomUUID().toString(), form, analysis)
            return null
        }

        candidate = null
        reads = 0
        if (pending == null || signalOnOffer || !signal) {
            acceptanceReads = 0
            return null
        }
        acceptanceReads++
        if (acceptanceReads >= 2) confirmed = pending
        return confirmed
    }

    fun recorded() {
        pending = null
        confirmed = null
        candidate = null
        reads = 0
        acceptanceReads = 0
    }
}
