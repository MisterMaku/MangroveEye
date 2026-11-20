package com.example.mangrove.data

import kotlin.math.max
import kotlin.math.sqrt

/**
 * One point in the health-density heatmap: a classified tile's centre plus its Healthy / Unhealthy
 * share, used to paint a soft blob on the map.
 */
data class HeatmapPoint(
    val lat: Double, val lon: Double,
    val healthyPct: Int,      // 0-100, share of the tile that is Healthy
    val unhealthyPct: Int,    // 0-100, share of the tile that is Unhealthy
    val north: Double, val south: Double, val west: Double, val east: Double,  // tile bounds (for pixel sizing)
    val radiusM: Double       // blob radius in metres (so neighbouring blobs overlap)
)

/**
 * Result of building the health-density heatmap from the tile GIS layer.
 */
data class HeatmapResult(
    val points: List<HeatmapPoint>,
    val healthyM2: Double,
    val unhealthyM2: Double,
    val totalM2: Double
) {
    val healthyPct: Int get() = if (totalM2 > 0) ((healthyM2 / totalM2) * 100).toInt().coerceIn(0, 100) else 0
    val unhealthyPct: Int get() = if (totalM2 > 0) ((unhealthyM2 / totalM2) * 100).toInt().coerceIn(0, 100) else 0
}

/**
 * One cluster in the GIS health-density visualization: an actual AI assessment location with its
 * Healthy / Unhealthy result, used to paint a smooth red or green density blob at that exact GPS
 * point. Unlike the tile-based [HeatmapPoint], each assessment produces its own cluster, so the
 * GIS view shows one intensity field per assessment rather than one per 512 px tile.
 */
data class GisPoint(
    val lat: Double, val lon: Double,
    val healthyPct: Int,   // 0-100, Healthy share of the assessment
    val unhealthyPct: Int, // 0-100, Unhealthy share of the assessment
    val radiusM: Double    // blob radius in metres (so neighbouring assessments overlap)
)

/**
 * Result of building the GIS health-density visualization from the assessment log.
 */
data class GisResult(
    val points: List<GisPoint>,
    val healthyCount: Int,
    val unhealthyCount: Int,
    val total: Int
) {
    val healthyPct: Int get() = if (total > 0) (healthyCount * 100 / total).coerceIn(0, 100) else 0
    val unhealthyPct: Int get() = if (total > 0) (unhealthyCount * 100 / total).coerceIn(0, 100) else 0
}

/**
 * Builds the GIS health-density visualization from the assessment log.
 *
 * Every in-reach assessment becomes one density cluster at its real GPS point: green for Healthy,
 * red for Unhealthy. The radius grows with the ground area behind the assessment (tile size x AI
 * pieces), so neighbouring assessments overlap and blend into the continuous density fields shown
 * on the GIS view. Out of reach assessments (outside the QGIS map, no AI prediction) are skipped -
 * they have no cluster.
 */
object GisBuilder {
    private const val RADIUS_FACTOR = 1.8

    fun build(assessments: List<Assessment>): GisResult {
        val points = mutableListOf<GisPoint>()
        var healthyCount = 0
        var unhealthyCount = 0
        for (a in assessments) {
            if (a.outOfReach) continue
            val g = PhotoGeo.parse(a.geo) ?: continue
            // Radius is a LENGTH (metres): sqrt of the photo's ground area times the overlap factor.
            // The old line used the area (m²) directly as a radius, making blobs ~5x too wide.
            val areaM2 = max(g.tileM * g.tileM * g.pieces.coerceAtLeast(1), 1.0)
            val radiusM = sqrt(areaM2) * 0.5 * RADIUS_FACTOR
            points.add(GisPoint(a.lat, a.lon, a.healthy, a.unhealthy, radiusM))
            when (a.status) {
                Status.HEALTHY -> healthyCount++
                Status.UNHEALTHY -> unhealthyCount++
                else -> {}
            }
        }
        return GisResult(points, healthyCount, unhealthyCount, points.size)
    }
}

/**
 * Builds the smooth health-density heatmap from the classified tiles.
 *
 * Each classified 512 px tile becomes one soft blob centred on the tile's ground centre:
 *  - green for the Healthy share, red for the Unhealthy share
 * The blob radius is a little over half a tile, so neighbouring blobs overlap and blend into
 * the continuous density fields shown on the Heatmap view.
 */
object HeatmapBuilder {
    private const val RADIUS_FACTOR = 1.6    // blob radius = tile size * this (so blobs overlap)

    fun build(features: List<TileFeature>): HeatmapResult {
        val points = mutableListOf<HeatmapPoint>()
        var healthyM2 = 0.0
        var unhealthyM2 = 0.0
        var totalM2 = 0.0

        for (f in features) {
            if (f.healthyPct == 0 && f.unhealthyPct == 0) continue
            // Radius is a LENGTH (metres): sqrt of the tile's area times the overlap factor —
            // "a little over half a tile" as the comment says. The old line used the area (m²)
            // directly as a radius, making each blob ~5x wider than a tile.
            val radiusM = sqrt(max(f.areaM2, 1.0)) * 0.5 * RADIUS_FACTOR
            points.add(HeatmapPoint(
                lat = f.centerLat, lon = f.centerLon,
                healthyPct = f.healthyPct, unhealthyPct = f.unhealthyPct,
                north = f.north, south = f.south, west = f.west, east = f.east,
                radiusM = radiusM
            ))
            totalM2 += f.areaM2
            healthyM2 += f.areaM2 * f.healthyPct / 100.0
            unhealthyM2 += f.areaM2 * f.unhealthyPct / 100.0
        }
        return HeatmapResult(points, healthyM2, unhealthyM2, totalM2)
    }
}