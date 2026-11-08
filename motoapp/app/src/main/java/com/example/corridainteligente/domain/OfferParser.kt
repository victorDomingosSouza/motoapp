package com.example.corridainteligente.domain

/** Campos (como texto) preenchidos a partir do OCR; vazio = não encontrado. */
data class OfferForm(
    val fare: String = "",
    val pickupKm: String = "",
    val tripKm: String = "",
    val pickupMin: String = "",
    val tripMin: String = "",
) {
    private fun String.num() = replace(',', '.').toDoubleOrNull()
    fun toOffer(): Offer? {
        val f = fare.num() ?: return null
        val pk = pickupKm.num() ?: 0.0
        val tk = tripKm.num() ?: return null
        val pm = pickupMin.num() ?: 0.0
        val tm = tripMin.num() ?: return null
        if (f <= 0 || tk + pk <= 0 || tm + pm <= 0) return null
        return Offer(f, pk, tk, pm, tm)
    }
}

/**
 * Interpreta o texto reconhecido de uma tela de oferta. Padrão comum nos apps:
 *   R$ 12,50 ... 5 min (1,8 km) ... 14 min (6,2 km)
 * 1º trecho = ir buscar; 2º trecho = levar. Se só houver um trecho, assume que é a viagem.
 * Ajuste as regex ao layout do app que você usa.
 */
object OfferParser {
    private val money = Regex("""R\$\s*(\d+(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val leg = Regex("""(\d+)\s*min[^\d]{0,6}(\d+(?:[.,]\d+)?)\s*km""", RegexOption.IGNORE_CASE)

    fun parse(text: String): OfferForm {
        val fare = money.find(text)?.groupValues?.get(1).orEmpty()
        val legs = leg.findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()
        return when {
            legs.size >= 2 -> OfferForm(fare, legs[0].second, legs[1].second, legs[0].first, legs[1].first)
            legs.size == 1 -> OfferForm(fare, "0", legs[0].second, "0", legs[0].first)
            else -> OfferForm(fare = fare)
        }
    }
}
