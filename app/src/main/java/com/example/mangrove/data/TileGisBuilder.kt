package com.example.mangrove.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * private data class for tile polygon bounds.
 */
private data class TileBounds(
    val north: Double, val south: Double, val west: Double, val east: Double,
    val centerLat: Double, val centerLon: Double
)

/**
 * Builds a map based on the individual tiles of uploaded photos (replaces context grid).
 *
 * For each 224x224 pixel tile from a DJI photo, this creates a Polygon feature with
 * geographic bounds calculated from PhotoGeo (EXIF-based).
 *
 * Implementation approach:
 * 1. Parse PhotoGeo from each assessment (which contains the ground tile size, heading, and photo center grid position)
 * 2. For each grid cell in the photo, calculate the tile center GPS using a rotation matrix
 * 3. Compute polygon bounds (center ± half tile size in each cardinal direction)
 * 4. Aggregate classification and area statistics
 *
 * Current georeferencing limitations:
 * - Uses EXIF flight altitude and focal length to estimate GSD
 * - Assumes north-up presentation (no rotation correction)
 * - Photo orientation may vary from the actual camera heading
 * - For precise GIS operations, photogrammetric matching with the orthomosaic is recommended
 */
object TileGisBuilder {

    /**
     * Main entry point: builds TileGisResult from all assessments.
     *
     * @param assessments Analyzed photos with classification data
     * @return Result containing tile counts, area summaries, and a list of GeoJSON-ready features
     */
    fun build(assessments: List<Assessment>): TileGisResult {
        val allFeatures = mutableListOf<TileFeature>()
        var totalM2 = 0.0
        var healthyM2 = 0.0
        var unhealthyM2 = 0.0
        val tilesWithValidGeo = mutableSetOf<String>()

        for (assessment in assessments) {
            // Skip photos outside the QGIS map: no valid ground reference
            if (assessment.outOfReach) continue

            val photoGeo = PhotoGeo.parse(assessment.geo) ?: continue
            val pieces = photoGeo.pieces.coerceAtLeast(1)
            val tileAreaM2 = photoGeo.tileM * photoGeo.tileM / (pieces * pieces)

            val rows = assessment.grid.split("/")

            // Process each 512x512 grid cell, which contains 4 pieces (256x256 tiles)
            for (rowIdx in rows.indices) {
                val rowStr = rows[rowIdx]
                for (colIdx in rowStr.indices) {
                    val cellStr = rowStr[colIdx].toString() // single character: H/U/N

                    // Parse healthy/unhealthy counts from this cell (2 values, e.g., "12:3")
                    val parts = cellStr.split(":")
                    if (parts.size < 2) continue
                    val nH = parts[0].toIntOrNull() ?: 0
                    val nU = parts[1].toIntOrNull() ?: 0
                    val np = nH + nU

                    // Get or compute polygon bounds
                    val bounds = getTileBounds(assessment, rowIdx, colIdx, photoGeo)
                        ?: continue

                    // Determine classification
                    val cls = when {
                        np == 0 -> Status.NON_MANGROVE
                        nH >= nU -> Status.HEALTHY
                        else -> Status.UNHEALTHY
                    }

                    // Calculate per-tile properties
                    val healthyPct = if (np > 0) ((nH * 100.0 / np).roundToInt()) else 0
                    val unhealthyPct = if (np > 0) ((nU * 100.0 / np).roundToInt()) else 0
                    val nonMangrovePct = 100 - healthyPct - unhealthyPct

                    // Store feature
                    allFeatures.add(TileFeature(
                        id = "${assessment.id}_r${rowIdx}_c${colIdx}",
                        imageId = assessment.id,
                        imageFilename = assessment.name,
                        imageKey = assessment.photoKey,
                        imageTime = assessment.timeMillis,
                        tileRow = rowIdx,
                        tileCol = colIdx,
                        tileRowPx = rowIdx * 224, // 224 = MODEL_INPUT size
                        tileColPx = colIdx * 224,
                        classification = cls,
                        healthyPct = healthyPct,
                        unhealthyPct = unhealthyPct,
                        nonMangrovePct = nonMangrovePct,
                        centerLat = bounds.centerLat,
                        centerLon = bounds.centerLon,
                        north = bounds.north,
                        south = bounds.south,
                        west = bounds.west,
                        east = bounds.east,
                        areaM2 = bounds.north - bounds.south, // north-south gives Area (east-west provides width, same value)
                        captureTimestamp = null, // TODO: Add from EXIF if available
                        georeferencingMethod = "EXIF-based estimation (flight height + focal length + ground distance)",
                        georeferencingSource = "DJI EXIF (RelativeAltitude, GimbalYawDegree)",
                        healthyPieces = nH,
                        unhealthyPieces = nU,
                        totalPieces = np
                    ))

                    // Sum areas
                    totalM2 += bounds.north - bounds.south
                    when (cls) {
                        Status.HEALTHY -> healthyM2 += (bounds.north - bounds.south) * (healthyPct / 100.0).roundToInt()
                        Status.UNHEALTHY -> unhealthyM2 += (bounds.north - bounds.south) * (unhealthyPct / 100.0).roundToInt()
                        Status.NON_MANGROVE -> {}
                        Status.OUT_OF_REACH -> {}
                    }

                    // Track this essence: photo with valid GeoPosition
                    tilesWithValidGeo.add("${assessment.id}_${photoGeo.encode()}")
                }
            }
        }

        // Export GeoJSON with all features
        val geoJson = arrayToJson(allFeatures)
        return TileGisResult(
            photos = assessments.count { !it.outOfReach },
            tilesCount = allFeatures.size,
            uniqueMappedM2 = totalM2, // Simplified: each tile area counted
            totalM2 = totalM2,
            healthyM2 = healthyM2,
            unhealthyM2 = unhealthyM2,
            otherM2 = totalM2 - healthyM2 - unhealthyM2,
            tilesWithValidGeoCount = tilesWithValidGeo.size,
            features = allFeatures,
            geoJson = geoJson
        )
    }

    /**
     * Calculates the geographic polygon bounds of a single grid cell within a photo.
     *
     * Algorithm:
     * 1. Calculate grid coordinates of tile center relative to the photo's center
     * 2. Convert to meters using ground sample distance photoGeo.tileM
     * 3. Rotate by photo heading using rotation matrix
     * 4. Convert meters to lat/lon using Earth's radius
     * 5. Add/subtract half tile size in cardinal directions to get polygon bounds
     *
     * @param assessment Photo assessment with classification data
     * @param row Grid row (0-indexed from top)
     * @param col Grid column (0-indexed from left)
     * @param photoGeo Photo's ground position (tile size, heading, center grid pos)
     * @return Polygon bounds as TileBounds, or null if calculation fails
     */
    private fun getTileBounds(
        assessment: Assessment,
        row: Int,
        col: Int,
        photoGeo: PhotoGeo
    ): TileBounds? {
        try {
            // Get photo center GPS coordinates
            val lat = assessment.lat
            val lon = assessment.lon
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null

            // Grid position of tile center relative to photo center
            // Photo center is at (cx, cy) - indexing means: top-left=0,0, so center=0.5
            val colCenter = col + 0.5
            val rowCenter = row + 0.5
            val cx = photoGeo.cx
            val cy = photoGeo.cy

            // Horizontal distance from photo center in meters
            val right = (colCenter - cx) * photoGeo.tileM
            // Vertical distance from photo center in meters
            val fwd = (cy - rowCenter) * photoGeo.tileM

            // Rotate by heading (PhotoGeo.heading = degrees from north, 0 = north-up)
            val h = Math.toRadians(photoGeo.heading)
            val east = right * Math.cos(h) + fwd * Math.sin(h)
            val north = fwd * Math.cos(h) - right * Math.sin(h)

            // Convert meters to latitude/longitude offsets
            val latDelta = north / 111_320.0  // 1 degree latitude ≈ 111.32 km
            val lonOffset = east / (111_320.0 * Math.cos(Math.toRadians(lat)))

            val centerLat = lat + latDelta
            val centerLon = lon + lonOffset

            // Calculate polygon bounds (center ± half tile size)
            val halfTileM = photoGeo.tileM / 2.0
            val halfLat = halfTileM / (distancePerDegree(lat))
            val halfLon = halfTileM / (distancePerDegree(lat) * Math.cos(Math.toRadians(lat)))

            return TileBounds(
                north = centerLat + halfLat,
                south = centerLat - halfLat,
                west = centerLon - halfLon,
                east = centerLon + halfLon,
                centerLat = centerLat,
                centerLon = centerLon
            )
        } catch (e: Exception) {
            android.util.Log.e("TileGisBuilder", "Failed to calculate tile bounds for row $row col $col: ${e.message}")
            return null
        }
    }

    /**
     * Approximate distance in meters per degree of longitude/latitude at a given latitude.
     * Uses the effective Earth radius (6371 km), adjusted for latitude (cosine makes longitude
     * effect smaller near the equator, larger near the poles).
     */
    private fun distancePerDegree(lat: Double): Double {
        val earthRadius = 6371000.0
        return Math.toRadians(90.0 - Math.abs(lat)) * earthRadius
    }

    /**
     * Converts a list of TileFeature objects to a GeoJSON FeatureCollection string.
     */
    private fun arrayToJson(features: List<TileFeature>): String {
        return try {
            val featuresJson = JSONArray()
            for (feat in features) {
                if (!isValidGeo(feat.north, feat.south, feat.west, feat.east)) continue

                val feature = JSONObject()
                feature.put("type", "Feature")

                val geometry = JSONObject()
                geometry.put("type", "Polygon")
                val ring = JSONArray()

                // Ring vertices: west-north, east-north, east-south, west-south, close
                ring.put(JSONArray().put(feat.west).put(feat.north))
                ring.put(JSONArray().put(feat.east).put(feat.north))
                ring.put(JSONArray().put(feat.east).put(feat.south))
                ring.put(JSONArray().put(feat.west).put(feat.south))
                ring.put(JSONArray().put(feat.west).put(feat.north))

                geometry.put("coordinates", ring)
                feature.put("geometry", geometry)

                val props = JSONObject()
                props.put("id", feat.id)
                props.put("image_id", feat.imageId)
                props.put("image_filename", feat.imageFilename)
                props.put("image_key", feat.imageKey)
                props.put("image_time", feat.imageTime)
                props.put("tile_id", feat.id)
                props.put("tile_row", feat.tileRow)
                props.put("tile_col", feat.tileCol)
                props.put("tile_row_px", feat.tileRowPx)
                props.put("tile_col_px", feat.tileColPx)
                props.put("predicted_class", feat.classification.label)
                props.put("confidence", feat.healthyPct.toDouble())
                props.put("probability_healthy", feat.healthyPct / 100.0)
                props.put("probability_non_mangrove", feat.nonMangrovePct / 100.0)
                props.put("probability_unhealthy", feat.unhealthyPct / 100.0)
                props.put("area_m2", feat.areaM2)

                feat.captureTimestamp?.let {
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    props.put("capture_timestamp", sdf.format(Date(it)))
                }

                props.put("georeferencing_method", feat.georeferencingMethod)
                props.put("georeferencing_source", feat.georeferencingSource)
                props.put("healthy_pieces", feat.healthyPieces)
                props.put("unhealthy_pieces", feat.unhealthyPieces)
                props.put("total_pieces", feat.totalPieces)

                feature.put("properties", props)
                featuresJson.put(feature)
            }

            val fc = JSONObject()
            fc.put("type", "FeatureCollection")
            fc.put("features", featuresJson)
            fc.toString(2)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to export GeoJSON", e)
        }
    }

    /**
     * Validates polygon coordinates before including in GeoJSON.
     */
    private fun isValidGeo(north: Double, south: Double, west: Double, east: Double): Boolean {
        return north != Double.POSITIVE_INFINITY &&
                north != Double.NEGATIVE_INFINITY &&
                south != Double.POSITIVE_INFINITY &&
                south != Double.NEGATIVE_INFINITY &&
                west != Double.POSITIVE_INFINITY &&
                west != Double.NEGATIVE_INFINITY &&
                east != Double.POSITIVE_INFINITY &&
                east != Double.NEGATIVE_INFINITY &&
                north > south &&
                east > west
    }
}

/**
 * Tile geometry feature for GIS display and GeoJSON export.
 */
data class TileFeature(
    val id: String,
    val imageId: String,
    val imageFilename: String,
    val imageKey: String,
    val imageTime: Long,
    val tileRow: Int,
    val tileCol: Int,
    val tileRowPx: Int,
    val tileColPx: Int,
    val classification: Status,
    val healthyPct: Int,
    val unhealthyPct: Int,
    val nonMangrovePct: Int,
    // Geographic polygon
    val centerLat: Double,
    val centerLon: Double,
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double,
    // Area
    val areaM2: Double,
    // Metadata
    val captureTimestamp: Long?,
    val georeferencingMethod: String,
    val georeferencingSource: String,
    // AI classification
    val healthyPieces: Int,
    val unhealthyPieces: Int,
    val totalPieces: Int
)

/**
 * Result of building tile-based GIS.
 *
 * Contains statistics and the GeoJSON export, plus helper methods for UI consumption.
 */
data class TileGisResult(
    val photos: Int,
    val tilesCount: Int,
    val uniqueMappedM2: Double,  // Same as totalM2 for now - will become unique area if overlap resolver added
    val totalM2: Double,
    val healthyM2: Double,
    val unhealthyM2: Double,
    val otherM2: Double,
    val tilesWithValidGeoCount: Int,
    val features: List<TileFeature>,
    val geoJson: String
) {

    /**
     * Validates that the GeoJSON string can be parsed (no circular references, duplicate keys, etc.)
     * Useful for error checking before export.
     *
     * @return true if geoJson is parseable, false if malformed
     */
    fun isGeoJsonValid(): Boolean {
        return try {
            JSONObject(geoJson)
            true
        } catch (e: Exception) {
            android.util.Log.e("TileGisBuilder", "Invalid GeoJSON: ${e.message}")
            false
        }
    }
}