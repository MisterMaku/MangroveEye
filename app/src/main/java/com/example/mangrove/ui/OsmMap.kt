package com.example.mangrove.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mangrove.theme.Mg
import com.example.mangrove.data.Assessment
import com.example.mangrove.data.BaseMap
import com.example.mangrove.data.Config
import com.example.mangrove.data.CoverageTracker
import com.example.mangrove.data.PinTile
import com.example.mangrove.data.GeoRotatableBox
import com.example.mangrove.data.OrthoSource
import com.example.mangrove.data.GisPoint
import com.example.mangrove.data.HeatmapPoint
import com.example.mangrove.data.QgisBoundary
import com.example.mangrove.data.Status
import com.example.mangrove.data.TileFeature
import androidx.core.view.doOnLayout
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val BG = 0xFFE8E4D6.toInt()   // shown where there are no base-map tiles (e.g. offline)

/** Satellite base map (Esri World Imagery). Tiles (c) Esri, Maxar, Earthstar Geographics. */
private val EsriSatellite = object : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(i: Long) =
        baseUrl + MapTileIndex.getZoom(i) + "/" + MapTileIndex.getY(i) + "/" + MapTileIndex.getX(i)
}

/** MapTiler satellite (needs your own key in Config.MAPTILER_KEY). Tiles (c) MapTiler (c) OpenStreetMap contributors. */
private val MapTilerSat = object : OnlineTileSourceBase(
    "MapTilerSatellite", 0, 19, 256, ".jpg", arrayOf("https://api.maptiler.com/tiles/satellite-v2/")
) {
    override fun getTileURLString(i: Long) =
        baseUrl + MapTileIndex.getZoom(i) + "/" + MapTileIndex.getX(i) + "/" + MapTileIndex.getY(i) +
                ".jpg?key=" + Config.MAPTILER_KEY
}

/** Zooms so the whole QGIS map fills the view, and stops the user zooming out past that view. */
private fun MapView.fitOrtho(o: OrthoSource) {
    doOnLayout {
        minZoomLevel = o.minZoom.toDouble()                       // let the fit pick any zoom first
        zoomToBoundingBox(BoundingBox(o.north, o.east, o.south, o.west), false, 12)
        minZoomLevel = zoomLevelDouble                            // cannot zoom out past this view
    }
}

/** Draws tile polygons. */
private class TileGisOverlay : Overlay() {
    var tiles: List<TileFeature>? = null
    var visible = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL_AND_STROKE }

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible) return
        val tiles = tiles ?: return
        val proj = mapView.projection
        val p1 = Point(); val p2 = Point()
        for (t in tiles) {
            proj.toPixels(GeoPoint(t.north, t.west), p1)
            proj.toPixels(GeoPoint(t.south, t.east), p2)
            paint.color = when (t.classification) {
                Status.HEALTHY -> 0x664CAF50.toInt()
                Status.UNHEALTHY -> 0x66E57373.toInt()
                else -> 0x669E9E9E.toInt()
            }
            c.drawRect(p1.x.toFloat(), p1.y.toFloat(), p2.x.toFloat(), p2.y.toFloat(), paint)
        }
    }
}

/**
 * Map 2 - Image Capture Tracking, two states (matching the legend):
 *
 *  - the whole selection area paints soft yellow ("Uncaptured") first, and
 *  - each captured pin then paints its own 4 m x 4 m square (centred exactly on the pin's GPS
 *    coordinates, see CoverageTracker / PinTile) soft green ("Captured") on top.
 *
 * Everything is clipped to the irregular QGIS study-area boundary (all loops: outline + holes),
 * and — when the user has set a selection box — to the box's rotated rectangle, so the yellow
 * never spills past either outline. No pin markers or dots are drawn. The overlay updates live
 * whenever a new photo is analyzed.
 */
private class PinTileOverlay : Overlay() {
    private class TileSpot(val tile: PinTile, val x: Float, val y: Float)

    var tiles: List<PinTile> = emptyList()
    var boundary: QgisBoundary? = null     // when set, the painting is clipped to the study-area outline
    var boundingBox: GeoRotatableBox? = null   // when set (non-default), also clipped to the selection box
    var visible = false
    var selectedId: String? = null
    var onSelect: (String) -> Unit = {}
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private var drawn = emptyList<TileSpot>()

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible) return
        val proj = mapView.projection
        val pt = Point()

        // Clip to the study-area polygon and the selection box, so the yellow paints only the
        // part of the selection area inside both outlines (same approach as GisOverlay).
        var save = -1
        var clipped = false
        val clip = Path()
        val b = boundary
        if (b != null && b.loops.isNotEmpty()) {
            val p = Point()
            for (loop in b.loops) {
                loop.forEachIndexed { i, v ->
                    proj.toPixels(GeoPoint(v.lat, v.lon), p)
                    if (i == 0) clip.moveTo(p.x.toFloat(), p.y.toFloat())
                    else clip.lineTo(p.x.toFloat(), p.y.toFloat())
                }
                clip.close()
            }
            clipped = true
        }
        val box = boundingBox
        if (box != null && !box.isDefault) {
            val p = Point()
            box.corners().forEachIndexed { i, v ->
                proj.toPixels(GeoPoint(v.first, v.second), p)
                if (i == 0) clip.moveTo(p.x.toFloat(), p.y.toFloat())
                else clip.lineTo(p.x.toFloat(), p.y.toFloat())
            }
            clip.close()
            clipped = true
        }
        if (clipped) {
            save = c.save()
            c.clipPath(clip)
        }

        // 1) Uncaptured: fill the whole clipped selection area yellow...
        fill.color = UNCAPTURED_FILL
        c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), fill)

        // 2) ...then one green 4 m x 4 m axis-aligned square per captured pin, centred on its
        //    exact GPS point.
        val half = CoverageTracker.TILE_M / 2.0
        val latPerM = 1.0 / 111_320.0
        val spots = ArrayList<TileSpot>()
        fill.color = CAPTURED_FILL
        for (t in tiles) {
            if (!t.captured) continue
            val lonPerM = 1.0 / (111_320.0 * cos(Math.toRadians(t.lat)))
            proj.toPixels(GeoPoint(t.lat + half * latPerM, t.lon - half * lonPerM), pt)   // NW corner
            val nwX = pt.x.toFloat(); val nwY = pt.y.toFloat()
            proj.toPixels(GeoPoint(t.lat - half * latPerM, t.lon + half * lonPerM), pt)   // SE corner
            val seX = pt.x.toFloat(); val seY = pt.y.toFloat()
            val left = minOf(nwX, seX)
            val top = minOf(nwY, seY)
            val right = maxOf(nwX, seX)
            val bottom = maxOf(nwY, seY)
            if (right < 0 || bottom < 0 || left > c.width || top > c.height) continue
            c.drawRect(left, top, right, bottom, fill)
            spots.add(TileSpot(t, (left + right) / 2f, (top + bottom) / 2f))
        }
        drawn = spots

        if (save >= 0) c.restoreToCount(save)
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (!visible) return false   // hidden (e.g. Map 1): never consume taps on stale positions
        val d = mapView.context.resources.displayMetrics.density
        // Tap near a captured tile centre; 32dp target, matching PinsOverlay
        val hitRadius = 16f * d
        val hit = drawn.minByOrNull { hypot(it.x - e.x, it.y - e.y) } ?: return false
        if (hypot(hit.x - e.x, hit.y - e.y) > hitRadius) return false
        onSelect(hit.tile.id)
        return true
    }

    companion object {
        private val CAPTURED_FILL = 0x554CAF50.toInt()      // green: Captured
        private val UNCAPTURED_FILL = 0x55FFD54F.toInt()    // yellow: Uncaptured
    }
}

/**
 * Draws the irregular QGIS study-area boundary as a thin black outline.
 * Uses even-odd stroke to create a crisp, non-varying line appearance.
 * The polygon is traced from the actual tile imagery (see QgisBoundary), so it hugs
 * the real drone-imagery footprint instead of the bounding rectangle.
 * In Map 2 - Capture and GIS view, this is always visible.
 */
private class BoundaryOverlay : Overlay() {
    var boundary: QgisBoundary? = null
    var visible = false
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val path = Path()
    private val p = Point()
    private var pathKey: String? = null

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible) return
        val b = boundary ?: return
        val proj = mapView.projection
        val d = mapView.context.resources.displayMetrics.density

        // Rebuild the screen-space polygon only when the projection changes (pan / zoom).
        val center = mapView.getMapCenter()
        val key = "${mapView.zoomLevelDouble}:${center.getLatitude()}:${center.getLongitude()}"
        if (key != pathKey) {
            path.reset()
            for (loop in b.loops) {
                loop.forEachIndexed { i, v ->
                    proj.toPixels(GeoPoint(v.lat, v.lon), p)
                    if (i == 0) path.moveTo(p.x.toFloat(), p.y.toFloat())
                    else path.lineTo(p.x.toFloat(), p.y.toFloat())
                }
                path.close()
            }
            pathKey = key
        }
        outline.strokeWidth = 1.5f * d  // thin outline
        outline.color = BOUNDARY_COLOR
        outline.setPathEffect(null)  // crisp solid line, no variation
        c.drawPath(path, outline)
    }

    companion object {
        private val BOUNDARY_COLOR = 0xFF000000.toInt()     // black: thin study-area outline
    }
}

/**
 * Draws one dot per assessment. Dots that would sit on top of each other are spread apart in a small
 * spiral (a thin line shows the true spot), so every dot stays visible and tappable. Zoom in and they
 * move back to their real positions.
 * Pin visual size is half diameter (smaller), but tap target stays at least 32px.
 */
private class PinsOverlay : Overlay() {
    private class Spot(val a: Assessment, val x: Float, val y: Float, val tx: Float, val ty: Float)

    var items: List<Assessment> = emptyList()
    var selectedId: String? = null
    var onSelect: (String) -> Unit = {}
    var visible = true
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xCCFFFFFF.toInt() }
    private var drawn = emptyList<Spot>()

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible) return
        val d = mapView.context.resources.displayMetrics.density
        // Visual radius is half of the original (9f -> 4.5f), making pin diameter smaller
        // but hit area for tapping stays at least 32dp (16f * d for radius = 32dp)
        val r = 4.5f * d   // visual radius (reduced from 9f * d)
        val hitRadius = 16f * d  // for overlap detection (keeps 32dp tap target)
        ring.strokeWidth = 2f * d
        line.strokeWidth = 1.2f * d
        val proj = mapView.projection
        val pt = Point()
        val placed = ArrayList<PointF>()
        val spots = ArrayList<Spot>()

        // Sort items so that pins with same location are ordered by ID for consistent stacking
        val sortedItems = items.sortedBy { it.id }

        for (a in sortedItems) {
            proj.toPixels(GeoPoint(a.lat, a.lon), pt)
            var x = pt.x.toFloat()
            var y = pt.y.toFloat()
            var k = 0
            // Use visual radius for overlap detection
            while (k < 40 && placed.any { hypot(it.x - x, it.y - y) < 2f * r }) {
                k++
                val ang = k * 2.4f
                val rad = 1.1f * r * sqrt(k.toFloat())
                x = pt.x + rad * cos(ang)
                y = pt.y + rad * sin(ang)
            }
            placed.add(PointF(x, y))
            spots.add(Spot(a, x, y, pt.x.toFloat(), pt.y.toFloat()))
        }
        drawn = spots
        for (s in spots) if (s.x != s.tx || s.y != s.ty) c.drawLine(s.tx, s.ty, s.x, s.y, line)
        // Draw smaller pins, with selected on top
        for (s in spots.sortedBy { it.a.id == selectedId }) {
            val rr = r   // same visual size for all pins (selected doesn't scale up differently)
            fill.color = when (s.a.status) {
                Status.HEALTHY -> 0xFF4A7C59.toInt()
                Status.UNHEALTHY -> 0xFFB8493A.toInt()
                Status.NON_MANGROVE -> 0xFF8C9AA5.toInt()
                else -> 0xFF4A7C59.toInt()  // fallback for any other status
            }
            ring.color = android.graphics.Color.WHITE
            c.drawCircle(s.x, s.y, rr, fill)
            c.drawCircle(s.x, s.y, rr, ring)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val d = mapView.context.resources.displayMetrics.density
        // Use hitRadius (32dp) for tap detection, not visual radius
        val hitRadius = 16f * d
        val hit = drawn.minByOrNull { hypot(it.x - e.x, it.y - e.y) } ?: return false
        if (hypot(hit.x - e.x, hit.y - e.y) > hitRadius) return false
        onSelect(hit.a.id)
        return true
    }
}

/**
 * Draws individual assessment pins.
 * Each pin represents an assessed drone photo location.
 */
private class AssessmentPinsOverlay : Overlay() {
    data class AssessmentPin(val id: String, val lat: Double, val lon: Double)

    var items: List<AssessmentPin> = emptyList()
    var selectedId: String? = null
    var onSelect: (String) -> Unit = {}
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xCCFFFFFF.toInt() }
    private var drawn = emptyList<Triple<AssessmentPin, Float, Float>>()  // (item, screenX, screenY)

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val d = mapView.context.resources.displayMetrics.density
        val r = 9f * d
        ring.strokeWidth = 2.5f * d
        line.strokeWidth = 1.2f * d
        val proj = mapView.projection
        val pt = Point()
        val placed = ArrayList<PointF>()
        val spots = ArrayList<Triple<AssessmentPin, Float, Float>>()

        for (item in items) {
            proj.toPixels(GeoPoint(item.lat, item.lon), pt)
            var x = pt.x.toFloat()
            var y = pt.y.toFloat()
            var k = 0
            while (k < 40 && placed.any { hypot(it.x - x, it.y - y) < 2f * r }) {
                k++
                val ang = k * 2.4f
                val rad = 1.1f * r * sqrt(k.toFloat())
                x = pt.x + rad * cos(ang)
                y = pt.y + rad * sin(ang)
            }
            placed.add(PointF(x, y))
            spots.add(Triple(item, x, y))
        }
        drawn = spots

        for (s in spots.sortedBy { it.first.id == selectedId }) {
            val item = s.first
            val big = item.id == selectedId
            val rr = if (big) r * 1.3f else r
            fill.color = 0xFF4A7C59.toInt()  // Healthy-like green for all pins
            c.drawCircle(s.second, s.third, rr, fill)
            c.drawCircle(s.second, s.third, rr, ring)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val d = mapView.context.resources.displayMetrics.density
        val hit = drawn.minByOrNull { hypot(it.second - e.x, it.third - e.y) } ?: return false
        if (hypot(hit.second - e.x, hit.third - e.y) > 16f * d) return false
        onSelect(hit.first.id)
        return true
    }
}

/**
 * Draws the smooth health-density heatmap via HeatmapPainter: one soft green/red blob per
 * classified tile, blended into continuous density fields and CLIPPED to the traced QGIS
 * boundary, so colours never spill past the real drone-imagery footprint.
 * Green = Healthy, red = Unhealthy; intensity follows each tile's density.
 */
private class HeatmapOverlay : Overlay() {
    var points: List<HeatmapPoint> = emptyList()
    var boundary: QgisBoundary? = null
    var visible = false

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible) return
        HeatmapPainter.draw(c, mapView, points, boundary)
    }
}

/**
 * Draws the GIS visualization: one soft radial cluster per assessment (green for Healthy,
 * red for Unhealthy), CLIPPED to the traced QGIS boundary so nothing appears outside the
 * study area. Unlike the tile-based HeatmapOverlay, each assessment produces its own cluster,
 * so the GIS view shows one intensity field per assessment rather than one per 512 px tile.
 * A white fill rectangle is drawn behind everything so the view shows a clean white canvas
 * (no satellite imagery or base-map tiles).
 *
 * Density levels (based on healthy/unhealthy percentage):
 * - Low Density: 0-25%
 * - Medium Density: 26-50%
 * - Mild Density: 51-75%
 * - High Density: 76-100%
 */
private class GisOverlay : Overlay() {
    var points: List<GisPoint> = emptyList()
    var boundary: QgisBoundary? = null
    var visible = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible) return
        val b = boundary ?: return
        val proj = mapView.projection
        val p = Point()

        // Draw a white background across the full map canvas so the GIS view is clean.
        paint.color = Color.WHITE
        paint.shader = null
        c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), paint)

        // Clip everything below to the irregular study-area polygon (all loops: outline + holes).
        val clip = Path()
        for (loop in b.loops) {
            loop.forEachIndexed { i, v ->
                proj.toPixels(GeoPoint(v.lat, v.lon), p)
                if (i == 0) clip.moveTo(p.x.toFloat(), p.y.toFloat())
                else clip.lineTo(p.x.toFloat(), p.y.toFloat())
            }
            clip.close()
        }
        val save = c.save()
        c.clipPath(clip)

        // One soft radial cluster per assessment at its real GPS point.
        for (t in points) {
            proj.toPixels(GeoPoint(t.lat, t.lon), p)
            val cx = p.x.toFloat()
            val cy = p.y.toFloat()

            // Blob radius in metres -> screen pixels at this latitude.
            val lonPerM = 1.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(t.lat)))
            val edge = proj.toPixels(GeoPoint(t.lat, t.lon + t.radiusM * lonPerM), p)
            val rPx = hypot(edge.x - cx.toDouble(), edge.y - cy.toDouble()).toFloat().coerceAtLeast(14f)

            // Pick the core colour by the assessment's majority class and density level.
            // Density levels: Low (0-25%), Medium (26-50%), Mild (51-75%), High (76-100%)
            val isHealthy = t.healthyPct >= t.unhealthyPct
            val densityPct = if (isHealthy) t.healthyPct else t.unhealthyPct

            val core = if (isHealthy) {
                // Healthy ramps: Low -> Medium -> Mild -> High
                when {
                    densityPct >= 76 -> 0xFF2A8A2A.toInt()    // High Density - dark green
                    densityPct >= 51 -> 0xFF7FE07F.toInt()    // Mild Density - medium green
                    densityPct >= 26 -> 0xFFA8F0A8.toInt()   // Medium Density - pale green
                    else -> 0xFFEEF9EA.toInt()                 // Low Density - very pale green
                }
            } else {
                // Unhealthy ramps: Low -> Medium -> Mild -> High
                when {
                    densityPct >= 76 -> 0xFFC81212.toInt()    // High Density - dark red
                    densityPct >= 51 -> 0xFFFF7B5A.toInt()    // Mild Density - warm orange-red
                    densityPct >= 26 -> 0xFFFFA08A.toInt()   // Medium Density - pale coral
                    else -> 0xFFFFE5E0.toInt()               // Low Density - very pale red
                }
            }

            // Create smooth gradient from core color to transparent
            val g = RadialGradient(
                cx, cy, rPx,
                intArrayOf(core, withAlpha(core, 0.6f), Color.TRANSPARENT),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = g
            c.drawCircle(cx, cy, rPx, paint)
        }
        paint.shader = null
        c.restoreToCount(save)
    }

    private fun withAlpha(color: Int, alpha: Float): Int {
        val alphaBytes = (255 * alpha).roundToInt()
        return (alphaBytes shl 24) or (color and 0x00FFFFFF.toInt())
    }
}

/**
 * Interactive rotatable bounding box for Map 2 - Capture.
 * Semi-transparent dark fill with black outline, four draggable yellow corner handles,
 * and rotation capability. Geography-aligned: moves/resizes in real map coordinates.
 */
private class BoundingBoxOverlay : Overlay() {
    var box: GeoRotatableBox? = null
    var boundary: QgisBoundary? = null
    var visible = false
    var onChange: (GeoRotatableBox?) -> Unit = {}
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFEB3B.toInt()  // yellow
    }

    // Interaction state
    private var draggingHandle: Int? = null    // 0-3 for corners
    private var dragStartScreenX = 0f
    private var dragStartScreenY = 0f
    private var dragStartLat = 0.0
    private var dragStartLon = 0.0
    private var initialBox: GeoRotatableBox? = null
    private var moveMode = 0                   // 0 = move center, 1 = resize
    private var isDragging = false
    private var handleRadiusPx = 0f            // cached handle hit radius in pixels

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || !visible) return
        val b = box ?: return
        val proj = mapView.projection
        val d = mapView.context.resources.displayMetrics.density

        val corners = b.corners()
        if (corners.isEmpty()) return

        // Draw filled rectangle
        fill.color = 0x80333333.toInt()   // semi-transparent dark
        fill.style = Paint.Style.FILL
        val path = Path()
        corners.forEachIndexed { i, (lat, lon) ->
            val pt = Point()
            proj.toPixels(GeoPoint(lat, lon), pt)
            if (i == 0) path.moveTo(pt.x.toFloat(), pt.y.toFloat())
            else path.lineTo(pt.x.toFloat(), pt.y.toFloat())
        }
        path.close()
        c.drawPath(path, fill)

        // Draw black outline
        outline.color = 0xFF000000.toInt()
        outline.style = Paint.Style.STROKE
        outline.strokeWidth = 2f * d
        outline.setPathEffect(null)
        c.drawPath(path, outline)

        // Draw yellow corner handles
        handleRadiusPx = 10f * d
        for ((lat, lon) in corners) {
            val pt = Point()
            proj.toPixels(GeoPoint(lat, lon), pt)
            c.drawCircle(pt.x.toFloat(), pt.y.toFloat(), handleRadiusPx, handlePaint)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val b = box ?: return false
        val proj = mapView.projection
        val d = mapView.context.resources.displayMetrics.density
        val corners = b.corners()
        if (corners.isEmpty()) return false

        // Convert tap to geographic
        val tapPoint = proj.fromPixels(e.x.toInt(), e.y.toInt())
        val tapLat = tapPoint.latitude
        val tapLon = tapPoint.longitude

        // Find which handle (if any) was tapped
        val centerLat = b.centerLat
        val centerLon = b.centerLon
        val hRad = Math.toRadians(b.headingDeg)
        val lonPerM = 1.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(b.centerLat)))
        val latPerM = 1.0 / 111_320.0

        // Check each corner
        for (i in 0..3) {
            val (lat, lon) = corners[i]
            val screenPt = Point()
            proj.toPixels(GeoPoint(lat, lon), screenPt)
            if (hypot(e.x - screenPt.x.toFloat(), e.y - screenPt.y.toFloat()) < handleRadiusPx) {
                // Found the tapped handle
                isDragging = true
                draggingHandle = i
                dragStartScreenX = e.x
                dragStartScreenY = e.y
                dragStartLat = tapLat
                dragStartLon = tapLon
                initialBox = b
                moveMode = 1   // resize mode
                mapView.isPressed = true
                return true
            }
        }

        // Check if tap is near the center (for moving)
        val centerPt = Point()
        proj.toPixels(GeoPoint(centerLat, centerLon), centerPt)
        if (hypot(e.x - centerPt.x.toFloat(), e.y - centerPt.y.toFloat()) < handleRadiusPx * 1.5f) {
            isDragging = true
            draggingHandle = null
            dragStartScreenX = e.x
            dragStartScreenY = e.y
            dragStartLat = tapLat
            dragStartLon = tapLon
            initialBox = b
            moveMode = 0   // move mode
            mapView.isPressed = true
            return true
        }

        return false
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (!isDragging) return false

        when (event.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                draggingHandle = null
                initialBox = null
                moveMode = 0
                mapView.isPressed = false
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val currentBox = initialBox ?: return false
                val b = currentBox
                val proj = mapView.projection

                if (moveMode == 0) {
                    // Move the centre
                    val currentPt = proj.fromPixels(event.x.toInt(), event.y.toInt())
                    val deltaLat = currentPt.latitude - dragStartLat
                    val deltaLon = currentPt.longitude - dragStartLon

                    // Clamp to boundary
                    val newLat = dragStartLat + deltaLat
                    val newLon = dragStartLon + deltaLon
                    if (boundary?.contains(newLat, newLon) == true || boundary == null) {
                        val newBox = b.copy(centerLat = newLat, centerLon = newLon)
                        box = newBox
                        onChange(newBox)
                        mapView.invalidate()
                    }
                } else if (moveMode == 1 && draggingHandle != null) {
                    // Resize from corner
                    val handleIdx = draggingHandle!!
                    val origCorners = b.corners()
                    if (origCorners.isEmpty()) return true

                    val currentPt = proj.fromPixels(event.x.toInt(), event.y.toInt())
                    val newLat = currentPt.latitude
                    val newLon = currentPt.longitude

                    // Calculate new width/height from the dragged corner
                    val dLat = newLat - dragStartLat
                    val dLon = newLon - dragStartLon

                    // Convert to metres
                    val latPerM = 1.0 / 111_320.0
                    val cosLat = kotlin.math.cos(Math.toRadians(b.centerLat))
                    val lonPerM = 1.0 / (111_320.0 * cosLat)

                    // Get the original corners relative to centre
                    val origRight = (dragStartLon - b.centerLon) / lonPerM
                    val origFwd = (dragStartLat - b.centerLat) / latPerM
                    val dx = (newLon - dragStartLon) / lonPerM
                    val dy = (newLat - dragStartLat) / latPerM

                    // New dimensions in local coordinates
                    val hRad = Math.toRadians(b.headingDeg)
                    val newRight = origRight + dx * cos(hRad) - dy * sin(hRad)
                    val newFwd = origFwd + dx * sin(hRad) + dy * cos(hRad)

                    val newHalfW = kotlin.math.abs(newRight)
                    val newHalfH = kotlin.math.abs(newFwd)

                    // Ensure minimum size
                    val minW = 50.0
                    val minH = 50.0
                    val finalW = maxOf(newHalfW * 2, minW)
                    val finalH = maxOf(newHalfH * 2, minH)

                    val newBox = b.copy(widthM = finalW, heightM = finalH)
                    box = newBox
                    onChange(newBox)
                    mapView.invalidate()
                }

                return true
            }
        }
        return false
    }
}

private class MapState(var base: BaseMap? = null, var tick: Int = 0, var srcKey: String? = null) {
    val overlay = OrthoOverlay()
    val pins = PinsOverlay()
    val tileGis = TileGisOverlay()
    val pinTiles = PinTileOverlay()
    val heatmap = HeatmapOverlay()
    val gis = GisOverlay()
    val boundary = BoundaryOverlay()
    val boundingBox = BoundingBoxOverlay()
    lateinit var scale: ScaleBarOverlay
    var lastSel: String? = null
}

/** Draws the mangrove health density heatmap and study area boundary. */

/** Final step: "Display result on map" - base map + your QGIS orthomosaic + one colored pin per assessment. */
@Composable
fun OsmMap(
    items: List<Assessment>, selectedId: String?, baseMap: BaseMap, ortho: OrthoSource?, recenterTick: Int,
    onSelect: (String) -> Unit, modifier: Modifier,
    showPins: Boolean = true,                                                          // show assessment pins on Map 1
    showTileGis: Boolean = false,                                                      // show individual tile polygons
    tileGisFeatures: List<TileFeature> = emptyList(),                                     // tile GIS features
    pinTiles: List<PinTile> = emptyList(),                                                 // Map 2: one 4 m x 4 m tile per pin
    showCoverage: Boolean = false,                                                       // Map 2: capture tracking overlay on/off
    showHeatmap: Boolean = false,                                                        // Heatmap: smooth health-density blobs
    heatmapPoints: List<HeatmapPoint> = emptyList(),                                    // Heatmap: blob centres
    showGis: Boolean = false,                                                            // GIS: one cluster per assessment
    gisPoints: List<GisPoint> = emptyList(),                                             // GIS: cluster centres
    showBoundary: Boolean = false,                                                       // traced study-area outline
    boundingBox: GeoRotatableBox? = null,                                                // Map 2: selection rectangle
    onBoundingBoxChanged: (GeoRotatableBox?) -> Unit = {},                              // callback for box changes
    captureItems: List<Assessment> = emptyList()                                         // Map 2: pin markers at assessment locations (excludes OUT_OF_REACH)
) {
    // The boundary is traced from the tile imagery once per map and cached in QgisBoundary.
    val traced = remember(ortho?.key) { ortho?.let { QgisBoundary.trace(it) } }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            Configuration.getInstance().userAgentValue = Config.USER_AGENT
            MapView(c).apply {
                val st = MapState()
                tag = st
                setBackgroundColor(BG)
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.ALWAYS)   // always show + / - buttons
                overlayManager.tilesOverlay.loadingBackgroundColor = BG
                overlayManager.tilesOverlay.loadingLineColor = BG
                // QGIS orthomosaic (built-in or the user's own), drawn under the pins - works offline
                overlays.add(st.overlay)
                overlays.add(st.tileGis)  // Tile-based overlay for individual tiles
                st.tileGis.visible = false  // starts hidden, user toggles with chip
                overlays.add(st.heatmap)  // Heatmap: smooth health-density blobs
                st.heatmap.visible = false
                overlays.add(st.gis)  // GIS: one cluster per assessment
                st.gis.visible = false
                overlays.add(st.boundary)  // traced study-area outline
                st.boundary.visible = false
                overlays.add(st.boundingBox)  // interactive selection box
                st.boundingBox.visible = false
                st.scale = ScaleBarOverlay(this).apply {
                    setAlignBottom(true)
                    setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric)
                    setLineWidth(2f)
                    setTextSize(11f * c.resources.displayMetrics.density)
                    isEnabled = false
                }
                overlays.add(st.scale)
                overlays.add(st.pins)
                overlays.add(st.pinTiles)  // Map 2: captured/uncaptured painting per pin
                if (ortho != null) {
                    st.overlay.setSource(ortho)
                    st.srcKey = ortho.key
                    maxZoomLevel = (ortho.maxZoom + Config.EXTRA_ZOOM).toDouble()   // limit zoom in
                    fitOrtho(ortho)                                                  // start on the whole QGIS map
                }
            }
        },
        update = { map ->
            val st = map.tag as MapState
            if (st.base != baseMap) {
                st.base = baseMap
                when (baseMap) {
                    BaseMap.SATELLITE -> map.setTileSource(EsriSatellite)
                    BaseMap.MAPTILER -> map.setTileSource(MapTilerSat)
                    BaseMap.NONE -> {}
                }
            }
            map.overlayManager.tilesOverlay.isEnabled = baseMap != BaseMap.NONE
            st.overlay.setSource(ortho)
            if (ortho != null && st.srcKey != ortho.key) {          // the user switched QGIS map
                st.srcKey = ortho.key
                map.maxZoomLevel = (ortho.maxZoom + Config.EXTRA_ZOOM).toDouble()
                map.fitOrtho(ortho)
            }
            if (st.tick != recenterTick) {
                st.tick = recenterTick
                ortho?.let { map.fitOrtho(it) }
            }

            st.pins.items = items
            st.pins.selectedId = selectedId
            st.pins.onSelect = onSelect
            st.pins.visible = showPins
            // Animation: pan to selected assessment (works across all views via captureItems)
            val targetForAnim = items.ifEmpty { captureItems }.firstOrNull { it.id == selectedId }
            if (st.lastSel != selectedId) {
                st.lastSel = selectedId
                targetForAnim?.let { map.controller.animateTo(GeoPoint(it.lat, it.lon)) }
            }
            st.tileGis.visible = showTileGis      // update visibility based on ViewModel state
            st.tileGis.tiles = tileGisFeatures   // Pass TileGis features to overlay

            // Heatmap: smooth health-density blobs, clipped to the traced boundary
            st.heatmap.visible = showHeatmap
            st.heatmap.points = heatmapPoints
            st.heatmap.boundary = traced

            // GIS: one cluster per assessment, white canvas, clipped to the traced boundary
            st.gis.visible = showGis
            st.gis.points = gisPoints
            st.gis.boundary = traced

            // The QGIS orthomosaic is hidden in the GIS view: a plain white canvas is drawn instead.
            st.overlay.visible = !(showGis && ortho != null)

            // Set white background for GIS view (so heatmap clusters don't overlap with gray tiles)
            if (showGis) {
                map.setBackgroundColor(Color.WHITE)
                map.overlayManager.tilesOverlay.isEnabled = false
            } else {
                map.setBackgroundColor(BG)
                map.overlayManager.tilesOverlay.isEnabled = baseMap != BaseMap.NONE
            }

            // Traced study-area outline (Boundary chip)
            st.boundary.visible = showBoundary
            st.boundary.boundary = traced

            // Map 2 - Bounding Box overlay (interactive selection rectangle)
            st.boundingBox.visible = boundingBox != null
            st.boundingBox.box = boundingBox
            st.boundingBox.boundary = traced
            st.boundingBox.onChange = { newBox -> onBoundingBoxChanged(newBox) }

            // Map 2 - Image Capture Tracking: one tile per pin, clipped to the study-area outline.
            // Tiles already exclude outOfReach assessments (no tile is built for them in CoverageTracker).
            st.pinTiles.visible = showCoverage
            st.pinTiles.tiles = pinTiles
            st.pinTiles.boundary = traced
            st.pinTiles.boundingBox = boundingBox
            st.pinTiles.selectedId = selectedId
            st.pinTiles.onSelect = onSelect

            map.invalidate()
        }
    )
}