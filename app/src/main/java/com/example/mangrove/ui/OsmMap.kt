package com.example.mangrove.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PointF
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mangrove.data.Assessment
import com.example.mangrove.data.BaseMap
import com.example.mangrove.data.Config
import com.example.mangrove.data.GisCell
import com.example.mangrove.data.GisClass
import com.example.mangrove.data.OrthoSource
import com.example.mangrove.data.Status
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
import kotlin.math.cos
import kotlin.math.hypot
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

/**
 * Draws one dot per assessment. Dots that would sit on top of each other are spread apart in a small
 * spiral (a thin line shows the true spot), so every dot stays visible and tappable. Zoom in and they
 * move back to their real positions.
 */
private class PinsOverlay : Overlay() {
    private class Spot(val a: Assessment, val x: Float, val y: Float, val tx: Float, val ty: Float)

    var items: List<Assessment> = emptyList()
    var selectedId: String? = null
    var onSelect: (String) -> Unit = {}
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xCCFFFFFF.toInt() }
    private var drawn = emptyList<Spot>()

    override fun draw(c: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val d = mapView.context.resources.displayMetrics.density
        val r = 9f * d
        ring.strokeWidth = 2.5f * d
        line.strokeWidth = 1.2f * d
        val proj = mapView.projection
        val pt = Point()
        val placed = ArrayList<PointF>()
        val spots = ArrayList<Spot>()
        for (a in items) {
            proj.toPixels(GeoPoint(a.lat, a.lon), pt)
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
            spots.add(Spot(a, x, y, pt.x.toFloat(), pt.y.toFloat()))
        }
        drawn = spots
        for (s in spots) if (s.x != s.tx || s.y != s.ty) c.drawLine(s.tx, s.ty, s.x, s.y, line)
        for (s in spots.sortedBy { it.a.id == selectedId }) {          // selected dot is drawn last (on top)
            val big = s.a.id == selectedId
            val rr = if (big) r * 1.3f else r
            fill.color = when (s.a.status) {
                Status.HEALTHY -> 0xFF4A7C59.toInt()
                Status.UNHEALTHY -> 0xFFB8493A.toInt()
                Status.NON_MANGROVE -> 0xFF8C9AA5.toInt()
                Status.OUT_OF_REACH -> 0xFFC98A3C.toInt()      // not drawn: Invalid photos have no pin (kept so the colour exists)
            }
            ring.color = android.graphics.Color.WHITE
            c.drawCircle(s.x, s.y, rr, fill)
            c.drawCircle(s.x, s.y, rr, ring)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val d = mapView.context.resources.displayMetrics.density
        val hit = drawn.minByOrNull { hypot(it.x - e.x, it.y - e.y) } ?: return false
        if (hypot(hit.x - e.x, hit.y - e.y) > 16f * d) return false
        onSelect(hit.a.id)
        return true
    }
}

private class MapState(var base: BaseMap? = null, var tick: Int = 0, var srcKey: String? = null) {
    val overlay = OrthoOverlay()
    val gis = GisOverlay()
    val pins = PinsOverlay()
    lateinit var scale: ScaleBarOverlay
    var lastSel: String? = null
}

/** Final step: "Display result on map" - base map + your QGIS orthomosaic + one colored pin per assessment. */
@Composable
fun OsmMap(
    items: List<Assessment>, selectedId: String?, baseMap: BaseMap, ortho: OrthoSource?, recenterTick: Int,
    onSelect: (String) -> Unit, modifier: Modifier,
    gisCells: List<GisCell> = emptyList(), gisVisible: Set<GisClass> = emptySet(),      // GIS health map layer
    gisSelected: GisCell? = null, onGisTap: (GisCell?) -> Unit = {}                     // tap a GIS tile to see its details
) {
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
                overlays.add(st.gis)
                st.scale = ScaleBarOverlay(this).apply {
                    setAlignBottom(true)
                    setUnitsOfMeasure(ScaleBarOverlay.UnitsOfMeasure.metric)
                    setLineWidth(2f)
                    setTextSize(11f * c.resources.displayMetrics.density)
                    isEnabled = false
                }
                overlays.add(st.scale)
                overlays.add(st.pins)
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
            st.gis.cells = gisCells
            st.gis.visible = gisVisible
            st.gis.selected = gisSelected
            st.gis.onTap = onGisTap
            st.scale.isEnabled = gisCells.isNotEmpty()      // scale bar only while the GIS tiles are shown
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
            if (st.lastSel != selectedId) {                           // selection changed: move the map to that dot
                st.lastSel = selectedId
                items.firstOrNull { it.id == selectedId }?.let { map.controller.animateTo(GeoPoint(it.lat, it.lon)) }
            }
            map.invalidate()
        }
    )
}