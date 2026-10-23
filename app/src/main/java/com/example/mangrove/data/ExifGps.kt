package com.example.mangrove.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface

/** Step: "Extract GPS from EXIF" (the DJI Mini 4K writes GPS into every JPG). */
object ExifGps {
    fun read(ctx: Context, uri: Uri): Pair<Double, Double>? = try {
        ctx.contentResolver.openInputStream(uri)?.use { s ->
            ExifInterface(s).latLong?.let { it[0] to it[1] }
        }
    } catch (e: Exception) { null }

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