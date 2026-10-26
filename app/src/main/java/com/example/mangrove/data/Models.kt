package com.example.mangrove.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class BaseMap(val label: String, val credit: String) {
    SATELLITE("Satellite", "Imagery © Esri, Maxar, Earthstar Geographics"),
    MAPTILER("MapTiler", "© MapTiler © OpenStreetMap contributors"),
    NONE("QGIS only", "Map: your QGIS orthomosaic (works fully offline)")
}

enum class Status(val label: String) {
    HEALTHY("Healthy"), UNHEALTHY("Unhealthy"), NON_MANGROVE("Non-mangrove"),
    OUT_OF_REACH("Invalid")      // photo GPS is outside the QGIS map: invalid, no AI prediction, no pin
}

data class Assessment(
    val id: String,
    val name: String,
    val timeMillis: Long,
    val lat: Double,
    val lon: Double,
    val healthy: Int,              // % of the mangrove area the AI called Healthy (read in 256 px pieces)
    val unhealthy: Int,            // % called Unhealthy
    val photoKey: String = "",     // name|size of the photo, used to skip duplicates
    val nonMangrove: Int = 0,      // % of ALL pieces that were water, mud or land
    val grid: String = "",         // tile map, one letter per tile: H healthy, U unhealthy, N non-mangrove; rows split by "/"
    val thumb: String = "",        // file name of the small preview photo (see Thumbs)
    val outOfReach: Boolean = false, // GPS is outside the QGIS map: the AI was NOT run for this photo
    val tiles: String = "",        // per tile "healthy pieces:unhealthy pieces", tiles split by ",", rows by "/"
    val geo: String = ""           // see PhotoGeo: tile size in metres, photo centre, heading, pieces per tile
) {
    val status: Status
        get() = when {
            outOfReach -> Status.OUT_OF_REACH
            nonMangrove >= Config.NON_MANGROVE_MIN -> Status.NON_MANGROVE
            healthy >= Config.HEALTHY_MIN -> Status.HEALTHY
            else -> Status.UNHEALTHY
        }
    val showsPercent: Boolean get() = status == Status.HEALTHY || status == Status.UNHEALTHY
    val tilesText: String
        get() = if (outOfReach) "Invalid: outside the QGIS map · no AI prediction"
        else "Healthy $healthy% · Unhealthy $unhealthy%" + (if (nonMangrove > 0) " · Non-mangrove $nonMangrove%" else "")
    val gpsText: String
        get() = String.format(Locale.US, "%.5f, %.5f", lat, lon)
    /** Ground area covered by all the tiles of this photo in m² (0 if unknown). */
    val coveredM2: Double
        get() {
            val g = PhotoGeo.parse(geo) ?: return 0.0
            return grid.split("/").sumOf { it.length } * g.tileM * g.tileM
        }

    /** Enhanced tile info with full polygon bounds, not just center point. Returns TileGeo object. */
    fun tileGeoInfo(row: Int, col: Int): TileGeo? {
        val g = PhotoGeo.parse(geo) ?: return null
        val cell = tiles.split("/").getOrNull(row)?.split(",")?.getOrNull(col)?.split(":") ?: return null
        val nH = cell.getOrNull(0)?.toIntOrNull() ?: return null
        val nU = cell.getOrNull(1)?.toIntOrNull() ?: return null
        val np = g.pieces.coerceAtLeast(1)
        val hp = (nH * 100.0 / np).roundToInt()                    // % of THIS tile that is Healthy
        val up = (nU * 100.0 / np).roundToInt()                    // % of THIS tile that is Unhealthy

        // Calculate tile center coordinates
        val right = (col + 0.5 - g.cx) * g.tileM                    // metres east of photo centre
        val fwd = (g.cy - (row + 0.5)) * g.tileM                    // metres north of photo centre
        val h = Math.toRadians(g.heading)                           // the way the top of the photo points (0 = north)
        val east = right * cos(h) + fwd * sin(h)
        val north = fwd * cos(h) - right * sin(h)
        val centerLat = lat + north / 111_320.0
        val centerLon = lon + east / (111_320.0 * cos(Math.toRadians(lat)))

        // Calculate tile polygon bounds (center +/- half tile size in each direction)
        // This gives us the actual bounding box of this tile on the ground
        val halfTileM = g.tileM / 2.0
        val tileH = Math.toRadians(g.heading)
        val tileNorth = centerLat + (halfTileM * cos(tileH)) / 111_320.0
        val tileSouth = centerLat - (halfTileM * cos(tileH)) / 111_320.0
        val tileEast = centerLon + (halfTileM * sin(tileH)) / (111_320.0 * cos(Math.toRadians(centerLat)))
        val tileWest = centerLon - (halfTileM * sin(tileH)) / (111_320.0 * cos(Math.toRadians(centerLat)))

        return TileGeo(
            row = row,
            col = col,
            centerLat = centerLat,
            centerLon = centerLon,
            north = tileNorth,
            south = tileSouth,
            west = tileWest,
            east = tileEast,
            healthyPct = hp,
            unhealthyPct = up,
            nonMangrovePct = (100 - hp - up).coerceAtLeast(0),
            areaM2 = (tileNorth - tileSouth) * (tileEast - tileWest),
            tileM = g.tileM
        )
    }

    /** Details of one tile: its size, its place on the ground (GPS) and its Healthy / Unhealthy share. */
    fun tileInfo(row: Int, col: Int): TileInfo? {
        val g = PhotoGeo.parse(geo) ?: return null
        val cell = tiles.split("/").getOrNull(row)?.split(",")?.getOrNull(col)?.split(":") ?: return null
        val nH = cell.getOrNull(0)?.toIntOrNull() ?: return null
        val nU = cell.getOrNull(1)?.toIntOrNull() ?: return null
        val np = g.pieces.coerceAtLeast(1)
        val hp = (nH * 100.0 / np).roundToInt()                    // % of THIS tile that is Healthy
        val up = (nU * 100.0 / np).roundToInt()                    // % of THIS tile that is Unhealthy
        val right = (col + 0.5 - g.cx) * g.tileM                    // metres to the right of the photo centre
        val fwd = (g.cy - (row + 0.5)) * g.tileM                    // metres towards the top of the photo
        val h = Math.toRadians(g.heading)                           // the way the top of the photo points (0 = north)
        val east = right * cos(h) + fwd * sin(h)
        val north = fwd * cos(h) - right * sin(h)
        return TileInfo(row, col, hp, up, (100 - hp - up).coerceAtLeast(0), g.tileM, g.tileM * g.tileM,
            lat + north / 111_320.0, lon + east / (111_320.0 * cos(Math.toRadians(lat))))
    }
    val action: String
        get() = when (status) {
            Status.HEALTHY -> "No intervention needed. Keep the current monitoring interval."
            Status.UNHEALTHY -> "Canopy damage detected. Plan a ground survey within 2 weeks."
            Status.NON_MANGROVE -> "No mangrove here (water, mud or land). No assessment needed."
            Status.OUT_OF_REACH -> "Invalid: the photo is outside the QGIS map, so it is not on the map and no AI prediction was made."
        }
}

/** A photo chosen for analysis. Its GPS is read from the EXIF first and travels with it. */
data class PickedPhoto(
    val uri: Uri,
    val name: String,
    val lat: String,
    val lon: String,
    val fromExif: Boolean,
    val key: String                // name|size, to spot the same photo picked twice
) {
    fun latLon(): Pair<Double, Double>? {
        val a = lat.trim().toDoubleOrNull()
        val b = lon.trim().toDoubleOrNull()
        return if (a != null && b != null && a in -90.0..90.0 && b in -180.0..180.0) a to b else null
    }
}

/**
 * Result for one photo. healthy + unhealthy = 100 (of mangrove tiles only); nonMangrove is % of all tiles;
 * grid is the tile map (H / U / N per tile, rows split by "/"); tiles and geo hold the per-tile details.
 */
data class TileResult(
    val healthy: Int, val unhealthy: Int, val nonMangrove: Int = 0, val grid: String = "",
    val tiles: String = "", val geo: String = ""
)

/**
 * How one photo sits on the ground. tileM = side of one 512 px tile in metres; cx / cy = the photo centre measured in
 * tiles from the top-left corner; heading = direction the top of the photo points to (degrees from north);
 * pieces = AI pieces per tile (2 x 2 = 4).
 */
data class PhotoGeo(val tileM: Double, val cx: Double, val cy: Double, val heading: Double, val pieces: Int) {
    fun encode(): String = String.format(Locale.US, "%.4f,%.4f,%.4f,%.1f,%d", tileM, cx, cy, heading, pieces)
    companion object {
        fun parse(s: String): PhotoGeo? = try {
            val p = s.split(",")
            PhotoGeo(p[0].toDouble(), p[1].toDouble(), p[2].toDouble(), p[3].toDouble(), p[4].toInt())
        } catch (e: Exception) { null }
    }
}

/** Everything the tile view shows for one tile. */
data class TileInfo(
    val row: Int, val col: Int,
    val healthyPct: Int, val unhealthyPct: Int, val nonMangrovePct: Int,   // share of the tile area
    val sideM: Double, val areaM2: Double,                                  // size of the tile on the ground
    val lat: Double, val lon: Double                                        // tile centre
) {
    val status: Status
        get() = when {
            healthyPct == 0 && unhealthyPct == 0 -> Status.NON_MANGROVE
            healthyPct >= unhealthyPct -> Status.HEALTHY
            else -> Status.UNHEALTHY
        }
    fun areaOf(pct: Int): Double = areaM2 * pct / 100.0
}

/**
 * Enhanced tile information combining center point, land use (Healthy/Unhealthy percentages),
 * polygon bounds, and area. Used for GIS tile polygons and GeoJSON export.
 */
data class TileGeo(
    val row: Int,
    val col: Int,
    val centerLat: Double,
    val centerLon: Double,
    val north: Double,
    val south: Double,
    val west: Double,
    val east: Double,
    val healthyPct: Int,
    val unhealthyPct: Int,
    val nonMangrovePct: Int,
    val areaM2: Double,
    val tileM: Double  // side length in metres
) {
    val status: Status
        get() = when {
            healthyPct == 0 && unhealthyPct == 0 -> Status.NON_MANGROVE
            healthyPct >= unhealthyPct -> Status.HEALTHY
            else -> Status.UNHEALTHY
        }
    val centerLatLon: Pair<Double, Double>
        get() = centerLat to centerLon
}

/** Small preview photos saved on the phone (one per assessment), used in the log, tile view and PDF. */
object Thumbs {
    fun file(ctx: Context, name: String) = File(File(ctx.filesDir, "thumbs").apply { mkdirs() }, name)
    fun delete(ctx: Context, name: String) { if (name.isNotEmpty()) file(ctx, name).delete() }
    fun deleteAll(ctx: Context) { File(ctx.filesDir, "thumbs").listFiles()?.forEach { it.delete() } }
}