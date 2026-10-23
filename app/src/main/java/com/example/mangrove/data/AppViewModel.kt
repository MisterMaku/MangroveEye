package com.example.mangrove

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mangrove.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = AssessmentStore(app)
    private val classifier = TileClassifier(app)

    var assessments by mutableStateOf(store.load()); private set
    var photos by mutableStateOf<List<PickedPhoto>>(emptyList()); private set
    var siteName by mutableStateOf("")
    var progress by mutableStateOf(0f); private set
    var photoIndex by mutableIntStateOf(0); private set
    var photoTotal by mutableIntStateOf(0); private set
    var message by mutableStateOf<String?>(null); private set
    var selectedId by mutableStateOf<String?>(null)
    var filter by mutableStateOf<Status?>(null)          // null = show All on the Map and in the log
    var baseMap by mutableStateOf(Config.DEFAULT_BASE)
    var ortho by mutableStateOf(OrthoTiles.load(app)); private set
    var hasCustomMap by mutableStateOf(OrthoTiles.hasCustom(app)); private set     // user added a QGIS map

    // ---------- GIS health map (built from the photos you uploaded) ----------
    // Rebuilt by itself whenever the assessment log changes. No extra AI run is needed:
    // the tiles of every analyzed photo are placed on the map at their GPS.
    val gis: GisResult? by derivedStateOf { GisBuilder.build(assessments) }
    var showGis by mutableStateOf(false)                                  // GIS tiles shown on the Map screen
    var showPins by mutableStateOf(true)                                  // pins shown on the Map screen (Pins chip)
    var gisVisible by mutableStateOf(GisClass.values().toSet())          // layers ticked in the legend

    fun toggleGisClass(c: GisClass) { gisVisible = if (c in gisVisible) gisVisible - c else gisVisible + c }

    // ---------- upload: single or batch ----------
    /** One box for everything: pick one photo or many at once. Picking again adds more. */
    fun pickPhotos(uris: List<Uri>) = addPhotos(uris)

    /** GPS is read from each photo's EXIF right away, before any tiling, and stays with the photo. */
    private fun addPhotos(uris: List<Uri>) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val picked = withContext(Dispatchers.IO) {
                uris.map { u ->
                    val g = ExifGps.read(ctx, u)
                    PickedPhoto(u, ExifGps.displayName(ctx, u),
                        g?.let { fmt(it.first) } ?: "", g?.let { fmt(it.second) } ?: "", g != null,
                        ExifGps.fileKey(ctx, u))
                }
            }
            // Skip photos already in the list or already in the assessment log.
            val base = photos
            val taken = base.map { it.key }.toMutableSet()
            var dup = 0
            val fresh = picked.filter { p ->
                val seen = !taken.add(p.key) || assessments.any { sameShot(p, it) }
                if (seen) dup++
                !seen
            }
            photos = base + fresh
            message = ((if (photos.isEmpty()) "" else summary()) +
                    (if (dup > 0) " $dup photo(s) skipped: already added or analyzed." else "")).trim()
        }
    }

    /**
     * True if this photo is already in the log: same file name + size, or, for older log entries
     * saved before photo keys existed, the same EXIF GPS point.
     */
    private fun sameShot(p: PickedPhoto, a: Assessment): Boolean {
        if (a.photoKey.isNotEmpty()) return a.photoKey == p.key
        val ll = p.latLon() ?: return false
        return p.fromExif && Math.abs(ll.first - a.lat) < 0.000002 && Math.abs(ll.second - a.lon) < 0.000002
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.6f", v)

    /** True if the photo's GPS has no drone imagery in the QGIS map (outside it, or on its empty / transparent part). */
    fun isOutside(p: PickedPhoto): Boolean =
        p.latLon()?.let { (la, lo) -> ortho?.hasImagery(la, lo) == false } == true

    private fun summary(): String {
        val missing = photos.count { !it.fromExif }
        val outside = photos.count { isOutside(it) }
        return buildString {
            append("${photos.size} photo(s). GPS read from ${photos.size - missing}.")
            if (missing > 0) append(" $missing without GPS (they will be skipped).")
            if (outside > 0) append(" $outside outside your QGIS map: marked Invalid (no AI, no pin).")
        }
    }

    fun removePhoto(index: Int) {
        photos = photos.filterIndexed { i, _ -> i != index }
        message = if (photos.isEmpty()) null else summary()
    }

    fun clearPhotos() { photos = emptyList(); message = null }

    fun clearForm() { photos = emptyList(); siteName = ""; message = null }

    // ---------- log ----------
    fun rename(id: String, name: String) {
        assessments = assessments.map { if (it.id == id) it.copy(name = name) else it }
        store.save(assessments)
    }

    /** Removes every assessment (and its preview photos). Old entries without a photo key go this way too. */
    fun clearLog() {
        Thumbs.deleteAll(getApplication())
        assessments = emptyList()
        selectedId = null
        store.save(assessments)
    }

    fun delete(id: String) {
        assessments.firstOrNull { it.id == id }?.let { Thumbs.delete(getApplication(), it.thumb) }
        assessments = assessments.filter { it.id != id }
        if (selectedId == id) selectedId = null
        store.save(assessments)
    }

    private fun nextSiteNumber() =
        (assessments.mapNotNull { it.name.removePrefix("Site ").toIntOrNull() }.maxOrNull() ?: 0) + 1

    // ---------- your own QGIS map ----------
    fun importMap(uri: Uri, onDone: (OrthoSource?) -> Unit) {
        viewModelScope.launch {
            val src = withContext(Dispatchers.IO) { OrthoTiles.import(getApplication(), uri) }
            if (src != null) { ortho = src; hasCustomMap = true }
            onDone(src)
        }
    }

    fun useBuiltInMap() { ortho = OrthoTiles.useBuiltIn(getApplication()) }

    /** Switch back to the QGIS map the user added earlier. */
    fun useMyMap() { ortho = OrthoTiles.useCustom(getApplication()) }

    /** Delete the QGIS map the user added; the built-in map is shown again. Assessments are not touched. */
    fun removeMyMap() {
        ortho = OrthoTiles.removeCustom(getApplication())
        hasCustomMap = false
    }

    /**
     * For each photo: if its GPS is inside the QGIS map, tile (512 px) -> AI Healthy/Unhealthy; if it is outside,
     * the AI is skipped and the photo is saved as "Invalid". The result is saved together with the
     * GPS that was read from that photo at the start. Returns false if it could not start.
     */
    fun analyze(onDone: () -> Unit, onFail: () -> Unit): Boolean {
        val unique = photos.filter { p -> assessments.none { sameShot(p, it) } }   // never log the same photo twice
        if (unique.isEmpty()) {
            message = if (photos.isEmpty()) "Choose at least one photo." else "These photos are already in the assessment log."
            return false
        }
        // Photos without GPS cannot be placed on the map: they are skipped (the list already says "No GPS").
        val list = unique.filter { it.latLon() != null }
        val noGps = unique.size - list.size
        if (list.isEmpty()) {
            message = "No GPS in the photo(s), so they can't be placed on the map. Choose photos that have GPS."
            return false
        }
        photoTotal = list.size
        photoIndex = 0
        progress = 0f
        viewModelScope.launch {
            var failed = 0
            var outCount = 0
            var modelError: String? = null
            var lastId: String? = null
            list.forEachIndexed { i, p ->
                photoIndex = i + 1
                val (la, lo) = p.latLon()!!
                val id = UUID.randomUUID().toString()
                val thumbFile = Thumbs.file(getApplication(), "$id.jpg")
                val outside = isOutside(p)
                val r: TileResult? = if (outside) {
                    // GPS is outside the orthomosaic: NO AI prediction, only a preview photo.
                    classifier.makeThumb(p.uri, thumbFile)
                    progress = (i + 1f) / list.size
                    TileResult(0, 0, 0, "")
                } else try {
                    classifier.classify(p.uri, thumbFile) { progress = (i + it) / list.size }
                } catch (e: ModelLoadException) { modelError = e.message; null
                } catch (e: Exception) { null }
                if (r == null) { failed++; return@forEachIndexed }
                if (outside) outCount++
                val base = siteName.trim()
                val name = when {
                    base.isEmpty() -> "Site ${nextSiteNumber()}"
                    list.size == 1 -> base
                    else -> "$base ${i + 1}"
                }
                val a = Assessment(id, name, System.currentTimeMillis(), la, lo, r.healthy, r.unhealthy, p.key,
                    r.nonMangrove, r.grid, if (thumbFile.exists()) thumbFile.name else "", outside, r.tiles, r.geo)
                assessments = listOf(a) + assessments
                lastId = a.id
            }
            store.save(assessments)
            if (lastId == null) {
                message = when {
                    modelError != null -> "The AI model could not load ($modelError). Check Step 2b and Step 4."
                    else -> "Could not read the photo(s). Try choosing them again."
                }
                onFail()
            } else {
                selectedId = lastId
                clearForm()
                val notes = listOfNotNull(
                    if (failed > 0) "$failed photo(s) skipped (could not be read)." else null,
                    if (noGps > 0) "$noGps photo(s) skipped: no GPS in the photo." else null,
                    if (outCount > 0) "$outCount photo(s) outside the QGIS map: marked Invalid (no AI prediction, not shown on the map)." else null
                )
                if (notes.isNotEmpty()) message = notes.joinToString(" ")
                onDone()
            }
        }
        return true
    }
}