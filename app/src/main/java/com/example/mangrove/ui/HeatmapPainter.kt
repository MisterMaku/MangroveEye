package com.example.mangrove.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.RadialGradient
import android.graphics.Shader
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import com.example.mangrove.data.GisPoint
import com.example.mangrove.data.HeatmapPoint
import com.example.mangrove.data.QgisBoundary
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Paints the smooth health-density heatmap in one pass onto the map canvas:
 *
 *  - one soft radial blob per classified tile (green for mostly Healthy, red for mostly Unhealthy),
 *    its radius derived from the tile's ground size so blobs overlap into continuous fields;
 *  - the whole density field is CLIPPED to the irregular QGIS boundary polygon, so the colours
 *    never spill past the actual drone-imagery footprint;
 *  - colour stops follow the tile's density, matching the ramp in the Heatmap legend
 *    (Low -> Mild -> High Density).
 *
 * Used by HeatmapOverlay in OsmMap.kt.
 */
object HeatmapPainter {

    /** Density ramp for mostly-Healthy tiles: pale green (low) -> deep green (high). */
    private val HEALTHY_RAMP = intArrayOf(
        0xFFEEF9EA.toInt(),  // Low Density - pale
        0xFFA8F0A8.toInt(),  // Medium Density
        0xFF7FE07F.toInt(),  // Mild Density
        0xFF2A8A2A.toInt(),  // High Density - deep
    )

    /** Density ramp for mostly-Unhealthy tiles: pale coral (low) -> deep red (high). */
    private val UNHEALTHY_RAMP = intArrayOf(
        0xFFFFE5E0.toInt(),  // Low Density - pale
        0xFFFFA08A.toInt(),  // Medium Density
        0xFFFF7B5A.toInt(),  // Mild Density
        0xFFC81212.toInt(),  // High Density - deep red
    )

    /**
     * Draws the heatmap for [points] onto [c], clipped to [boundary] (the traced QGIS boundary).
     * When [boundary] is null nothing is drawn - callers decide what to show without one.
     */
    fun draw(c: Canvas, mapView: MapView, points: List<HeatmapPoint>, boundary: QgisBoundary?) {
        if (points.isEmpty() || boundary == null) return
        val proj = mapView.projection
        val p = Point()

        // Clip everything below to the irregular study-area polygon (all loops: outline + holes).
        val clip = Path()
        for (loop in boundary.loops) {
            loop.forEachIndexed { i, v ->
                proj.toPixels(GeoPoint(v.lat, v.lon), p)
                if (i == 0) clip.moveTo(p.x.toFloat(), p.y.toFloat())
                else clip.lineTo(p.x.toFloat(), p.y.toFloat())
            }
            clip.close()
        }
        val save = c.save()
        c.clipPath(clip)

        // One soft blob per classified tile. Radius comes from the tile's ground size converted
        // to pixels via the projection, so blobs keep their true ground size while zooming.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (t in points) {
            proj.toPixels(GeoPoint(t.lat, t.lon), p)
            val cx = p.x.toFloat()
            val cy = p.y.toFloat()

            // Blob radius: t.radiusM is half the tile's ground diagonal. Measure how many screen
            // pixels that many metres span at this latitude by projecting a point offset in lon.
            val lonPerM = 1.0 / (111_320.0 * kotlin.math.cos(Math.toRadians(t.lat)))
            val edge = proj.toPixels(GeoPoint(t.lat, t.lon + t.radiusM * lonPerM), p)
            val rPx = hypot(edge.x - cx.toDouble(), edge.y - cy.toDouble()).toFloat().coerceAtLeast(14f)

            // Pick the ramp by the tile's majority class and the colour stop by its density (0..1).
            val ramp: IntArray
            val density: Float
            if (t.healthyPct >= t.unhealthyPct) {
                ramp = HEALTHY_RAMP
                density = (t.healthyPct - t.unhealthyPct) / 100f
            } else {
                ramp = UNHEALTHY_RAMP
                density = (t.unhealthyPct - t.healthyPct) / 100f
            }
            val stop = (density * (ramp.size - 1)).roundToInt().coerceIn(0, ramp.size - 1)
            val core = ramp[stop]

            // Radial gradient: ramp colour at the centre, fully transparent at the blob edge.
            val g = RadialGradient(
                cx, cy, rPx,
                intArrayOf(core, withAlpha(core, 0.55f), Color.TRANSPARENT),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.shader = g
            c.drawCircle(cx, cy, rPx, paint)
        }
        paint.shader = null
        c.restoreToCount(save)
    }

    private fun withAlpha(color: Int, alpha: Float): Int =
        ((255 * alpha).roundToInt() shl 24) or (color and 0x00FFFFFF)
}
