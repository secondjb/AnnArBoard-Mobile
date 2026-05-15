package com.example.annarboard.network

data class Hub(
    val key: String,
    val name: String,
    val stopIds: List<String>,
    val keywords: List<String>
)

object Constants {
    val HUBS = listOf(
        Hub("cctc", "Central Campus (CCTC)", listOf("C250", "C251"), listOf("Central", "CCTC")),
        Hub("pierpont", "Pierpont Commons", listOf("N553", "N551", "N550"), listOf("North Campus", "Pierpont")),
        Hub("union", "Michigan Union", listOf("C200", "C201"), listOf("Union", "State")),
        Hub("bursley", "Bursley Hall", listOf("N407", "N408"), listOf("Bursley", "Baits")),
        Hub("fxb", "FXB (North Campus)", listOf("N405", "N406"), listOf("FXB", "Aero")),
        Hub("crisler", "Crisler Center", listOf("S002", "S001"), listOf("Crisler", "Lot SC-5")),
        Hub("hospital", "Mott/Hospital", listOf("M314", "N400", "M324"), listOf("Hospital", "Mott")),
        Hub("fuller", "Mitchell Field/Fuller", listOf("N450", "N451", "N452", "M350"), listOf("Mitchell", "Fuller")),
        Hub("art", "Art & Architecture", listOf("N552"), listOf("Art", "Architecture")),
        Hub("commonwealth", "Commonwealth/N-Wood", listOf("E604", "N417"), listOf("Commonwealth", "Cram"))
    )

    val ROUTE_MAP = mapOf(
        "central-to-north" to listOf("CN", "NW", "BB", "NE", "WX"),
        "north-to-central" to listOf("CS", "NW", "WX"),
        "north-to-hospital" to listOf("CS", "IC"),
        "central-to-hospital" to listOf("CN", "BB")
    )
}
