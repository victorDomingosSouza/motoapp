package com.example.corridainteligente.domain

import com.example.corridainteligente.data.Ride
import java.util.Calendar
import kotlin.math.roundToLong

data class HourStat(val hour: Int, val perMin: Double, val count: Int)
data class RegionStat(val lat: Double, val lng: Double, val perKm: Double, val count: Int)

data class Stats(
    val count: Int = 0,
    val total: Double = 0.0,
    val avgPerKm: Double = 0.0,
    val avgPerMin: Double = 0.0,
    val bestHours: List<HourStat> = emptyList(),
    val bestRegions: List<RegionStat> = emptyList(),
)

object StatsCalculator {
    fun compute(rides: List<Ride>): Stats {
        if (rides.isEmpty()) return Stats()
        val total = rides.sumOf { it.fare }
        val km = rides.sumOf { it.totalKm }
        val minutes = rides.sumOf { it.totalMin }

        val bestHours = rides.groupBy {
            Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)
        }.map { (h, list) ->
            val min = list.sumOf { it.totalMin }
            HourStat(h, if (min > 0) list.sumOf { it.fare } / min else 0.0, list.size)
        }.sortedByDescending { it.perMin }.take(5)

        // Agrupa em células de ~1 km (0,01° de lat/lng)
        val bestRegions = rides.filter { it.lat != null && it.lng != null }
            .groupBy { (it.lat!! * 100).roundToLong() to (it.lng!! * 100).roundToLong() }
            .map { (cell, list) ->
                val k = list.sumOf { it.totalKm }
                RegionStat(cell.first / 100.0, cell.second / 100.0,
                    if (k > 0) list.sumOf { it.fare } / k else 0.0, list.size)
            }.sortedByDescending { it.perKm }

        return Stats(
            count = rides.size, total = total,
            avgPerKm = if (km > 0) total / km else 0.0,
            avgPerMin = if (minutes > 0) total / minutes else 0.0,
            bestHours = bestHours, bestRegions = bestRegions,
        )
    }
}
