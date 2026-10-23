package com.example.mangrove.data

object Config {
    const val SITE_NAME = "Mangrove Sanctuary, Davao City"
    // Name your app sends to the map servers (use your own app name + email).
    const val USER_AGENT = "MangroveHealthApp/1.0 (denmark.mabayo@hcdc.edu.ph)"
    // Base map shown under your QGIS map. The user can switch it on the Map screen.
    val DEFAULT_BASE = BaseMap.SATELLITE

    // Optional: free key from cloud.maptiler.com. Leave empty to hide the MapTiler option.
    const val MAPTILER_KEY = ""

    // Built-in QGIS map: XYZ tiles zipped in app/src/main/assets.
    // Users can load their own QGIS map with the small "QGIS layer" button on the Map screen.
    const val ORTHO_ZIP = "QGIS-Maps.zip"

    // How many zoom levels you can zoom IN past your sharpest QGIS tiles. The image is enlarged
    // (looks softer), but you can get closer. Use 0 to stop at the sharpest tiles.
    const val EXTRA_ZOOM = 1
    // The start view and the recenter button fit your whole QGIS map in the map box, and the user
    // cannot zoom out past that view (so there is nothing to type here).

    // AI tiling: each photo is cut into 512 x 512 px tiles (the tile map and the preview use these tiles).
    const val TILE_SIZE = 512
    // The model reads each tile in smaller pieces (512 / 256 = 2 x 2 pieces per tile). This model is accurate at
    // about 256 px: a whole 512 px tile gets shrunk too much and healthy canopy was read as Unhealthy.
    // The Healthy % is counted from these pieces; the tile map shows each tile by the majority of its pieces
    // (a tie counts as Healthy). Set READ_SIZE = TILE_SIZE to read whole tiles (only for a model trained on 512 px tiles).
    const val READ_SIZE = 256
    const val MODEL_INPUT = 224          // must match the AI model input
    const val MAX_IMAGE_SIDE = 4096      // bigger photos are shrunk to this before tiling (saves memory)

    // The app follows the model: write its 3 class names in the SAME ORDER as the model's outputs
    // (the class_names list printed in your training notebook, e.g. next to the confusion matrix).
    // Use the words "Healthy", "Non-mangrove" and "Unhealthy". Non-mangrove tiles (water, mud, land) are ignored.
    val CLASS_NAMES = listOf("Healthy", "Non-mangrove", "Unhealthy")
    val CLASS_HEALTHY = CLASS_NAMES.indexOf("Healthy")
    val CLASS_UNHEALTHY = CLASS_NAMES.indexOf("Unhealthy")
    const val INPUT_SCALE = 1f           // 1f = raw 0-255 pixels (EfficientNet style); use 1f / 255f if your model wants 0-1
    // A photo is Healthy when at least this % of its tiles are Healthy, otherwise Unhealthy
    const val HEALTHY_MIN = 60
    // A result within this many % of HEALTHY_MIN is marked "Uncertain" (60 +/- 10 = 50-70%).
    const val UNCERTAIN_BAND = 10
}