package com.example.mangrove.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Keeps the assessment log on the phone as a small JSON file. */
class AssessmentStore(ctx: Context) {
    private val file = File(ctx.filesDir, "assessments.json")

    fun load(): List<Assessment> = try {
        val a = JSONArray(file.readText())
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Assessment(o.getString("id"), o.getString("name"), o.getLong("t"),
                o.getDouble("lat"), o.getDouble("lon"),
                healthyOf(o), unhealthyOf(o), o.optString("k", ""),
                o.optInt("n", 0), o.optString("g", ""), o.optString("th", ""), o.optBoolean("o", false),
                o.optString("tl", ""), o.optString("geo", ""))
        }
    } catch (e: Exception) { emptyList() }

    fun save(list: List<Assessment>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.id).put("name", it.name).put("t", it.timeMillis)
                .put("lat", it.lat).put("lon", it.lon)
                .put("h", it.healthy).put("u", it.unhealthy).put("k", it.photoKey)
                .put("n", it.nonMangrove).put("g", it.grid).put("th", it.thumb).put("o", it.outOfReach)
                .put("tl", it.tiles).put("geo", it.geo))
        }
        file.writeText(a.toString())
    }

    // Older saved entries: 2-class ("pct") and 3-class ("h", "m", "c") are converted to Healthy / Unhealthy.
    private fun healthyOf(o: JSONObject) = if (o.has("h")) o.getInt("h") else o.getInt("pct")
    private fun unhealthyOf(o: JSONObject) = when {
        o.has("u") -> o.getInt("u")
        o.has("m") -> o.getInt("m") + o.getInt("c")
        else -> 100 - o.getInt("pct")
    }
}