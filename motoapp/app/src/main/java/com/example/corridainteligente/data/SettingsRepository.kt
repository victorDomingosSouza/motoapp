package com.example.corridainteligente.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")
private const val LEGACY_ACCEPT_SIGNALS = "iniciar corrida, iniciar viagem, cheguei, buscar passageiro, ir até o passageiro"

/** Critérios configuráveis pelo motorista. */
data class Criteria(
    val minPerKm: Double = 1.50,     // R$/km mínimo
    val minPerMin: Double = 0.42,    // R$/min mínimo (0,42 ≈ R$ 25/h)
    val minFare: Double = 6.0,       // valor mínimo da corrida
    val maxPickupKm: Double = 3.0,   // distância máxima até o passageiro
    val costPerKm: Double = 0.55,    // custo (combustível + manutenção) por km
    val useNet: Boolean = true,      // calcular ganhos já descontando o custo por km
    // Textos que, aparecendo na tela depois de uma oferta, indicam "corrida aceita".
    // Calibre com a tela do seu app logo após aceitar.
    val acceptSignals: String =
        "corrida aceita, $LEGACY_ACCEPT_SIGNALS",
)

class SettingsRepository(private val context: Context) {
    private object K {
        val minPerKm = doublePreferencesKey("min_per_km")
        val minPerMin = doublePreferencesKey("min_per_min")
        val minFare = doublePreferencesKey("min_fare")
        val maxPickupKm = doublePreferencesKey("max_pickup_km")
        val costPerKm = doublePreferencesKey("cost_per_km")
        val useNet = booleanPreferencesKey("use_net")
        val acceptSignals = stringPreferencesKey("accept_signals")
    }

    val criteria: Flow<Criteria> = context.dataStore.data.map { p ->
        val d = Criteria()
        val signals = p[K.acceptSignals] ?: d.acceptSignals
        Criteria(
            minPerKm = p[K.minPerKm] ?: d.minPerKm,
            minPerMin = p[K.minPerMin] ?: d.minPerMin,
            minFare = p[K.minFare] ?: d.minFare,
            maxPickupKm = p[K.maxPickupKm] ?: d.maxPickupKm,
            costPerKm = p[K.costPerKm] ?: d.costPerKm,
            useNet = p[K.useNet] ?: d.useNet,
            acceptSignals = if (signals == LEGACY_ACCEPT_SIGNALS) d.acceptSignals else signals,
        )
    }

    suspend fun save(c: Criteria) {
        context.dataStore.edit {
            it[K.minPerKm] = c.minPerKm
            it[K.minPerMin] = c.minPerMin
            it[K.minFare] = c.minFare
            it[K.maxPickupKm] = c.maxPickupKm
            it[K.costPerKm] = c.costPerKm
            it[K.useNet] = c.useNet
            it[K.acceptSignals] = c.acceptSignals
        }
    }
}
