package com.example.corridainteligente.domain

/** Campos (como texto) preenchidos a partir do OCR; vazio = não encontrado. */
data class OfferForm(
    val fare: String = "",
    val pickupKm: String = "",
    val tripKm: String = "",
    val pickupMin: String = "",
    val tripMin: String = "",
    val stopCount: Int = 0,
    val negotiable: Boolean = false,
) {
    private fun String.num() = replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    fun toOffer(): Offer? {
        val f = fare.num() ?: return null
        val pk = pickupKm.num() ?: return null
        val tk = tripKm.num() ?: return null
        val pm = pickupMin.num() ?: return null
        val tm = tripMin.num() ?: return null
        if (f <= 0 || pk < 0 || tk < 0 || pm < 0 || tm < 0 || stopCount < 0) return null
        if (!(tk + pk).isFinite() || !(tm + pm).isFinite() || tk + pk <= 0 || tm + pm <= 0) return null
        return Offer(f, pk, tk, pm, tm, stopCount, negotiable)
    }
}

/**
 * Interpreta o texto reconhecido de uma tela de oferta. Exemplo (app 99):
 *   R$23,30 / R$1,83/km / R$2,82 Tarifa base dinâmica incl. / 8 min (2,2 km) / 26 min (10,5 km)
 *
 * - Ignora taxas por unidade e parcelas identificadas como tarifa base/dinâmica.
 * - Valores monetários ambíguos deixam o valor da corrida pendente de revisão.
 * - 1º trecho "N min (X km)" = ir buscar; 2º = levar. Aceita também "1 h 5 min (30 km)".
 */
object OfferParser {
    // `(?:\$|S)` aceita o "$" lido como "S" pelo OCR; os lookaheads descartam "R$ 1,83/km".
    private val money = Regex("""(?<![A-Za-z])R(?:\$|S)\s*(\d{1,4}(?:[.,]\d{1,2})?)(?![\d,.])(?!\s*/\s*(?:km|min|m|h)\b)""", RegexOption.IGNORE_CASE)
    private val leg = Regex("""(?:(\d+)\s*h\s*)?(\d+)\s*min[^\d]{0,6}(\d+(?:[.,]\d+)?)\s*(km|m)\b""", RegexOption.IGNORE_CASE)
    private val header = Regex("""\b(?:negocia|pgto\.?\s+no\s+app|dinheiro|prioritario)\b""")
    private val acceptFare = Regex("""\baceitar\s+por\s+R(?:\$|S)\s*(\d{1,4}(?:[.,]\d{1,2})?)(?![\d,.])""", RegexOption.IGNORE_CASE)
    private val stops = Regex("""\b(\d+)\s+parada(?:\(s\)|s)?""")
    private val negotiation = Regex("""\bnegocia\b""")

    fun parse(text: String): OfferForm {
        val normalized = text.normalized().replace('\u00a0', ' ')
        val offerText = header.find(normalized)?.let { normalized.substring(it.range.first) } ?: normalized
        val firstLeg = leg.find(offerText)?.range?.first ?: offerText.length
        val amounts = money.findAll(offerText).filter { match ->
            val suffix = offerText.substring(match.range.last + 1).trimStart()
            val lineBefore = offerText.substring(0, match.range.first).substringAfterLast('\n')
            val previousLine = offerText.substring(0, match.range.first).trimEnd().substringAfterLast('\n')
            match.range.first < firstLeg &&
                !suffix.startsWith("tarifa base") && !suffix.startsWith("tarifa dinamica") &&
                !suffix.startsWith("taxa de parada") && !lineBefore.contains("taxa de parada") &&
                !previousLine.contains("ganho") && !previousLine.contains("taxa de parada") &&
                (match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0
        }.map { it.groupValues[1] }.distinctBy { it.replace(',', '.').toDoubleOrNull() }.toList()
        val actionFare = acceptFare.findAll(offerText).map { it.groupValues[1] }
            .distinctBy { it.replace(',', '.').toDoubleOrNull() }.singleOrNull()
        val fare = (actionFare ?: amounts.singleOrNull())?.replace(',', '.')?.toDoubleOrNull()?.toString().orEmpty()
        val stopCount = stops.find(offerText)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val negotiable = negotiation.containsMatchIn(offerText)

        val legs = leg.findAll(offerText).mapNotNull {
            val hours = it.groupValues[1].ifEmpty { "0" }.toLongOrNull() ?: return@mapNotNull null
            val mins = it.groupValues[2].toLongOrNull() ?: return@mapNotNull null
            if (hours > (Int.MAX_VALUE - mins) / 60L || mins > Int.MAX_VALUE) return@mapNotNull null
            val minutes = hours * 60L + mins
            val distance = it.groupValues[3].replace(',', '.').toDoubleOrNull()
                ?.takeIf { value -> value.isFinite() } ?: return@mapNotNull null
            val km = if (it.groupValues[4].equals("m", ignoreCase = true)) distance / 1000.0 else distance
            minutes.toString() to km.toString()
        }.toList()

        val form = when {
            legs.size == 2 -> OfferForm(fare, legs[0].second, legs[1].second, legs[0].first, legs[1].first)
            stopCount > 0 && legs.size.toLong() == stopCount.toLong() + 2 -> OfferForm(
                fare, legs[0].second, legs.drop(1).sumOf { it.second.toDouble() }.toString(),
                legs[0].first, legs.drop(1).sumOf { it.first.toDouble() }.toString(),
            )
            legs.size == 1 -> OfferForm(fare = fare, tripKm = legs[0].second, tripMin = legs[0].first)
            else -> OfferForm(fare = fare)
        }
        return form.copy(stopCount = stopCount, negotiable = negotiable)
    }
}
