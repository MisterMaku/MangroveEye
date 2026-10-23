package com.example.mangrove.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.view.MotionEvent
import com.example.mangrove.data.GisCell
import com.example.mangrove.data.GisClass
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** Draws the GIS cells as coloured square tiles on the map (like the zoning layer in a GIS program). */
class GisOverlay : Overlay() {
    var cells: List<GisCell> = emptyList()
    var visible: Set<GisClass> = emptySet()
    var selected: GisCell? = null                 // the tile you tapped (drawn with a dark outline)
    var onTap: (GisCell?) -> Unit = {}
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pick = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF1C2B24.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x88FFFFFF.toInt(); strokeWidth = 1.5f }
    private val p1 = Point()
    private val p2 = Point()

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || cells.isEmpty()) return
        val proj = mapView.projection
        for (cell in cells) {
            if (cell.cls !in visible) continue
            proj.toPixels(GeoPoint(cell.north, cell.west), p1)
            proj.toPixels(GeoPoint(cell.south, cell.east), p2)
            if (p2.x < 0 || p2.y < 0 || p1.x > c.width || p1.y > c.height) continue    // off screen
            fill.color = (cell.cls.color and 0x00FFFFFF) or (0xB8 shl 24)               // semi-transparent
            c.drawRect(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), fill)
            c.drawRect(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), edge)
        }
        selected?.let { s ->                                                            // outline of the tapped tile
            pick.strokeWidth = 3f * mapView.context.resources.displayMetrics.density
            proj.toPixels(GeoPoint(s.north, s.west), p1)
            proj.toPixels(GeoPoint(s.south, s.east), p2)
            c.drawRect(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), pick)
        }
    }

    /** Tap on the map: find the GIS tile under the finger (pins are drawn above this layer, so they get the tap first). */
    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (cells.isEmpty()) return false
        val g = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
        val hit = cells.firstOrNull {
            it.cls in visible && g.latitude <= it.north && g.latitude >= it.south && g.longitude >= it.west && g.longitude <= it.east
        }
        onTap(hit)
        return hit != null
    }
}