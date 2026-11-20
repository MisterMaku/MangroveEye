package com.example.mangrove.data

import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * One small square tile on Map 2 - Capture: a 4 m x 4 m square (16 m²) centred exactly on a pin's
 * GPS point (a.lat, a.lon). One tile per assessment pin — no grid, no extras.
 *
 * captured: true when the assessment yields usable footprint data (an EXIF-stored ImageFootprint,
 * or PhotoGeo with a non-empty tile grid) — i.e. the drone photo really covered this spot, so the
 * tile paints green ("Captured"). false = the spot is still "Uncaptured" (yellow).
 *
 * id: the assessment id, so the tile is tappable (tap-to-select still works from Map 2).
 */
data class PinTile(
    val id: String,
    val lat: Double,
    val lon: Double,
    val captured: Boolean,
    val status: Status
)

/**
 * Result of the coverage analysis on Map 2 - Capture, built from one 4 m x 4 m tile per
 * non-outOfReach assessment pin, measured against the selection area (the user's selection box).
 *
 * Area statistics (two states, matching the legend):
 * capturedM2   = Σ captured tile areas (16 m² each) — the part of the selection area the drone
 *                photos really covered
 * totalM2      = the selection area (boundingBox.areaM2; the 10,000 m² default box when the user
 *                has not moved it), the region the yellow/green painting is clipped to
 * uncapturedM2 = totalM2 − capturedM2 — every part of the selection area not yet captured
 * photos       = number of captured tiles (photos that contributed usable footprint data)
 */
data class CoverageResult(
    val hasMap: Boolean = false,
    val tiles: List<PinTile> = emptyList(),   // every drawn tile, captured or not (Map 2 overlay)
    val capturedM2: Double = 0.0,
    val uncapturedM2: Double = 0.0,
    val totalM2: Double = 0.0,
    val photos: Int = 0                       // photos that contributed a footprint (= captured tiles)
) {
    val capturedPct: Int
        get() = if (totalM2 > 0) ((capturedM2 / totalM2) * 100.0).roundToInt().coerceIn(0, 100) else 0
}

/**
 * Map 2 - Image Capture Tracking: one 4 m x 4 m square tile per assessment pin, centred on the
 * pin's exact GPS coordinates. A tile is green ("Captured") when its assessment has usable
 * footprint data; every part of the selection area without a captured tile paints yellow
 * ("Uncaptured"). The captured tile area (16 m², the ground footprint at the 4 m flight height)
 * is subtracted from the selection area to give the uncaptured area. Recomputed whenever a new
 * photo is analyzed, so the map updates live.
 */
object CoverageTracker {
    /** Tile edge length in metres: 4 m x 4 m = 16 m² at the 4 m flight height. */
    const val TILE_M = 4.0

    fun build(assessments: List<Assessment>, ortho: OrthoSource?, boundingBox: GeoRotatableBox? = null): CoverageResult {
        if (ortho == null) return CoverageResult()
        if (ortho.north - ortho.south <= 0 || ortho.east - ortho.west <= 0) return CoverageResult()

        val boxSet = boundingBox != null && !boundingBox.isDefault
        val tiles = mutableListOf<PinTile>()
        var capturedCount = 0

        for (a in assessments) {
            if (a.outOfReach) continue                 // outside the map: no tile

            // A pin counts as captured when its assessment yields usable footprint data:
            // either an EXIF-stored ImageFootprint, or PhotoGeo with a non-empty tile grid.
            val hasFootprint = ImageFootprint.parse(a.imageFootprint) != null || run {
                val g = PhotoGeo.parse(a.geo)
                g != null && a.grid.split("/").any { it.isNotEmpty() }
            }

            // With a user selection box set, only tiles whose centre (the pin itself) is inside
            // the box are counted and drawn.
            if (boxSet && !boundingBox!!.contains(a.lat, a.lon)) continue

            if (hasFootprint) capturedCount++
            tiles.add(PinTile(a.id, a.lat, a.lon, hasFootprint, a.status))
        }

        // The selection area is what captured/uncaptured are measured against: the user's
        // selection box when set (Map 2 always passes one, defaulting to the 10,000 m² starter
        // size), otherwise the QGIS map extent as a rough fallback.
        val selectionAreaM2 = when {
            boundingBox != null -> boundingBox.areaM2
            else -> (ortho.north - ortho.south) * 111_320.0 * (ortho.east - ortho.west) *
                    111_320.0 * cos(Math.toRadians((ortho.north + ortho.south) / 2.0))
        }
        val tileAreaM2 = TILE_M * TILE_M
        val capturedM2 = capturedCount * tileAreaM2
        return CoverageResult(
            hasMap = true,
            tiles = tiles,
            capturedM2 = capturedM2,
            uncapturedM2 = (selectionAreaM2 - capturedM2).coerceAtLeast(0.0),
            totalM2 = selectionAreaM2,
            photos = capturedCount
        )
    }
}
