package com.example.corridainteligente.domain

import com.example.corridainteligente.data.Criteria
import java.util.Locale

data class Offer(
    val fare: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val pickupMin: Double,
    val tripMin: Double,
) {
    val totalKm get() = pickupKm + tripKm
    val totalMin get() = pickupMin + tripMin   // ir buscar + levar
}

enum class Verdict { ACCEPT, MAYBE, REJECT }

data class Analysis(
    val offer: Offer,
    val earned: Double,      // valor considerado (bruto ou líquido, conforme config)
    val perKm: Double,
    val perHour: Double,
    val verdict: Verdict,
    val reasons: List<String>,
)

object Evaluator {
    fun evaluate(o: Offer, c: Criteria): Analysis {
        val earned = if (c.useNet) o.fare - c.costPerKm * o.totalKm else o.fare
        val perKm = if (o.totalKm > 0) earned / o.totalKm else 0.0
        val perHour = if (o.totalMin > 0) earned / (o.totalMin / 60.0) else 0.0

        val critical = mutableListOf<String>()
        val soft = mutableListOf<String>()
        if (perKm < c.minPerKm) critical += "R$/km ${perKm.f()} abaixo do mínimo ${c.minPerKm.f()}"
        if (perHour < c.minPerHour) critical += "R$/h ${perHour.f()} abaixo do mínimo ${c.minPerHour.f()}"
        if (o.fare < c.minFare) soft += "Valor ${o.fare.f()} abaixo do mínimo ${c.minFare.f()}"
        if (o.pickupKm > c.maxPickupKm) soft += "Busca de ${o.pickupKm.f()} km (máx. ${c.maxPickupKm.f()})"

        val verdict = when {
            critical.isEmpty() && soft.isEmpty() -> Verdict.ACCEPT
            critical.isEmpty() -> Verdict.MAYBE
            critical.size == 1 && soft.isEmpty() -> Verdict.MAYBE
            else -> Verdict.REJECT
        }
        val reasons = (critical + soft).ifEmpty { listOf("Atende a todos os seus critérios") }
        return Analysis(o, earned, perKm, perHour, verdict, reasons)
    }
}

fun Double.f(): String = String.format(Locale("pt", "BR"), "%.2f", this)
fun Double.brl(): String = "R$ " + f()
