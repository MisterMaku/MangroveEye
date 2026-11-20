package com.example.mangrove.data

import java.util.Date

/**
 * Information about a QGIS map for better organization and tracking.
 */
data class MapInfo(
    val name: String = "",
    val location: String = "",
    val description: String = "",
    val additionalInfo: String = "",   // any other relevant details the user entered about the map
    val creationDate: Long = 0,
    val sourceType: MapSourceType = MapSourceType.QGIS,
    val totalAreaSqm: Double = 0.0     // total study area in square metres, as entered by the user (0 = not entered)
) {
    val creationDateStr: String
        get() = if (creationDate > 0) {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            sdf.format(Date(creationDate))
        } else ""
}

/**
 * Source type for the map
 */
enum class MapSourceType(val label: String) {
    QGIS("QGIS Orthomosaic"),
    ORTHOMOSAIC("Orthomosaic"),
    CUSTOM("Custom Map")
}