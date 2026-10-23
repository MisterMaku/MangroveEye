package com.example.mangrove.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.Locale

enum class BaseMap(val label: String, val credit: String) {
    SATELLITE("Satellite", "Imagery © Esri, Maxar, Earthstar Geographics"),
    MAPTILER("MapTiler", "© MapTiler © OpenStreetMap contributors"),
    NONE("QGIS only", "Map: your QGIS orthomosaic (works fully offline)")
}

enum class Status(val label: String) { HEALTHY("Healthy"), UNHEALTHY("Unhealthy") }

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
    val thumb: String = ""         // file name of the small preview photo (see Thumbs)
) {
    val status: Status
        get() = if (healthy >= Config.HEALTHY_MIN) Status.HEALTHY else Status.UNHEALTHY
    val uncertain: Boolean
        get() = Math.abs(healthy - Config.HEALTHY_MIN) <= Config.UNCERTAIN_BAND
    val mostlyNonMangrove: Boolean
        get() = nonMangrove >= 50
    /** A warning shown next to the result when it should not be trusted as a confident Healthy / Unhealthy. */
    val note: String?
        get() = when {
            mostlyNonMangrove -> "Mostly water, mud or land ($nonMangrove%): result is not reliable"
            uncertain -> "Uncertain: close to the ${Config.HEALTHY_MIN}% limit"
            else -> null
        }
    val tilesText: String
        get() = "Healthy $healthy% · Unhealthy $unhealthy%" + (if (nonMangrove > 0) " · Non-mangrove $nonMangrove%" else "")
    val gpsText: String
        get() = String.format(Locale.US, "%.5f, %.5f", lat, lon)
    val action: String
        get() = when (status) {
            Status.HEALTHY -> "No intervention needed. Keep the current monitoring interval."
            Status.UNHEALTHY -> "Canopy damage detected. Plan a ground survey within 2 weeks."
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
 * grid is the tile map (H / U / N per tile, rows split by "/").
 */
data class TileResult(val healthy: Int, val unhealthy: Int, val nonMangrove: Int = 0, val grid: String = "")

/** Small preview photos saved on the phone (one per assessment), used in the log, tile view and PDF. */
object Thumbs {
    fun file(ctx: Context, name: String) = File(File(ctx.filesDir, "thumbs").apply { mkdirs() }, name)
    fun delete(ctx: Context, name: String) { if (name.isNotEmpty()) file(ctx, name).delete() }
    fun deleteAll(ctx: Context) { File(ctx.filesDir, "thumbs").listFiles()?.forEach { it.delete() } }
}