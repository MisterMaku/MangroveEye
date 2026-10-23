package com.example.mangrove.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.*

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
    val custom: Boolean          // true = the user's own map, false = the built-in one
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
        val ck = "$key|${"%.6f".format(lat)}|${"%.6f".format(lon)}"
        synchronized(imageryCache) { imageryCache[ck]?.let { return it } }
        val ok = try {
            val z = maxZoom
            val fx = (lon + 180.0) / 360.0 * (1 shl z)
            val r = Math.toRadians(lat)
            val fy = (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)
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

object TileMath {
    fun tileX(lon: Double, z: Int) = floor((lon + 180.0) / 360.0 * (1 shl z)).toInt()
    fun tileY(lat: Double, z: Int): Int {
        val r = Math.toRadians(lat)
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)).toInt()
    }
    fun tileLon(x: Int, z: Int) = x.toDouble() / (1 shl z) * 360.0 - 180.0
    fun tileLat(y: Int, z: Int) = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y / (1 shl z)))))
}

/** Loads the built-in QGIS map, or the user's own one if they added it. */
object OrthoTiles {
    private const val CUSTOM_ZIP = "my_qgis_map.zip"
    private const val PREFS = "mangroveeye"
    private const val KEY_CUSTOM = "use_custom_map"
    private val ENTRY = Regex("""^(?:(.+)/)?(\d{1,2})/(\d+)/(\d+)\.(png|jpg|jpeg)$""", RegexOption.IGNORE_CASE)

    fun load(ctx: Context): OrthoSource? {
        val custom = File(ctx.filesDir, CUSTOM_ZIP)
        val useCustom = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CUSTOM, false)
        if (useCustom && custom.exists()) scan(custom, true)?.let { return it }
        return try { scan(builtIn(ctx), false) } catch (e: Exception) { null }
    }

    /** Copies a zip the user picked, checks that it holds XYZ tiles, and makes it the active map. */
    fun import(ctx: Context, uri: Uri): OrthoSource? {
        val tmp = File(ctx.filesDir, "import.tmp")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } } ?: return null
        } catch (e: Exception) { tmp.delete(); return null }
        if (scan(tmp, true) == null) { tmp.delete(); return null }
        val dest = File(ctx.filesDir, CUSTOM_ZIP)
        dest.delete()
        tmp.renameTo(dest)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CUSTOM, true).apply()
        return scan(dest, true)
    }

    /** True if the user has added a map of their own (even if the built-in one is showing right now). */
    fun hasCustom(ctx: Context) = File(ctx.filesDir, CUSTOM_ZIP).exists()

    /** Shows the built-in map. The user's own map stays on the phone so they can switch back. */
    fun useBuiltIn(ctx: Context): OrthoSource? {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CUSTOM, false).apply()
        return load(ctx)
    }

    /** Switches back to the map the user added earlier (if it is still on the phone). */
    fun useCustom(ctx: Context): OrthoSource? {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CUSTOM, hasCustom(ctx)).apply()
        return load(ctx)
    }

    /** Deletes the map the user added from the phone and shows the built-in map again. */
    fun removeCustom(ctx: Context): OrthoSource? {
        File(ctx.filesDir, CUSTOM_ZIP).delete()
        File(ctx.filesDir, "import.tmp").delete()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CUSTOM, false).apply()
        return load(ctx)
    }

    private fun builtIn(ctx: Context): File {
        val f = File(ctx.filesDir, Config.ORTHO_ZIP)
        val size = ctx.assets.open(Config.ORTHO_ZIP).use { it.available().toLong() }
        if (!f.exists() || f.length() != size) {
            ctx.assets.open(Config.ORTHO_ZIP).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        }
        return f
    }

    /** Reads the zip's tile list to find the folder name, zoom range and the area it covers. */
    private fun scan(file: File, custom: Boolean): OrthoSource? = try {
        ZipFile(file).use { zip ->
            var prefix: String? = null
            var ext = "png"
            var minZ = 99
            var maxZ = -1
            val ranges = HashMap<Int, IntArray>()          // zoom -> minX, maxX, minY, maxY
            for (e in zip.entries()) {
                if (e.isDirectory || e.name.startsWith("__MACOSX")) continue
                val m = ENTRY.matchEntire(e.name) ?: continue
                val p = m.groupValues[1]
                if (prefix == null) { prefix = p; ext = m.groupValues[5].lowercase() }
                if (p != prefix) continue
                val z = m.groupValues[2].toInt()
                val x = m.groupValues[3].toInt()
                val y = m.groupValues[4].toInt()
                minZ = min(minZ, z); maxZ = max(maxZ, z)
                val r = ranges.getOrPut(z) { intArrayOf(x, x, y, y) }
                r[0] = min(r[0], x); r[1] = max(r[1], x); r[2] = min(r[2], y); r[3] = max(r[3], y)
            }
            val r = ranges[maxZ]
            if (prefix == null || r == null) null
            else OrthoSource(
                file, prefix, ext, minZ, maxZ,
                south = TileMath.tileLat(r[3] + 1, maxZ), north = TileMath.tileLat(r[2], maxZ),
                west = TileMath.tileLon(r[0], maxZ), east = TileMath.tileLon(r[1] + 1, maxZ),
                custom = custom
            )
        }
    } catch (e: Exception) { null }
}