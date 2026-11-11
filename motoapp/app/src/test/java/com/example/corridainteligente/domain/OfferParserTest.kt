package com.example.corridainteligente.domain

import org.junit.Assert.*
import org.junit.Test

class OfferParserTest {
    private val form = OfferForm("20", "1", "9", "2", "18")

    @Test fun readsTwoLegsAndHours() {
        val parsed = OfferParser.parse("R$ 23,30 8 min (2,2 km) 1 h 5 min (30 km)")
        assertEquals(65.0, parsed.toOffer()!!.tripMin, 0.0)
        assertEquals(2.2, parsed.toOffer()!!.pickupKm, 0.0)
    }

    @Test fun oneLegLeavesPickupUnknown() {
        val parsed = OfferParser.parse("R$ 20,00 10 min (5 km)")
        assertEquals("", parsed.pickupKm)
        assertNull(parsed.toOffer())
    }

    @Test fun rejectsNonFiniteAndNegativeFields() {
        assertNull(form.copy(fare = "NaN").toOffer())
        assertNull(form.copy(tripKm = "Infinity").toOffer())
        assertNull(form.copy(pickupKm = "-1").toOffer())
        assertNull(form.copy(pickupMin = "-1").toOffer())
        assertNull(form.copy(pickupKm = "abc").toOffer())
    }

    @Test fun oversizedDurationsDoNotThrow() {
        assertNull(OfferParser.parse("R$ 20 999999999999999999999999 min (5 km)").toOffer())
        assertNull(OfferParser.parse("R$ 20 999999999999 h 3 min (5 km)").toOffer())
    }

    @Test fun explicitZeroPickupIsValid() {
        assertNotNull(form.copy(pickupKm = "0", pickupMin = "0").toOffer())
    }

    @Test fun ignoresUnitRateAndLabeledBaseTariff() {
        val parsed = OfferParser.parse("R$23,30 / R$1,83/km / R$2,82 Tarifa base dinâmica incl. / 8 min (2,2 km) / 26 min (10,5 km)")
        assertEquals(23.3, parsed.toOffer()!!.fare, 0.0)
    }

    @Test fun ambiguousMoneyRequiresReview() {
        assertEquals("", OfferParser.parse("R$ 20,00 R$ 30,00 2 min (1 km) 18 min (9 km)").fare)
    }

    @Test fun reads99PickupInMetersWithDynamicBaseTariff() {
        val text = """
            Pgto. no app
            R$7,90
            R$2,01/km
            R$1,13 Tarifa base dinâmica incl.
            4,94 • 754 corridas
            Perfil Premium
            5 min (866 m)
            Endereço de embarque
            7 min (3,1 km)
            Endereço de destino
        """.trimIndent()
        val offer = OfferParser.parse(text).toOffer()!!
        assertEquals(7.9, offer.fare, 0.0)
        assertEquals(0.866, offer.pickupKm, 0.000001)
        assertEquals(3.1, offer.tripKm, 0.0)
        assertEquals(3.966, offer.totalKm, 0.000001)
        assertEquals(12.0, offer.totalMin, 0.0)
    }

    @Test fun supportsMetersInBothLegsAndOcrLineBreaks() {
        val offer = OfferParser.parse("R$ 8,00\n3 min\n(500 m)\n4 min\n(950 m)").toOffer()!!
        assertEquals(0.5, offer.pickupKm, 0.0)
        assertEquals(0.95, offer.tripKm, 0.0)
    }

    @Test fun readsNegociaWithoutUsingCounterofferButtons() {
        val text = """
            R$0,00
            Negocia · Dinheiro
            R$6,70
            R$1,20/km
            4,85 · 108 corridas
            8 min (2,3 km)
            Endereço de embarque
            9 min (3,2 km)
            Endereço de destino
            Aceitar por R$6,70
            R$7,04 R$7,37 R$7,71
        """.trimIndent()
        val offer = OfferParser.parse(text).toOffer()!!
        assertEquals(6.7, offer.fare, 0.0)
        assertEquals(5.5, offer.totalKm, 0.000001)
        assertEquals(17.0, offer.totalMin, 0.0)
        assertTrue(offer.negotiable)
    }

    @Test fun readsSecondNegociaLayout() {
        val offer = OfferParser.parse("""
            Negocia · Pgto. no app
            R$11,60
            R$1,75/km
            7 min (1,8 km)
            11 min (4,8 km)
            Aceitar por R$11,60
            R$12,18 R$12,76 R$13,34
        """.trimIndent()).toOffer()!!
        assertEquals(11.6, offer.fare, 0.0)
        assertEquals(6.6, offer.totalKm, 0.000001)
        assertTrue(offer.negotiable)
    }

    @Test fun stopFeeIsAlreadyIncludedInFare() {
        val offer = OfferParser.parse("""
            Pgto. no app
            R$34,30
            R$1,87/km
            R$1,41 Tarifa base dinâmica incl.
            Taxa de parada de R$0,80 inclusa
            4 min (739 m)
            Endereço de embarque
            1 parada(s)
            44 min (17,6 km)
            Endereço de destino
        """.trimIndent()).toOffer()!!
        assertEquals(34.3, offer.fare, 0.0)
        assertEquals(18.339, offer.totalKm, 0.000001)
        assertEquals(48.0, offer.totalMin, 0.0)
        assertEquals(1, offer.stopCount)
    }

    @Test fun stopFeeLabelCanFollowAmount() {
        val offer = OfferParser.parse("R$34,30\nR$0,80 Taxa de parada inclusa\n4 min (739 m)\n1 parada\n44 min (17,6 km)").toOffer()!!
        assertEquals(34.3, offer.fare, 0.0)
    }

    @Test fun sumsIndividualStopLegs() {
        val offer = OfferParser.parse("R$ 30\n2 min (1 km)\n1 parada\n10 min (3 km)\n20 min (7 km)").toOffer()!!
        assertEquals(10.0, offer.tripKm, 0.0)
        assertEquals(30.0, offer.tripMin, 0.0)
    }

    @Test fun homeAndNavigationScreensDoNotBecomeOffers() {
        assertNull(OfferParser.parse("R$0,00\nSolicitações (5)\n99 Abastece\nR$3,46 R$3,59\nBuscando").toOffer())
        assertNull(OfferParser.parse("Corrida aceita\n5 min · 1,8 km\nChegue antes de 22:03").toOffer())
    }

    @Test fun paymentHeaderExcludesOldOverlayAmounts() {
        val offer = OfferParser.parse("""
            Assistente de corridas
            R$19,50 R$1,21 R$25,93
            Dinheiro · Prioritário
            R$19,50
            R$1,76/km
            R$1,41 Tarifa base dinâmica incl.
            6 min (1,6 km)
            25 min (9,5 km)
        """.trimIndent()).toOffer()!!
        assertEquals(19.5, offer.fare, 0.0)
    }

    @Test fun ignoresOverlayGainsAndRateWithOcrMissingK() {
        val offer = OfferParser.parse("""
            Dinheiro Prioritário
            R$19,50
            R$1,76/m
            31 min R$0,43/min Ganho R$13,40
            6 min (1,6 km)
            25 min (9,5 km)
        """.trimIndent()).toOffer()!!
        assertEquals(19.5, offer.fare, 0.0)
    }

    @Test fun overlayLabelsCannotBecomeFareCandidates() {
        val offer = OfferParser.parse("""
            Assistente de corridas
            Valor da corrida
            R$7,90
            Ganho por km
            R$1,44
            Ganho por hora
            R$28,59
            12 min R$0,48/min Ganho R$5,72
            R$7,90
            R$2,01/km
            R$1,13 Tarifa base dinâmica incl.
            5 min (866 m)
            7 min (3,1 km)
        """.trimIndent()).toOffer()!!
        assertEquals(7.9, offer.fare, 0.0)
    }
}
