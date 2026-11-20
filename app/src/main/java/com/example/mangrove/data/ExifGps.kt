package com.example.mangrove.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import java.util.Locale

/**
 * Shared utilities for geographic coordinate transformations.
 * Used by both ImageFootprint and GeoRotatableBox to avoid duplicate code.
 */
object GeoUtils {
    /** Convert meters per latitude to degrees per meter at the given latitude. */
    fun latPerMeterAt(lat: Double): Double = 1.0 / 111_320.0

    /** Convert meters per longitude to degrees per meter at the given latitude. */
    fun lonPerMeterAt(lat: Double): Double = 1.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)))

    /** Calculate corner coordinates for a rotated rectangle centered at (lat, lon). */
    fun calculateRotatedCorners(
        centerLat: Double,
        centerLon: Double,
        widthM: Double,
        heightM: Double,
        headingDeg: Double
    ): List<Pair<Double, Double>> {
        val cosLat = kotlin.math.cos(Math.toRadians(centerLat))
        val lonPerM = lonPerMeterAt(centerLat)
        val latPerM = latPerMeterAt(centerLat)
        val hRad = Math.toRadians(headingDeg)

        val halfW = widthM / 2.0
        val halfH = heightM / 2.0

        // Local directions in the box's coordinate system (right/fwd) rotated by heading
        // Corner offsets: (±halfW, ±halfH) in local coords
        val cornersLocal = listOf(
            -halfW to -halfH,   // bottom-left / southwest
            halfW to -halfH,    // bottom-right / southeast
            halfW to halfH,     // top-right / northeast
            -halfW to halfH     // top-left / northwest
        )

        val result = mutableListOf<Pair<Double, Double>>()
        for ((right, fwd) in cornersLocal) {
            val eastM = right * cos(hRad) + fwd * sin(hRad)
            val northM = fwd * cos(hRad) - right * sin(hRad)
            val cornerLat = centerLat + northM * latPerM
            val cornerLon = centerLon + eastM * lonPerM
            result.add(Pair(cornerLat, cornerLon))
        }
        return result
    }

    /** Rotate a point (lat, lon) by the given heading to get local coordinates. */
    fun toLocalCoords(
        centerLat: Double,
        centerLon: Double,
        pointLat: Double,
        pointLon: Double,
        headingDeg: Double
    ): Pair<Double, Double> {
        val latPerM = latPerMeterAt(centerLat)
        val lonPerM = lonPerMeterAt(centerLat)
        val hRad = Math.toRadians(headingDeg)
        val eastM = (pointLon - centerLon) / lonPerM
        val northM = (pointLat - centerLat) / latPerM
        val right = eastM * cos(hRad) - northM * sin(hRad)
        val fwd = eastM * sin(hRad) + northM * cos(hRad)
        return Pair(right, fwd)
    }

    /** Convert local coordinates (right, fwd) back to lat/lon given a center point and heading.
     *  This is the inverse of toLocalCoords.
     */
    fun fromLocalCoords(
        centerLat: Double,
        centerLon: Double,
        rightM: Double,
        fwdM: Double,
        headingDeg: Double
    ): Pair<Double, Double> {
        val hRad = Math.toRadians(headingDeg)
        // Rotate the local offset back to geographic frame
        val eastM = rightM * cos(hRad) - fwdM * sin(hRad)
        val northM = rightM * sin(hRad) + fwdM * cos(hRad)
        val latPerM = latPerMeterAt(centerLat)
        val lonPerM = lonPerMeterAt(centerLat)
        val pointLat = centerLat + northM * latPerM
        val pointLon = centerLon + eastM * lonPerM
        return Pair(pointLat, pointLon)
    }
}

/**
 * Flight data written by the DJI into the photo. A null means the photo does not say.
 * heightM: altitude above ground in metres
 * yawDeg: camera heading/direction in degrees (0 = north, 90 = east)
 * focal35mm: 35mm equivalent focal length
 */
data class FlightInfo(val heightM: Double?, val yawDeg: Double?, val focal35mm: Double?)

/**
 * Represents the actual ground footprint of a drone photo as a rotated polygon.
 * Calculated from GPS centre, altitude, camera specs, and orientation.
 */
data class ImageFootprint(
    val lat: Double,              // GPS centre latitude
    val lon: Double,              // GPS centre longitude
    val widthM: Double,           // width of footprint on ground (metres)
    val heightM: Double,          // height of footprint on ground (metres)
    val headingDeg: Double,       // camera orientation (degrees from north)
    val corners: List<Pair<Double, Double>> = emptyList()  // (lat, lon) pairs, counter-clockwise
) {
    /** Calculate the four corners of the rotated footprint. */
    fun calculateCorners(): List<Pair<Double, Double>> =
        GeoUtils.calculateRotatedCorners(lat, lon, widthM, heightM, headingDeg)

    /** Check if a point (lat, lon) is inside this footprint using rotated coordinates. */
    fun contains(pointLat: Double, pointLon: Double): Boolean {
        val (right, fwd) = localCoords(pointLat, pointLon)
        return kotlin.math.abs(right) <= widthM / 2 && kotlin.math.abs(fwd) <= heightM / 2
    }

    /** Rotate a point (lat, lon) by the footprint's heading to get local coordinates. */
    fun localCoords(lat: Double, lon: Double): Pair<Double, Double> =
        GeoUtils.toLocalCoords(this.lat, this.lon, lat, lon, headingDeg)

    /** Serialize to string: "lat,lon,width,height,heading" */
    fun encode(): String = String.format(Locale.US, "%.6f,%.6f,%.2f,%.2f,%.1f", lat, lon, widthM, heightM, headingDeg)

    companion object {
        /** Parse from string: "lat,lon,width,height,heading" */
        fun parse(s: String): ImageFootprint? {
            val p = s.split(",")
            if (p.size != 5) return null
            return try {
                val lat = p[0].toDouble()
                val lon = p[1].toDouble()
                val widthM = p[2].toDouble()
                val heightM = p[3].toDouble()
                val headingDeg = p[4].toDouble()
                ImageFootprint(
                    lat = lat,
                    lon = lon,
                    widthM = widthM,
                    heightM = heightM,
                    headingDeg = headingDeg,
                    corners = GeoUtils.calculateRotatedCorners(lat, lon, widthM, heightM, headingDeg)
                )
            } catch (e: Exception) { null }
        }
    }
}

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

    /**
     * Calculate the actual ground footprint of a drone photo based on EXIF metadata.
     * Uses flight data (altitude, heading) and the lens (35 mm equivalent focal length) to
     * determine the real-world area captured by the image.
     *
     * Same pinhole geometry as TileClassifier: the photo's diagonal on the ground
     * = altitude × 43.27 mm (full-frame diagonal) / focal length (35 mm equivalent).
     * The image aspect ratio then splits that diagonal into the width and height of the
     * footprint, so no physical sensor dimensions are needed (they vary between models and
     * the 35 mm equivalent already encodes the lens's true field of view).
     */
    fun calculateFootprint(ctx: Context, uri: Uri): ImageFootprint? {
        val latLon = read(ctx, uri) ?: return null
        val flight = flight(ctx, uri)

        // Use defaults when EXIF data is missing: matches TileClassifier's fallback logic.
        // Height: use relative altitude from flight, or fall back to Config.FLIGHT_HEIGHT_M (4 m).
        // Focal length: use EXIF value, or default to 24mm (DJI Mini 4K lens).
        val heightM = flight.heightM ?: Config.FLIGHT_HEIGHT_M
        val yawDeg = flight.yawDeg ?: 0.0  // Default to north if not available
        val focal35mm = flight.focal35mm ?: Config.FOCAL_35MM

        // Diagonal of the photo's ground footprint (pinhole camera, 35 mm equivalent lens):
        // a 24 mm lens on a full frame (43.27 mm diagonal) sees 43.27/24 ≈ 1.80x its distance.
        val diagonalM = heightM * 43.27 / focal35mm

        // Split the diagonal by the image's real aspect ratio (bounds-only decode: no bitmap pixels).
        val (w, h) = readImageDimensions(ctx, uri)
        val (footprintWidthM, footprintHeightM) = if (w > 0 && h > 0) {
            val diagPx = hypot(w.toDouble(), h.toDouble())
            (diagonalM * w / diagPx) to (diagonalM * h / diagPx)
        } else {
            // No readable dimensions: assume the DJI Mini's 4:3 photos (same default as the sensor's aspect).
            val diagPx = hypot(4.0, 3.0)
            (diagonalM * 4 / diagPx) to (diagonalM * 3 / diagPx)
        }

        return ImageFootprint(
            lat = latLon.first,
            lon = latLon.second,
            widthM = footprintWidthM,
            heightM = footprintHeightM,
            headingDeg = yawDeg,
            corners = GeoUtils.calculateRotatedCorners(latLon.first, latLon.second, footprintWidthM, footprintHeightM, yawDeg)
        )
    }

    /** Read the image's pixel dimensions (width, height) without decoding its pixels. */
    private fun readImageDimensions(ctx: Context, uri: Uri): Pair<Int, Int> {
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, opts)
                if (opts.outWidth > 0 && opts.outHeight > 0) opts.outWidth to opts.outHeight else 0 to 0
            } ?: (0 to 0)
        } catch (e: Exception) {
            0 to 0
        }
    }

    /** Name + size identify a photo, so the same picture is never analyzed twice. */
    fun fileKey(ctx: Context, uri: Uri): String {
        var name: String? = null
        var size = -1L
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { c -> if (c.moveToFirst()) { name = c.getString(0); size = c.getLong(1)} }
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