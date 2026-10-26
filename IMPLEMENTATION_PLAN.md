# Implementation Plan: MangroveEye GIS Fix

## Overview
Replace the fixed grid GIS layer with individual image tile polygons, add reliable GeoJSON export, and calculate valid polygon areas.

---

## Phase 1: Create Tile-based GIS Layer

### File: `data/TileGisBuilder.kt`
Create new builder that uses individual assessment tiles instead of fixed grid:

```kotlin
object TileGisBuilder {
    // Builds List<GeoJsonFeature> for each tile from assessments
    // Uses PhotoGeo for tile boundaries
    // Retains tileInfo() calculations from Models.kt
}
```

**Key features**:
- Parse PhotoGeo from assessment.geo
- For each assessment, iterate grid and create Polygon for each tile
- Calculate tile side length in meters from PhotoGeo.tileM
- Convert tile coordinates to LatLng bounds
- Pool nearby tiles with same classification (optional aggregation)

---

## Phase 2: Update Assessment Model

### File: `data/Models.kt`
Enhance `Assessment` class to support tile-level export:

```kotlin
data class Assessment(
    // existing fields...
    val rawTiles: List<TileDetail> = emptyList()  // NEW: raw tile data for export
)

data class TileDetail(
    val row: Int, val col: Int,
    val healthyPct: Int, val unhealthyPct: Int,
    val centerLat: Double, val centerLon: Double,
    val north: Double, val south: Double, val west: Double, val east: Double,
    val areaM2: Double
)
```

Modify `tileInfo()` to return complete polygon coordinates, not just center.

---

## Phase 3: Update TileClassifier

### File: `data/TileClassifier.kt`
Add `classifyWithGeo()` that returns `TileResultWithGeo`:

```kotlin
data class TileResultWithGeo(
    val healthy: Int, val unhealthy: Int, val nonMangrove: Int,
    val grid: String, val tiles: String,
    val tilesGeo: List<TileGeo>
)

data class TileGeo(
    val row: Int, val col: Int,
    val tileM: Double,
    val centerLat: Double, val centerLon: Double,
    val north: Double, val south: Double, val west: Double, val east: Double
)
```

Keep existing `classify()` for backward compatibility.

---

## Phase 4: Update AppViewModel

### File: `data/AppViewModel.kt`
Update `analyze()` to use `classifyWithGeo()`:

```kotlin
// Replace existing analyze() tile result collection logic
// Build List<TileGeo> from classifier output
// Store in assessment.rawTiles
```

Add new derived state:
```kotlin
val tileGis: TileGisResult? by derivedStateOf { GisBuilder.build(tileGisBuilder.build(assessments)) }
```

---

## Phase 5: Update UI for Map Display

### File: `ui/OsmMap.kt`
Add new overlay for individual tiles:

```kotlin
private class TileGisOverlay() : Overlay() {
    var tiles: List<TileDetail> = emptyList()
    visible: Set<TileType>
    // Draw each tile as polygon
}

// Add to MapState
val tileGis = TileGisOverlay()
```

Update `OsmMap()` composable:
- Accept `tileGisTiles` parameter
- Draw tile polygons when `showIndividualTiles` is true

### File: `ui/Screens.kt`
Add "Individual Tiles" chip in Filter section:
```kotlin
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    FilterChip(selected = vm.showGis, onClick = { ... }, label = { Text("GIS") })
    FilterChip(selected = vm.showPins, onClick = { ... }, label = { Text("Pins") })
    FilterChip(selected = vm.showIndividualTiles, onClick = { ... }, label = { Text("Tile Polygons") })
}
```

---

## Phase 6: Update GeoJSON Export

### File: `data/Models.kt`
Add method to `GisResult`:

```kotlin
fun toGeoJsonFeatures()

    // Returns List<GeoJsonFeature>
```

Update `toGeoJson()`:
```kotlin
fun toGeoJson(): String {
    val features = JSONArray()
    cells.forEach { cell -> ... }
    // ALSO add individual tile features
    tileFeatures.forEach { feature -> features.put(feature) }
    return fc.toString(2)
}
```

---

## Phase 7: Area Calculation

### File: `data/Models.kt`
Add area calculation utilities:
```kotlin
// Calculate ground area from LatLng polygon
fun calculatePolygonAreaM2(latlngs: List<Pair<Double, Double>>): Double

// For tile polygons
fun TileGeo.areaM2(): Double = (north - south) * (east - west)
```

After Polyline area calculation is verified, use it for validation.

---

## Phase 8: Build and Test

### Commands:
```bash
cd C:\Users\Denmark Mabayo\AndroidStudioProjects\Mangrove
.\gradlew.bat clean
.\gradlew.bat assembleDebug
```

### Test cases:
1. Upload photo, verify tile polygons appear within photo footprint
2. Verify yellow/green polygons don't extend beyond photo edges
3. Confirm GeoJSON export has Polygon features per tile, not grid cells
4. Verify area calculations match expected tile sizes
5. Test overlapping photos: polygons should be bounded to each photo individually

---

## Known Limitations

1. **EXIF-only georeferencing**: Without camera calibration or ground control points, tile positions are approximations using:
   - Flight altitude from EXIF (if available)
   - Focal length in 35mm equivalent
   - Image dimensions
   - This is reasonable for weighted sampling but not精度的地籍级 mapping

2. **Partial tiles**: Edge tiles that straddle image boundaries will be clipped. At present the app uses:
   - `rows = bmp.height / tile`
   - For partial edge tiles, the classifier runs on whatever is available
   - These tiles' polygons are truncated

3. **Oil on camera elements effect**: May slightly elongate Ground Sample Distance, but this is out of scope.

---

## Files Changed Summary

1. `data/Models.kt` - Add `TileDetail`, `TileGeo`, `calculatePolygonAreaM2()`, `Assessment.rawTiles`
2. `data/TileClassifier.kt` - Add `classifyWithGeo()` → `TileResultWithGeo`
3. `data/TileGisBuilder.kt` - New file: Build GIS from individual tiles
4. `data/AppViewModel.kt` - Use `classifyWithGeo()`, add derived tile GIS state
5. `ui/OsmMap.kt` - Add `TileGisOverlay`
6. `ui/Screens.kt` - Add "Individual Tiles" chip, update map display logic, fix GeoJSON export
7. `app/build.gradle.kts` - Add area calculation library (preonception or `com.github_UPDATED.area_calculator`)

---

## Implementation Order

1. Create `TileGisBuilder.kt` first (independent)
2. Add `TileGeo` data class to `Models.kt`
3. Add `tileInfo()` enhancement for polygon coordinates
4. Update `TileClassifier.kt` with new classify method
5. Update `AppViewModel.kt` to collect new data
6. Add `TileGisOverlay` to `OsmMap.kt`
7. Update `Screens.kt` UI components
8. Fix GeoJSON export
9. Build and verify

---

## Rollback Strategy

If issues arise:
1. Keep old `GisBuilder` code unchanged
2. Add `showIndividual Tiles` as toggle (default: false)
3. Rename new files with `_v2` suffix
4. Feature can be disabled without affecting existing assessments