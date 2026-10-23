package com.example.mangrove.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface

/** Flight data written by the DJI into the photo. A null means the photo does not say. */
data class FlightInfo(val heightM: Double?, val yawDeg: Double?, val focal35mm: Double?)

/** Step: "Extract GPS from EXIF" (the DJI Mini 4K writes GPS into every JPG). */
object ExifGps {
    fun read(ctx: Context, uri: Uri): Pair<Double, Double>? = try {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            ExifInterface(s).latLong?.let { it[0] to it[1] }
        }
    } catch (e: Exception) { null }

    /** Height above the take-off point, camera heading and lens (35 mm equivalent), used for the tile size and location. */
    fun flight(ctx: Context, uri: Uri): FlightInfo = try {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            val ex = ExifInterface(s)
            val xmp = ex.getAttribute("Xmp") ?: ""                  // DJI keeps RelativeAltitude and GimbalYawDegree here
            fun num(name: String): Double? =
                Regex(name + """(?:\s*=\s*"|>)\s*([-+]?\d+(?:\.\d+)?)""").find(xmp)?.groupValues?.get(1)?.toDoubleOrNull()
            val f = ex.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
            FlightInfo(
                num("RelativeAltitude")?.takeIf { it > 1.0 },
                num("GimbalYawDegree") ?: num("FlightYawDegree"),
                if (f > 0) f.toDouble() else null
            )
        } ?: FlightInfo(null, null, null)
    } catch (e: Exception) { FlightInfo(null, null, null) }

    /** Name + size identify a photo, so the same picture is never analyzed twice. */
    fun fileKey(ctx: Context, uri: Uri): String {
        var name: String? = null
        var size = -1L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst()) { name = c.getString(0); size = c.getLong(1) } }
        } catch (e: Exception) { }
        return "${name ?: uri.lastPathSegment}|$size"
    }

    fun displayName(ctx: Context, uri: Uri): String {
        val name = try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) { null }
        return name ?: (uri.lastPathSegment ?: "photo")
    }
}