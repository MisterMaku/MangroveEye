package com.example.mangrove.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds the tile GIS layer from the assessments in the log.
 *
 * For every classified 512 px tile of every photo (the per-tile "h:u" counts in Assessment.tiles)
 * this creates one Polygon feature with geographic bounds calculated from PhotoGeo (EXIF-based):
 *
 * 1. Tile centre relative to the photo centre, in metres (grid position x tile size)
 * 2. Rotated by the photo heading
 * 3. Converted to lat/lon offsets around the photo's GPS point
 * 4. Bounding box = centre +/- half a tile in each direction
 *
 * Georeferencing is an EXIF-based estimate (flight height + focal length + gimbal yaw), good for
 * visualisation; for survey-grade GIS work, match the tiles against the orthomosaic instead.
 */
object TileGisBuilder {

    /** Main entry point: builds TileGisResult from all assessments. */
    fun build(assessments: List<Assessment>): TileGisResult {
        val allFeatures = mutableListOf<TileFeature>()
        var totalM2 = 0.0
        var healthyM2 = 0.0
        var unhealthyM2 = 0.0

        for (a in assessments) {
            // Photos outside the QGIS map have no AI result and no valid ground reference
            if (a.outOfReach) continue
            val g = PhotoGeo.parse(a.geo) ?: continue
            if (a.lat !in -90.0..90.0 || a.lon !in -180.0..180.0) continue

            // One "h:u" pair per 512 px tile, rows split by "/" (same layout as the tile map)
            val tileRows = a.tiles.split("/")
            for (r in tileRows.indices) {
                val cells = tileRows[r].split(",")
                for (c in cells.indices) {
                    val parts = cells[c].trim().split(":")
                    val nH = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: continue
                    val nU = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: continue
                    val np = nH + nU
                    val cls = when {
                        np == 0 -> Status.NON_MANGROVE
                        nH >= nU -> Status.HEALTHY
                        else -> Status.UNHEALTHY
                    }
                    val healthyPct = if (np > 0) (nH * 100.0 / np).toInt() else 0
                    val unhealthyPct = if (np > 0) (nU * 100.0 / np).toInt() else 0
                    val areaM2 = g.tileM * g.tileM

                    val b = tileBounds(a.lat, a.lon, r, c, g) ?: continue
                    allFeatures.add(TileFeature(
                        id = "${a.id}_r${r}_c${c}",
                        imageId = a.id,
                        imageFilename = a.name,
                        imageTime = a.timeMillis,
                        tileRow = r,
                        tileCol = c,
                        tileRowPx = r * Config.TILE_SIZE,
                        tileColPx = c * Config.TILE_SIZE,
                        classification = cls,
                        healthyPct = healthyPct,
                        unhealthyPct = unhealthyPct,
                        nonMangrovePct = (100 - healthyPct - unhealthyPct).coerceAtLeast(0),
                        centerLat = b.centerLat,
                        centerLon = b.centerLon,
                        north = b.north,
                        south = b.south,
                        west = b.west,
                        east = b.east,
                        areaM2 = areaM2,
                        healthyPieces = nH,
                        unhealthyPieces = nU,
                        totalPieces = np
                    ))

                    totalM2 += areaM2
                    when (cls) {
                        Status.HEALTHY -> healthyM2 += areaM2 * healthyPct / 100.0
                        Status.UNHEALTHY -> unhealthyM2 += areaM2 * unhealthyPct / 100.0
                        else -> {}
                    }
                }
            }
        }

        return TileGisResult(
            photos = assessments.count { !it.outOfReach },
            tilesCount = allFeatures.size,
            totalM2 = totalM2,
            healthyM2 = healthyM2,
            unhealthyM2 = unhealthyM2,
            otherM2 = (totalM2 - healthyM2 - unhealthyM2).coerceAtLeast(0.0),
            features = allFeatures,
            geoJson = toJson(allFeatures)
        )
    }

    /**
     * Geographic bounding box of one tile inside a photo.
     * Same math as Assessment.tileInfo: grid offset -> metres -> rotated by heading -> lat/lon.
     */
    private fun tileBounds(lat: Double, lon: Double, row: Int, col: Int, g: PhotoGeo): TileBounds? {
        return try {
            val right = (col + 0.5 - g.cx) * g.tileM          // metres right of the photo centre
            val fwd = (g.cy - (row + 0.5)) * g.tileM          // metres towards the top of the photo
            val h = Math.toRadians(g.heading)
            val east = right * cos(h) + fwd * sin(h)
            val north = fwd * cos(h) - right * sin(h)

            val centerLat = lat + north / 111_320.0
            val centerLon = lon + east / (111_320.0 * cos(Math.toRadians(lat)))

            // Bounding box of the (possibly rotated) tile: a square of side tileM rotated by h has an
            // axis-aligned bounding box of half-extent halfM * (|cos h| + |sin h|) on BOTH axes — at
            // heading 0 the box is the tile itself, at 45° it is at its largest. The old per-axis
            // cos/sin split collapsed the box to zero width at heading 0 and to zero height at 90°.
            val halfM = g.tileM / 2.0 * (abs(cos(h)) + abs(sin(h)))
            val halfLat = halfM / 111_320.0
            val halfLon = halfM / (111_320.0 * cos(Math.toRadians(centerLat)))
            TileBounds(
                north = centerLat + halfLat,
                south = centerLat - halfLat,
                west = centerLon - halfLon,
                east = centerLon + halfLon,
                centerLat = centerLat,
                centerLon = centerLon
            )
        } catch (e: Exception) {
            android.util.Log.e("TileGisBuilder", "Tile bounds failed (row $row, col $col): ${e.message}")
            null
        }
    }

    /** Converts the tile features to a GeoJSON FeatureCollection string. */
    private fun toJson(features: List<TileFeature>): String = try {
        val featuresJson = JSONArray()
        for (f in features) {
            if (!isValidGeo(f.north, f.south, f.west, f.east)) continue
            val feature = JSONObject()
            feature.put("type", "Feature")

            val geometry = JSONObject()
            geometry.put("type", "Polygon")
            val ring = JSONArray()
            // Ring: NW -> NE -> SE -> SW -> close (lon, lat order, as GeoJSON requires)
            ring.put(JSONArray().put(f.west).put(f.north))
            ring.put(JSONArray().put(f.east).put(f.north))
            ring.put(JSONArray().put(f.east).put(f.south))
            ring.put(JSONArray().put(f.west).put(f.south))
            ring.put(JSONArray().put(f.west).put(f.north))
            geometry.put("coordinates", ring)
            feature.put("geometry", geometry)

            val props = JSONObject()
            props.put("id", f.id)
            props.put("image_id", f.imageId)
            props.put("image_filename", f.imageFilename)
            props.put("image_time", f.imageTime)
            props.put("tile_row", f.tileRow)
            props.put("tile_col", f.tileCol)
            props.put("tile_row_px", f.tileRowPx)
            props.put("tile_col_px", f.tileColPx)
            props.put("predicted_class", f.classification.label)
            props.put("probability_healthy", f.healthyPct / 100.0)
            props.put("probability_unhealthy", f.unhealthyPct / 100.0)
            props.put("probability_non_mangrove", f.nonMangrovePct / 100.0)
            props.put("area_m2", f.areaM2)
            props.put("healthy_pieces", f.healthyPieces)
            props.put("unhealthy_pieces", f.unhealthyPieces)
            props.put("total_pieces", f.totalPieces)
            props.put("georeferencing_method", "EXIF-based estimation (flight height + focal length + gimbal yaw)")
            feature.put("properties", props)
            featuresJson.put(feature)
        }
        JSONObject().put("type", "FeatureCollection").put("features", featuresJson).toString(2)
    } catch (e: Exception) {
        throw IllegalStateException("Failed to export GeoJSON", e)
    }

    private fun isValidGeo(north: Double, south: Double, west: Double, east: Double): Boolean =
        north > south && east > west &&
                north != Double.POSITIVE_INFINITY && north != Double.NEGATIVE_INFINITY &&
                south != Double.POSITIVE_INFINITY && south != Double.NEGATIVE_INFINITY &&
                west != Double.POSITIVE_INFINITY && west != Double.NEGATIVE_INFINITY &&
                east != Double.POSITIVE_INFINITY && east != Double.NEGATIVE_INFINITY

    /** Polygon bounds of one tile. */
    private data class TileBounds(
        val north: Double, val south: Double, val west: Double, val east: Double,
        val centerLat: Double, val centerLon: Double
    )
}

/** One classified tile as a map polygon / GeoJSON feature. */
data class TileFeature(
    val id: String,
    val imageId: String,
    val imageFilename: String,
    val imageTime: Long,
    val tileRow: Int,
    val tileCol: Int,
    val tileRowPx: Int,
    val tileColPx: Int,
    val classification: Status,
    val healthyPct: Int,
    val unhealthyPct: Int,
    val nonMangrovePct: Int,
    val centerLat: Double,
    val centerLon: Double,
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double,
    val areaM2: Double,
    val healthyPieces: Int,
    val unhealthyPieces: Int,
    val totalPieces: Int
)

/** Result of building the tile GIS layer: statistics, features for the map, and the GeoJSON export. */
data class TileGisResult(
    val photos: Int,
    val tilesCount: Int,
    val totalM2: Double,
    val healthyM2: Double,
    val unhealthyM2: Double,
    val otherM2: Double,
    val features: List<TileFeature>,
    val geoJson: String
)
