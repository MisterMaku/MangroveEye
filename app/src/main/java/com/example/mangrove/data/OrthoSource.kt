package com.example.mangrove.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

private val imageryCache = HashMap<String, Boolean>()   // (map, point) -> has drone imagery?

/** One QGIS "Generate XYZ tiles" map stored as a zip: {prefix}/{zoom}/{x}/{y}.png */
data class OrthoSource(
    val file: File,
    val prefix: String,          // top folder inside the zip ("" if tiles are at the zip root)
    val ext: String,             // png or jpg
    val minZoom: Int,
    val maxZoom: Int,
    val south: Double,
    val north: Double,
    val west: Double,
    val east: Double,
    val custom: Boolean,         // true = the user's own map, false = the built-in one
    val info: MapInfo = MapInfo()  // name, location, description and study area entered by the user
) {
    val centerLat get() = (south + north) / 2
    val centerLon get() = (west + east) / 2
    val key get() = "${file.path}:${file.lastModified()}"
    val label get() = if (prefix.isEmpty()) file.nameWithoutExtension else prefix.substringAfterLast('/')
    fun entryName(z: Int, x: Int, y: Int) = (if (prefix.isEmpty()) "" else "$prefix/") + "$z/$x/$y.$ext"

    /** True if a GPS point is inside (or within ~55 m of) the mapped area. */
    fun contains(lat: Double, lon: Double, margin: Double = 0.0005) =
        lat in (south - margin)..(north + margin) && lon in (west - margin)..(east + margin)

    /**
     * True only if there is REAL drone imagery at this GPS point: the point must be inside the map AND the pixel of the
     * QGIS tile at that point must not be empty (transparent). A point on the empty water / mud / land around your
     * orthomosaic, even inside its rectangle, has no imagery, so it counts as outside.
     */
    fun hasImagery(lat: Double, lon: Double): Boolean {
        if (!contains(lat, lon, 0.0)) return false
        val ck = "$key|${String.format(Locale.US, "%.6f", lat)}|${String.format(Locale.US, "%.6f", lon)}"
        synchronized(imageryCache) { imageryCache[ck]?.let { return it } }
        val ok = try {
            val z = maxZoom
            val fx = (lon + 180.0) / 360.0 * (1 shl z)
            val r = Math.toRadians(lat)
            val fy = (1.0 - ln(tan(r) + 1.0 / cos(r)) / Math.PI) / 2.0 * (1 shl z)
            val x = floor(fx).toInt()
            val y = floor(fy).toInt()
            ZipFile(file).use { zip ->
                val entry = zip.getEntry(entryName(z, x, y))
                if (entry == null) false                                   // no tile here: no imagery
                else {
                    val bmp = zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
                    if (bmp == null) false
                    else {
                        val px = ((fx - x) * bmp.width).toInt().coerceIn(0, bmp.width - 1)
                        val py = ((fy - y) * bmp.height).toInt().coerceIn(0, bmp.height - 1)
                        var found = false
                        for (dy in -4..4) for (dx in -4..4) {               // a small window, so GPS jitter does not matter
                            val qx = px + dx
                            val qy = py + dy
                            if (qx in 0 until bmp.width && qy in 0 until bmp.height && (bmp.getPixel(qx, qy) ushr 24) >= 200) found = true
                        }
                        found
                    }
                }
            }
        } catch (e: Exception) { true }                                     // cannot check: do not reject the photo
        synchronized(imageryCache) { imageryCache[ck] = ok }
        return ok
    }
}
