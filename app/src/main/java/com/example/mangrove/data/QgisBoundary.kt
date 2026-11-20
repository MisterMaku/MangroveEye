package com.example.mangrove.data

import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sinh

/**
 * One vertex of the QGIS study-area boundary polygon.
 */
data class BoundaryVertex(val lat: Double, val lon: Double)

/**
 * The irregular QGIS study-area boundary, TRACED from the actual tile imagery of an [OrthoSource].
 *
 * How the trace works ([QgisBoundary.trace]):
 *  1. Pick the sharpest zoom whose tile count is workable (more tiles = finer outline).
 *  2. Decode every tile and classify each small square of it (about SUBCELL_PX pixels) as imagery
 *     when at least 30% of its sampled pixels are opaque. The orthomosaic's empty surroundings
 *     (water / mud outside the drone imagery) are transparent, so the imagery squares are exactly
 *     the area that carries real drone imagery.
 *  3. Walk the edges between imagery and empty squares to build closed boundary loops (a staircase
 *     in sub-cell coordinates), following the same imagery rule as OrthoSource.hasImagery.
 *  4. Round the staircase with two passes of Chaikin corner-cutting and convert to lat/lon.
 *
 * The result hugs the real drone-imagery footprint instead of the bounding rectangle. If anything
 * goes wrong (unreadable zip, all tiles flat), [trace] falls back to the ortho bounding rectangle.
 *
 * Used by:
 *  - the study-area outline overlay on the Map screen (BoundaryOverlay in OsmMap.kt)
 *  - clipping the heatmap so density colours stop at the imagery edge (HeatmapPainter)
 *  - an exact inside/outside test ([contains], even-odd across all loops)
 *
 * Results are cached per map (key = file path + last-modified), so the zip is read once.
 */
class QgisBoundary private constructor(
    /** Closed boundary loops (outer outline + possible holes), vertices in order. */
    val loops: List<List<BoundaryVertex>>,
    /** True when traced from the imagery; false when it fell back to the bounding rectangle. */
    val fromImagery: Boolean
) {
    /** The main (largest) loop - the outer outline of the study area. */
    val vertices: List<BoundaryVertex> get() = loops.maxByOrNull { it.size } ?: emptyList()

    val north: Double get() = loops.maxOf { loop -> loop.maxOf { it.lat } }
    val south: Double get() = loops.minOf { loop -> loop.minOf { it.lat } }
    val west: Double get() = loops.minOf { loop -> loop.minOf { it.lon } }
    val east: Double get() = loops.maxOf { loop -> loop.maxOf { it.lon } }

    /**
     * Polygon area in m2 (shoelace formula per loop, sum of absolute areas, with vertices treated
     * as a local planar approximation). Good enough for display; the coverage numbers come from
     * CoverageTracker.
     */
    val areaM2: Double get() {
        if (loops.isEmpty()) return 0.0
        val mPerLat = 111_320.0
        val mPerLon = 111_320.0 * cos(Math.toRadians((north + south) / 2.0))
        var total = 0.0
        for (loop in loops) {
            if (loop.size < 3) continue
            var sum = 0.0
            var j = loop.size - 1
            for (i in loop.indices) {
                sum += (loop[j].lon - loop[i].lon) * mPerLon * (loop[j].lat + loop[i].lat) * mPerLat / 2.0
                j = i
            }
            total += abs(sum) / 2.0
        }
        return total
    }

    /**
     * True if (lat, lon) is inside the boundary - even-odd rule across all loops, so holes
     * (rare, but possible in a traced footprint) subtract correctly.
     */
    fun contains(lat: Double, lon: Double): Boolean {
        var inside = false
        for (loop in loops) if (pointInLoop(loop, lat, lon)) inside = !inside
        return inside
    }

    private fun pointInLoop(loop: List<BoundaryVertex>, lat: Double, lon: Double): Boolean {
        var inside = false
        var j = loop.size - 1
        for (i in loop.indices) {
            val yi = loop[i].lat; val xi = loop[i].lon
            val yj = loop[j].lat; val xj = loop[j].lon
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    companion object {
        private const val MAX_TILES = 400          // trace at a zoom with at most this many tiles
        private const val OPAQUE_ALPHA = 200       // pixel alpha at/above this counts as imagery
        private const val SUBCELL_PX = 16          // boundary resolution: classify each ~16px square
        private const val SUBCELL_OPAQUE_FRACTION = 0.30  // square is imagery when >= 30% opaque
        private val ENTRY = Regex("""^(?:(.+)/)?(\d{1,2})/(\d+)/(\d+)\.(png|jpg|jpeg)$""", RegexOption.IGNORE_CASE)

        private val cache = HashMap<String, QgisBoundary>()

        /** Traces (or fetches from cache) the boundary of the given QGIS map. Never returns null. */
        fun trace(ortho: OrthoSource): QgisBoundary {
            synchronized(cache) { cache[ortho.key]?.let { return it } }
            val b = try {
                traceInternal(ortho)
            } catch (e: Exception) {
                android.util.Log.w("QgisBoundary", "trace failed, using bounding rectangle: ${e.message}")
                rect(ortho)
            }
            synchronized(cache) {
                if (cache.size >= 4) cache.clear()      // tiny cache: a few maps is all a session needs
                cache[ortho.key] = b
            }
            return b
        }

        private fun traceInternal(ortho: OrthoSource): QgisBoundary {
            ZipFile(ortho.file).use { zip ->
                // Group tile (x, y) positions per zoom level, restricted to this map's folder prefix.
                val perZoom = HashMap<Int, MutableList<Pair<Int, Int>>>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    val m = ENTRY.matchEntire(e.name) ?: continue
                    if (m.groupValues[1] != ortho.prefix) continue
                    perZoom.getOrPut(m.groupValues[2].toInt()) { mutableListOf() }
                        .add(m.groupValues[3].toInt() to m.groupValues[4].toInt())
                }
                // Sharpest zoom whose tile count is workable. All zoom levels show the same footprint,
                // so a coarser zoom simply gives a chunkier (but cheaper) outline.
                val z = (ortho.maxZoom downTo ortho.minZoom).firstOrNull { (perZoom[it]?.size ?: 0) in 1..MAX_TILES }
                    ?: return rect(ortho)
                val tiles = perZoom.getValue(z)

                // Classify small squares of each tile (~SUBCELL_PX pixels) as imagery or empty, so
                // the boundary follows the orthomosaic's real pixel edge instead of the tile grid.
                // Coordinates are in "sub-cells": tile * CELLS + cell, so vertices stay integers and
                // vKey can encode them; smoothAndConvert divides by CELLS before converting to lat/lon.
                var px = IntArray(256 * 256)
                val opaque = HashSet<Long>()
                var cells = 0                             // sub-cells per tile side (16 for a 256px tile)
                for ((x, y) in tiles) {
                    val entry = zip.getEntry(ortho.entryName(z, x, y)) ?: continue
                    val bmp = zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) } ?: continue
                    val w = bmp.width; val h = bmp.height
                    if (px.size < w * h) px = IntArray(w * h)
                    bmp.getPixels(px, 0, w, 0, 0, w, h)
                    bmp.recycle()
                    cells = w / SUBCELL_PX
                    if (cells == 0) continue
                    for (cy in 0 until cells) {
                        for (cx in 0 until cells) {
                            var opq = 0; var tot = 0
                            var py = cy * SUBCELL_PX
                            while (py < (cy + 1) * SUBCELL_PX) {
                                var pxi = py * w + cx * SUBCELL_PX
                                for (sx in 0 until SUBCELL_PX step 4) {   // sample every 4th pixel
                                    tot++
                                    if ((px[pxi] ushr 24) >= OPAQUE_ALPHA) opq++
                                    pxi += 4
                                }
                                py += 4                                    // sample every 4th row
                            }
                            if (tot > 0 && opq.toDouble() / tot >= SUBCELL_OPAQUE_FRACTION) {
                                opaque.add(keyOf(x * cells + cx, y * cells + cy))
                            }
                        }
                    }
                }
                if (opaque.isEmpty()) return rect(ortho)

                // Segments/loops come out in sub-cell units; scale to tile-corner units (divide by
                // CELLS) so the existing cornerLon/cornerLat conversion applies unchanged.
                val loops = chainLoops(boundarySegments(opaque))
                    .map { smoothAndConvert(it, z, cells) }
                    .filter { it.size >= 3 }
                if (loops.isEmpty()) return rect(ortho)
                return QgisBoundary(loops, fromImagery = true)
            }
        }

        /** Bounding-rectangle fallback: the old behaviour, used when tracing is impossible. */
        private fun rect(ortho: OrthoSource) = QgisBoundary(
            listOf(listOf(
                BoundaryVertex(ortho.north, ortho.west),
                BoundaryVertex(ortho.north, ortho.east),
                BoundaryVertex(ortho.south, ortho.east),
                BoundaryVertex(ortho.south, ortho.west)
            )),
            fromImagery = false
        )

        /**
         * Boundary edge segments between imagery and empty cells, in cell-corner coordinates
         * (corner (cx, cy) = top-left of cell (cx, cy); y grows southwards; units are sub-cells,
         * i.e. tile * CELLS + cell when called from traceInternal). Each segment is directed so
         * that walking them in sequence traces each region clockwise.
         */
        private fun boundarySegments(opaque: Set<Long>): List<Seg> {
            val segs = ArrayList<Seg>()
            for (key in opaque) {
                val x = (key shr 32).toInt()
                val y = (key and 0xFFFFFFFFL).toInt()
                if (keyOf(x, y - 1) !in opaque) segs.add(Seg(x.toDouble(), y.toDouble(), x + 1.0, y.toDouble()))
                if (keyOf(x + 1, y) !in opaque) segs.add(Seg(x + 1.0, y.toDouble(), x + 1.0, y + 1.0))
                if (keyOf(x, y + 1) !in opaque) segs.add(Seg(x + 1.0, y + 1.0, x.toDouble(), y + 1.0))
                if (keyOf(x - 1, y) !in opaque) segs.add(Seg(x.toDouble(), y + 1.0, x.toDouble(), y.toDouble()))
            }
            return segs
        }

        /** Chains directed segments into closed loops (drops any open leftovers). */
        private fun chainLoops(segs: List<Seg>): List<List<Seg>> {
            val byStart = HashMap<Long, MutableList<Seg>>()
            for (s in segs) byStart.getOrPut(vKey(s.x1, s.y1)) { mutableListOf() }.add(s)
            val loops = ArrayList<List<Seg>>()
            while (true) {
                val first = byStart.values.firstOrNull()?.firstOrNull() ?: break
                removeFrom(byStart, first)
                val loop = ArrayList<Seg>()
                loop.add(first)
                var cur = first
                while (vKey(cur.x2, cur.y2) != vKey(first.x1, first.y1)) {
                    val next = byStart[vKey(cur.x2, cur.y2)]?.firstOrNull() ?: break
                    removeFrom(byStart, next)
                    loop.add(next)
                    cur = next
                }
                if (vKey(cur.x2, cur.y2) == vKey(first.x1, first.y1) && loop.size >= 3) loops.add(loop)
            }
            return loops
        }

        private fun removeFrom(byStart: HashMap<Long, MutableList<Seg>>, s: Seg) {
            byStart[vKey(s.x1, s.y1)]?.remove(s)
        }

        /** Encodes an integer tile-corner vertex into one Long key. */
        private fun vKey(x: Double, y: Double): Long =
            (x.roundToInt().toLong() shl 32) or (y.roundToInt().toLong() and 0xFFFFFFFFL)

        private fun keyOf(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

        /**
         * Rounds a staircase loop with two passes of Chaikin corner-cutting, then converts to
         * lat/lon. Loop coordinates are in cell units where a tile spans [cells] cells (1 for a
         * whole-tile trace); converting to tile-corner units first keeps the cornerLon/cornerLat
         * math shared by both granularities.
         */
        private fun smoothAndConvert(loop: List<Seg>, z: Int, cells: Int): List<BoundaryVertex> {
            val scale = 1.0 / cells
            val pts = loop.map { it.x2 * scale to it.y2 * scale }   // cyclic tile-corner polygon
            var cur = pts
            repeat(2) {
                val next = ArrayList<Pair<Double, Double>>(cur.size * 2)
                for (i in cur.indices) {
                    val a = cur[i]; val b = cur[(i + 1) % cur.size]
                    next.add(a.first * 0.75 + b.first * 0.25 to a.second * 0.75 + b.second * 0.25)
                    next.add(a.first * 0.25 + b.first * 0.75 to a.second * 0.25 + b.second * 0.75)
                }
                cur = next
            }
            return cur.map { (x, y) -> BoundaryVertex(cornerLat(y, z), cornerLon(x, z)) }
        }

        /** Continuous tile-corner -> lon (x may be fractional after smoothing). */
        private fun cornerLon(x: Double, z: Int): Double = x / (1 shl z) * 360.0 - 180.0

        /** Continuous tile-corner -> lat (Web Mercator inverse). */
        private fun cornerLat(y: Double, z: Int): Double =
            Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y / (1 shl z)))))
    }

    private class Seg(val x1: Double, val y1: Double, val x2: Double, val y2: Double)
}
