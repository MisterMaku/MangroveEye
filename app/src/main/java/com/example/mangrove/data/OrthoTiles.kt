package com.example.mangrove.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.min

/** Loads, imports and manages the QGIS orthomosaic maps (XYZ tiles zipped). */
object OrthoTiles {
    private const val CUSTOM_ZIP = "my_qgis_map.zip"
    private const val CUSTOM_INFO = "my_qgis_info.json"
    private const val BUILTIN_INFO = "my_builtin_info.json"
    private const val PREFS = "mangroveeye"
    private const val KEY_CUSTOM = "use_custom_map"
    private val ENTRY = Regex("""^(?:(.+)/)?(\d{1,2})/(\d+)/(\d+)\.(png|jpg|jpeg)$""", RegexOption.IGNORE_CASE)

    fun load(ctx: Context): OrthoSource? {
        val customZip = File(ctx.filesDir, CUSTOM_ZIP)
        val useCustom = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CUSTOM, false)
        if (useCustom && customZip.exists()) {
            val src = scan(customZip, true, readInfo(ctx))
            return src
        }
        return try { scan(builtIn(ctx), false, readInfo(ctx, BUILTIN_INFO)) } catch (e: Exception) { null }
    }

    /** The map info (name, location, ...) the user entered when adding their map, or an empty MapInfo. */
    private fun readInfo(ctx: Context): MapInfo = readInfo(ctx, CUSTOM_INFO)

    /** Reads a saved map info file (name, location, description); missing or broken file -> empty MapInfo. */
    private fun readInfo(ctx: Context, fileName: String): MapInfo = try {
        val f = File(ctx.filesDir, fileName)
        if (f.exists()) {
            JSONObject(f.readText()).let {
                MapInfo(
                    name = it.optString("name", ""),
                    location = it.optString("location", ""),
                    description = it.optString("description", ""),
                    additionalInfo = it.optString("additionalInfo", ""),
                    creationDate = it.optLong("creationDate", 0),
                    sourceType = MapSourceType.valueOf(it.optString("sourceType", MapSourceType.QGIS.name)),
                    totalAreaSqm = it.optDouble("totalAreaSqm", 0.0)
                )
            }
        } else MapInfo()
    } catch (e: Exception) { MapInfo() }

    /** Saves the map info (name, location, description) the user entered to a json file. */
    fun saveInfo(ctx: Context, info: MapInfo) {
        val infoJson = JSONObject()
            .put("name", info.name)
            .put("location", info.location)
            .put("description", info.description)
            .put("additionalInfo", info.additionalInfo)
            .put("creationDate", info.creationDate)
            .put("sourceType", info.sourceType.name)
            .put("totalAreaSqm", info.totalAreaSqm)
        File(ctx.filesDir, CUSTOM_INFO).writeText(infoJson.toString())
    }

    /** Saves the map info for the built-in orthomosaic (used when creating a map entry without a new zip). */
    fun saveBuiltinInfo(ctx: Context, info: MapInfo) {
        val infoJson = JSONObject()
            .put("name", info.name)
            .put("location", info.location)
            .put("description", info.description)
            .put("additionalInfo", info.additionalInfo)
            .put("creationDate", info.creationDate)
            .put("sourceType", info.sourceType.name)
            .put("totalAreaSqm", info.totalAreaSqm)
        File(ctx.filesDir, BUILTIN_INFO).writeText(infoJson.toString())
    }

    fun import(ctx: Context, uri: Uri): OrthoSource? {
        val tmp = File(ctx.filesDir, "import.tmp")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } } ?: return null
        } catch (e: Exception) { tmp.delete(); return null }
        if (scan(tmp, true, MapInfo()) == null) { tmp.delete(); return null }
        val dest = File(ctx.filesDir, CUSTOM_ZIP)
        dest.delete()
        tmp.renameTo(dest)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CUSTOM, true).apply()
        return scan(dest, true, MapInfo())
    }

    /**
     * Import a map with associated metadata.
     */
    fun import(ctx: Context, uri: Uri, info: MapInfo): OrthoSource? {
        val tmp = File(ctx.filesDir, "import.tmp")
        try {
            ctx.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } } ?: return null
        } catch (e: Exception) { tmp.delete(); return null }

        val scanned = scan(tmp, true, info)
        if (scanned == null) { tmp.delete(); return null }

        val dest = File(ctx.filesDir, CUSTOM_ZIP)
        dest.delete()
        tmp.renameTo(dest)

        // Save the map info
        saveInfo(ctx, info)

        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CUSTOM, true).apply()
        return scan(dest, true, info)
    }

    fun hasCustom(ctx: Context) = File(ctx.filesDir, CUSTOM_ZIP).exists()

    /** True if the zip contains XYZ tiles (i.e. it is a valid QGIS map), without saving it. */
    fun isZipMap(file: File): Boolean = scan(file, true, MapInfo()) != null

    fun useBuiltIn(ctx: Context): OrthoSource? {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CUSTOM, false).apply()
        return load(ctx)
    }

    fun useCustom(ctx: Context): OrthoSource? {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CUSTOM, hasCustom(ctx)).apply()
        return load(ctx)
    }

    fun removeCustom(ctx: Context): OrthoSource? {
        File(ctx.filesDir, CUSTOM_ZIP).delete()
        File(ctx.filesDir, CUSTOM_INFO).delete()
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

    /** Scans a zip into an OrthoSource (exposed so the view model can re-scan with fresh map info). */
    fun scan(file: File, custom: Boolean, info: MapInfo): OrthoSource? = try {
        ZipFile(file).use { zip ->
            var prefix: String? = null
            var ext = "png"
            var minZ = 99
            var maxZ = -1
            val ranges = HashMap<Int, IntArray>()
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
                south = TileMath.tileLat(r[3] + 1, maxZ),
                north = TileMath.tileLat(r[2], maxZ),
                west = TileMath.tileLon(r[0], maxZ),
                east = TileMath.tileLon(r[1] + 1, maxZ),
                custom = custom,
                info = info
            )
        }
    } catch (e: Exception) { null }
}