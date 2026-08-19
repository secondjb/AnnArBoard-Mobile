package io.github.secondjb.annarboard.widget

import io.github.secondjb.annarboard.network.Constants
import io.github.secondjb.annarboard.network.RetrofitClient

data class BusArrivalItem(
    val route: String,
    val minutes: Int,
    val stopName: String,
    val destination: String
)

object MBusWidgetRepository {

    suspend fun fetchArrivals(campus: String): List<BusArrivalItem> {
        val (stopIds, allowedRoutes) = when (campus.lowercase()) {
            "north_to_central", "north" -> {
                val stops = listOf("N553", "N551", "N550", "N405", "N406").joinToString(",")
                val routes = Constants.ROUTE_MAP["north-to-central"] ?: listOf("CS", "NW", "WX")
                stops to routes
            }
            else -> { // Default: central_to_north
                val stops = listOf("C250", "C251").joinToString(",")
                val routes = Constants.ROUTE_MAP["central-to-north"] ?: listOf("CN", "NW", "BB", "NE", "WX")
                stops to routes
            }
        }

        val response = RetrofitClient.instance.getMBusPredictions(stopIds)
        val prdList = response.bustimeResponse?.prd ?: emptyList()

        val filtered = prdList.filter { allowedRoutes.contains(it.rt) }

        return filtered.map { prd ->
            val mins = if (prd.prdctdn.equals("DUE", ignoreCase = true)) {
                0
            } else {
                prd.prdctdn.toIntOrNull() ?: 0
            }
            BusArrivalItem(
                route = prd.rt,
                minutes = mins,
                stopName = cleanStopName(prd.stpnm),
                destination = cleanStopName(prd.des)
            )
        }.sortedBy { it.minutes }.take(5)
    }

    private fun cleanStopName(raw: String): String {
        return raw
            .replace("Central Campus Transit Center", "CCTC", ignoreCase = true)
            .replace("Pierpont Commons", "Pierpont", ignoreCase = true)
            .replace(Regex("Ruthven\\s+Mue?seum?s?", RegexOption.IGNORE_CASE), "Ruthven")
            .replace(":", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
