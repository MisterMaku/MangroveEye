# MangroveEye Project Memory

## Project Overview
Android Kotlin app for drone-based mangrove health assessment using TensorFlow Lite EfficientNetB0 model.
- Location: `C:\Users\Denmark Mabayo\AndroidStudioProjects\Mangrove`
- Primary purpose: Classify uploaded DJI Mini 4K photographs into Healthy/Unhealthy/Non-mangrove mangrove areas
- Displays results on a QGIS orthomosaic with GPS-marked assessment sites

## Key Files

### Core Data Models
- `data/Models.kt`: `Assessment`, `PickedPhoto`, `TileResult`, `PhotoGeo`, `TileInfo`, `Thumbs`
- `data/Gis.kt`: `GisCell`, `GisResult`, `GisBuilder` (fixed grid health map)
- `data/TileClassifier.kt`: TensorFlow Lite EfficientNetB0 integration (224×224 pixel input)
- `data/ExifGps.kt`: EXIF GPS reading and FlightInfo extraction
- `data/Config.kt`: Constants (TILE_SIZE=512, READ_SIZE=256, MODEL_INPUT=224, GIS_CELL_M=9.295)

### UI Components
- `ui/Screens.kt`: All screens including map with GIS layers
- `ui/OsmMap.kt`: OSMDroid map with base map, orthomosaic overlay, pins, GIS tiles
- `ui/GisOverlay.kt`: Draws GIS cells on map
- `ui/OrthoOverlay.kt`: QGIS orthomosaic XYZ tiles

### ViewModel
- `data/AppViewModel.kt`: Orchestrates upload, analysis, and GIS building

## Current Architecture Issues

### Issue 1: Two Different GIS Layers
- **Layer A (GisBuilder grid)**: Fixed-size grid cells (~86.4 m² each), created by pooling AI tiles from multiple photos that fall inside each cell. Used for "GIS" chip on map.
- **Layer B (Assessment tiles)**: Individual 224×224 pixel tiles from each photo, with `PhotoGeo` calculated from EXIF. Shown in "View tiles" dialog but NOT on map.

**Problem**: User thinks they're seeing individual photo tiles on the map, but they're actually seeing the fixed grid cells. This causes "spread" of predictions.

### Issue 2: Unreliable Georeferencing
- `PhotoGeo` calculates tile size and location using:
  - EXIF `flight.heightM` and `flight.yawDeg` to derive ground sample distance
  - Assumes photos are north-up, centered at photo's GPS lat/lon
  - tile_m = diagonal_px * diagonal_m / hypot(total_px)
- **Critical limitation**: EXIF GPS gives CAMERA position, not PHOTO FOOTPRINT
- Without camera calibration, GSD, or orthomosaic matching, the computed tile positions are approximations

## What Needs to Be Done

### Priority 1: Rebuild GIS with Individual Tile Polygons
- Create new `TileGisBuilder` that creates polygons for each 224×224 tile instead of fixed grid cells
- Preserve existing `GisBuilder` for backward compatibility (switch off by default)
- Export GeoJSON with individual tile polygons (already partially implemented in `GisResult.toGeoJson()`)

### Priority 2: Add Download GeoJSON Button
- Already exists at line 364 in `Screens.kt`! Shows "Download GeoJSON" button
- Uses Storage Access Framework: `ActivityResultContracts.CreateDocument("application/geo+json")`
- Exports current GIS layer cells (need to change to export individual tiles)

### Priority 3: Calculate Valid Polygon Areas
- For each tile with valid PhotoGeo:
  - Calculate area = tileM² from existing formula
  - For verification: could use Google Maps Distance Matrix or area-calculating library
- For tiles without valid PhotoGeo: export NaN or indicate invalid

### Priority 4: Display Individual Tile Footprints
- Modify `OsmMap` to draw tile polygons when "Individual Tiles" chip is selected
- Draw each 224×224 tile as polygon using `PhotoGeo` coordinates
- Show photo footprint as outline when available

## Missing Information
- No camera calibration data (focal length, sensor size, principal point)
- No ground sample distance from ground control points
- No orthomosaic image matching to verify tile positions
- EXIF only provides camera pose, not image-to-ground transformation matrix

## Constraints
- Preserve existing assessment data structure
- Keep same TensorFlow Lite model
- Maintain EXIF GPS reading
- Don't remove QGIS orthomosaic integration