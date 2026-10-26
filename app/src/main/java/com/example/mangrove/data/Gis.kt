package com.example.mangrove.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

/** Colours: green = healthy, orange = unhealthy, grey = not mangrove. */
enum class GisClass(val label: String, val color: Int) {
    HEALTHY("Healthy", 0xFF6DBE45.toInt()),
    UNHEALTHY("Unhealthy", 0xFFF28C28.toInt()),
    NON_MANGROVE("Non-mangrove", 0xFFB9C2C9.toInt())
}

/** One square of the GIS layer = one fixed grid cell: its corners, its class and its area on the ground. */
data class GisCell(
    val north: Double, val south: Double, val west: Double, val east: Double,
    val cls: GisClass, val healthyPct: Int, val areaM2: Double,
    val h: Int, val u: Int, val other: Int,           // AI pieces found in this cell: healthy, unhealthy, non-mangrove
    val source: String = ""                           // photo(s) this cell was estimated from
) {
    private val pieces: Int get() = (h + u + other).coerceAtLeast(1)
    val centerLat: Double get() = (north + south) / 2
    val centerLon: Double get() = (west + east) / 2

    /** Polygon coordinates for GeoJSON: [[lon, lat], ...] */
    val polygon: List<Pair<Double, Double>> get() = listOf(
        west to north, east to north, east to south, west to south, west to north
    )

    /** Share of THIS cell that is Healthy / Unhealthy / Non-mangrove, in %. */
    val healthyOfTile: Int get() = (h * 100.0 / pieces).roundToInt()
    val unhealthyOfTile: Int get() = (u * 100.0 / pieces).roundToInt()
    val nonMangroveOfTile: Int get() = (100 - healthyOfTile - unhealthyOfTile).coerceAtLeast(0)
    /** Ground area of the Healthy / Unhealthy / Non-mangrove part of this cell in m². */
    val healthyM2: Double get() = areaM2 * h / pieces
    val unhealthyM2: Double get() = areaM2 * u / pieces
    val nonMangroveM2: Double get() = areaM2 * other / pieces
}

data class GisResult(val photos: Int, val cells: List<GisCell>) {
    val totalM2: Double = cells.sumOf { it.areaM2 }
    fun areaOf(c: GisClass): Double = cells.filter { it.cls == c }.sumOf { it.areaM2 }
    val mangroveM2: Double get() = totalM2 - areaOf(GisClass.NON_MANGROVE)

    /** Export to GeoJSON */
    fun toGeoJson(): String {
        val features = JSONArray()
        cells.forEach { cell ->
            val feature = JSONObject()
            feature.put("type", "Feature")
            val geometry = JSONObject()
            geometry.put("type", "Polygon")
            val coords = JSONArray()
            val ring = JSONArray()
            cell.polygon.forEach { (lon, lat) -> ring.put(JSONArray().put(lon).put(lat)) }
            coords.put(ring)
            geometry.put("coordinates", coords)
            feature.put("geometry", geometry)
            val props = JSONObject()
            props.put("class", cell.cls.label)
            props.put("healthy_pct", cell.healthyOfTile)
            props.put("area_m2", cell.areaM2)
            props.put("source", cell.source)
            feature.put("properties", props)
            features.put(feature)
        }
        val fc = JSONObject()
        fc.put("type", "FeatureCollection")
        fc.put("features", features)
        return fc.toString(2)
    }
}


/**
 * Builds the GIS layer from the photos uploaded and analyzed in the app (the assessment log).
 *
 * The map is a FIXED grid of square cells (Config.GIS_CELL_M, about 86.4 m² each). It is NOT the small per-photo
 * tiles of the "View tiles" dialog. For every grid cell the app pools the small AI tiles of the uploaded
 * photos that fall inside it, and estimates the whole cell from them:
 *   - Non-mangrove (grey):  more than NON_MANGROVE_MIN % of the cell is water, mud or land
 *   - Healthy (green):      otherwise, Healthy >= Unhealthy (a tie counts as Healthy)
 *   - Unhealthy (orange):   otherwise
 * Where two photos overlap, each patch of ground is counted once (the sharper photo wins), so nothing is doubled.
 * Cells that the photos barely touch (less than Config.GIS_MIN_COVER) are not drawn. Nothing is analyzed again.
 */
object GisBuilder {
    private const val SUB = 5                     // each cell is split 5 x 5 to avoid counting overlapping photos twice

    private class P(val a: Assessment, val geo: PhotoGeo)
    private class Acc {
        var h = 0; var u = 0; var o = 0
        var area = 0.0
        val photos = LinkedHashSet<String>()
    }

    fun build(list: List<Assessment>): GisResult? {
        val side = Config.GIS_CELL_M
        val dLat = side / 111_320.0
        val dLon = side / (111_320.0 * cos(Math.toRadians(Config.GIS_REF_LAT)))

        // sharpest photos (smallest tiles) first: they win where photos overlap
        val photoList = list.filter { !it.outOfReach && it.grid.isNotEmpty() }
            .mapNotNull { a -> PhotoGeo.parse(a.geo)?.let { P(a, it) } }
            .sortedBy { it.geo.tileM }

        val claimed = HashMap<Pair<Long, Long>, Int>()           // small square of ground -> photo that owns it
        val acc = LinkedHashMap<Pair<Int, Int>, Acc>()           // (row, col) of the fixed grid -> pooled AI pieces
        val used = HashSet<Int>()

        photoList.forEachIndexed { idx, p ->
            val a = p.a
            val pieces = p.geo.pieces.coerceAtLeast(1)
            a.grid.split("/").forEachIndexed { r, line ->
                for (c in line.indices) {
                    val t = a.tileInfo(r, c) ?: continue
                    val sub = Pair(floor(t.lat / (dLat / SUB)).toLong(), floor(t.lon / (dLon / SUB)).toLong())
                    if (claimed.getOrPut(sub) { idx } != idx) continue      // another photo already covers this ground
                    val key = Pair(floor(t.lat / dLat).toInt(), floor(t.lon / dLon).toInt())
                    val h = (t.healthyPct * pieces / 100.0).roundToInt()
                    val u = (t.unhealthyPct * pieces / 100.0).roundToInt()
                    val v = acc.getOrPut(key) { Acc() }
                    v.h += h; v.u += u; v.o += (pieces - h - u).coerceAtLeast(0)
                    v.area += t.areaM2
                    v.photos.add(a.name)
                    used.add(idx)
                }
            }
        }

        val cells = ArrayList<GisCell>()
        for ((k, v) in acc) {
            if (v.area / (side * side) < Config.GIS_MIN_COVER) continue   // photos barely touch this cell
            val total = (v.h + v.u + v.o).coerceAtLeast(1)
            val nonPct = v.o * 100.0 / total
            val cls = when {
                nonPct > Config.NON_MANGROVE_MIN -> GisClass.NON_MANGROVE
                v.h >= v.u -> GisClass.HEALTHY            // a tie counts as Healthy
                else -> GisClass.UNHEALTHY
            }
            val hp = if (v.h + v.u == 0) 0 else (v.h * 100.0 / (v.h + v.u)).roundToInt()
            val s = k.first * dLat
            val w = k.second * dLon
            val src = v.photos.first() + if (v.photos.size > 1) " +${v.photos.size - 1}" else ""
            cells.add(GisCell(s + dLat, s, w, w + dLon, cls, hp, side * side, v.h, v.u, v.o, src))
        }
        return if (cells.isEmpty()) null else GisResult(used.size, cells)
    }
}