package com.example.corridainteligente.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Critérios configuráveis pelo motorista. */
data class Criteria(
    val minPerKm: Double = 1.50,     // R$/km mínimo
    val minPerHour: Double = 25.0,   // R$/hora mínimo
    val minFare: Double = 6.0,       // valor mínimo da corrida
    val maxPickupKm: Double = 3.0,   // distância máxima até o passageiro
    val costPerKm: Double = 0.55,    // custo (combustível + manutenção) por km
    val useNet: Boolean = true,      // calcular ganhos já descontando o custo por km
)

class SettingsRepository(private val context: Context) {
    private object K {
        val minPerKm = doublePreferencesKey("min_per_km")
        val minPerHour = doublePreferencesKey("min_per_hour")
        val minFare = doublePreferencesKey("min_fare")
        val maxPickupKm = doublePreferencesKey("max_pickup_km")
        val costPerKm = doublePreferencesKey("cost_per_km")
        val useNet = booleanPreferencesKey("use_net")
    }

    val criteria: Flow<Criteria> = context.dataStore.data.map { p ->
        val d = Criteria()
        Criteria(
            minPerKm = p[K.minPerKm] ?: d.minPerKm,
            minPerHour = p[K.minPerHour] ?: d.minPerHour,
            minFare = p[K.minFare] ?: d.minFare,
            maxPickupKm = p[K.maxPickupKm] ?: d.maxPickupKm,
            costPerKm = p[K.costPerKm] ?: d.costPerKm,
            useNet = p[K.useNet] ?: d.useNet,
        )
    }

    suspend fun save(c: Criteria) {
        context.dataStore.edit {
            it[K.minPerKm] = c.minPerKm
            it[K.minPerHour] = c.minPerHour
            it[K.minFare] = c.minFare
            it[K.maxPickupKm] = c.maxPickupKm
            it[K.costPerKm] = c.costPerKm
            it[K.useNet] = c.useNet
        }
    }
}
