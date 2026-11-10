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
 * Interpreta o texto reconhecido de uma tela de oferta. Exemplo (app 99):
 *   R$23,30 / R$1,83/km / R$2,82 Tarifa base dinâmica incl. / 8 min (2,2 km) / 26 min (10,5 km)
 *
 * - Valor da corrida = MAIOR "R$ x" que não seja "R$ x/km" (ignora o R$/km do app e a tarifa dinâmica).
 * - 1º trecho "N min (X km)" = ir buscar; 2º = levar. Aceita também "1 h 5 min (30 km)".
 */
object OfferParser {
    // `(?:\$|S)` aceita o "$" lido como "S" pelo OCR; os lookaheads descartam "R$ 1,83/km".
    private val money = Regex("""(?<![A-Za-z])R(?:\$|S)\s*(\d{1,4}(?:[.,]\d{1,2})?)(?![\d,.])(?!\s*/)""", RegexOption.IGNORE_CASE)
    private val leg = Regex("""(?:(\d+)\s*h\s*)?(\d+)\s*min[^\d]{0,6}(\d+(?:[.,]\d+)?)\s*km""", RegexOption.IGNORE_CASE)

    fun parse(text: String): OfferForm {
        val fare = money.findAll(text).map { it.groupValues[1] }
            .maxByOrNull { it.replace(',', '.').toDoubleOrNull() ?: 0.0 }.orEmpty()

        val legs = leg.findAll(text).map {
            val minutes = (it.groupValues[1].toIntOrNull() ?: 0) * 60 + it.groupValues[2].toInt()
            minutes.toString() to it.groupValues[3]          // (minutos, km)
        }.toList()

        return when {
            legs.size >= 2 -> OfferForm(fare, legs[0].second, legs[1].second, legs[0].first, legs[1].first)
            legs.size == 1 -> OfferForm(fare, "0", legs[0].second, "0", legs[0].first)
            else -> OfferForm(fare = fare)
        }
    }
}
