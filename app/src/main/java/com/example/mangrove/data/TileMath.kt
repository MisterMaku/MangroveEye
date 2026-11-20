package com.example.mangrove.data

import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan
import kotlin.math.cos
import kotlin.math.sinh
import kotlin.math.atan
import kotlin.math.PI

/**
 * Utility object for converting between geographic coordinates (lat/lon) and tile indices (x/y)
 * at different zoom levels. Used for XYZ tile systems (Web Mercator/GPS).
 *
 * Based on the OpenStreetMap tile scheme: https://wiki.openstreetmap.org/wiki/Tile_scheme
 */
object TileMath {
    fun tileX(lon: Double, z: Int): Int = floor((lon + 180.0) / 360.0 * (1 shl z)).toInt()
    fun tileY(lat: Double, z: Int): Int {
        val r = Math.toRadians(lat)
        return floor((1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl z)).toInt()
    }
    fun tileLon(x: Int, z: Int): Double = x.toDouble() / (1 shl z) * 360.0 - 180.0
    fun tileLat(y: Int, z: Int): Double = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y / (1 shl z)))))
}