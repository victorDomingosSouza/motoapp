package com.example.corridainteligente.domain

import com.example.corridainteligente.data.Criteria
import java.text.Normalizer
import java.util.Locale

data class Offer(
    val fare: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val pickupMin: Double,
    val tripMin: Double,
    val stopCount: Int = 0,
    val negotiable: Boolean = false,
) {
    val totalKm get() = pickupKm + tripKm
    val totalMin get() = pickupMin + tripMin   // ir buscar + levar
}

enum class Verdict { ACCEPT, MAYBE, REJECT }

data class Analysis(
    val offer: Offer,
    val earned: Double,      // valor considerado (bruto ou líquido, conforme config)
    val perKm: Double,
    val perMin: Double,
    val verdict: Verdict,
    val reasons: List<String>,
)

object Evaluator {
    fun evaluate(o: Offer, c: Criteria): Analysis {
        val earned = if (c.useNet) o.fare - c.costPerKm * o.totalKm else o.fare
        val perKm = if (o.totalKm > 0) earned / o.totalKm else 0.0
        val perMin = if (o.totalMin > 0) earned / o.totalMin else 0.0

        val critical = mutableListOf<String>()
        val soft = mutableListOf<String>()
        if (perKm < c.minPerKm) critical += "R$/km ${perKm.f()} abaixo do mínimo ${c.minPerKm.f()}"
        if (perMin < c.minPerMin) critical += "R$/min ${perMin.f()} abaixo do mínimo ${c.minPerMin.f()}"
        if (o.fare < c.minFare) soft += "Valor ${o.fare.f()} abaixo do mínimo ${c.minFare.f()}"
        if (o.pickupKm > c.maxPickupKm) soft += "Busca de ${o.pickupKm.f()} km (máx. ${c.maxPickupKm.f()})"

        val verdict = when {
            critical.isEmpty() && soft.isEmpty() -> Verdict.ACCEPT
            critical.isEmpty() -> Verdict.MAYBE
            critical.size == 1 && soft.isEmpty() -> Verdict.MAYBE
            else -> Verdict.REJECT
        }
        val reasons = (critical + soft).ifEmpty { listOf("Atende a todos os seus critérios") }
        return Analysis(o, earned, perKm, perMin, verdict, reasons)
    }
}

fun Double.f(): String = String.format(Locale("pt", "BR"), "%.2f", this)
fun Double.brl(): String = "R$ " + f()
/** 8.0 -> "8"; 2.2 -> "2,20" */
fun Double.clean(): String = if (this % 1.0 == 0.0) toLong().toString() else f()

/** minúsculas e sem acentos, para comparar textos do OCR. */
fun String.normalized(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()
