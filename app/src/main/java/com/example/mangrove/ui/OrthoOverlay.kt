package com.example.mangrove.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import com.example.mangrove.data.OrthoSource
import com.example.mangrove.data.TileMath
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.util.zip.ZipFile
import kotlin.math.ceil

/** Draws a QGIS orthomosaic (XYZ tiles inside a zip) on top of the base map. */
class OrthoOverlay : Overlay() {
    private var src: OrthoSource? = null
    private var zip: ZipFile? = null
    private var key = ""
    private val cache = HashMap<String, Bitmap?>()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val p1 = Point()
    private val p2 = Point()

    /** Switches to another QGIS map (or none). */
    fun setSource(s: OrthoSource?) {
        val k = s?.key ?: ""
        if (k != key) {
            try { zip?.close() } catch (e: Exception) { }
            zip = try { s?.let { ZipFile(it.file) } } catch (e: Exception) { null }
            cache.clear()
            key = k
        }
        src = s
    }

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val s = src ?: return
        val zf = zip ?: return
        // use the next sharper zoom level and shrink it (never stretch a smaller tile)
        val z = ceil(mapView.zoomLevelDouble - 0.01).toInt().coerceIn(s.minZoom, s.maxZoom)
        val proj = mapView.projection
        for (x in TileMath.tileX(s.west, z)..TileMath.tileX(s.east, z)) {
            for (y in TileMath.tileY(s.north, z)..TileMath.tileY(s.south, z)) {
                proj.toPixels(GeoPoint(TileMath.tileLat(y, z), TileMath.tileLon(x, z)), p1)
                proj.toPixels(GeoPoint(TileMath.tileLat(y + 1, z), TileMath.tileLon(x + 1, z)), p2)
                if (p2.x < 0 || p2.y < 0 || p1.x > c.width || p1.y > c.height) continue   // off screen
                val bmp = tile(zf, s, z, x, y) ?: continue
                c.drawBitmap(bmp, null, Rect(p1.x, p1.y, p2.x, p2.y), paint)
            }
        }
    }

    private fun tile(zf: ZipFile, s: OrthoSource, z: Int, x: Int, y: Int): Bitmap? {
        val k = "$z/$x/$y"
        if (cache.containsKey(k)) return cache[k]
        val entry = zf.getEntry(s.entryName(z, x, y))
        val bmp = try {
            entry?.let { e -> zf.getInputStream(e).use { BitmapFactory.decodeStream(it) } }
        } catch (e: Exception) { null }
        cache[k] = bmp
        return bmp
    }
}