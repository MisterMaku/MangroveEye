package com.example.mangrove.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import com.example.mangrove.AppViewModel
import com.example.mangrove.data.*
import com.example.mangrove.theme.Mg
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

private fun Long.fmt(): String = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(this)

// ---------------- 1. SPLASH ----------------
@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(1800); onDone() }
    Column(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Mg.Mangrove, Mg.Forest))).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        DottedIcon(Icons.Outlined.Eco, 88.dp, tint = Mg.Background)
        Spacer(Modifier.height(18.dp))
        Text("MangroveEye", color = Mg.Background, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Drone Based Assessment of\nMangrove Forest Health in Davao City",
            color = Mg.MangroveSoft, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

// ---------------- 2. HOME ----------------
@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val last = vm.assessments.firstOrNull()
    Column(
        Modifier
            .fillMaxSize()
            .background(Mg.Background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Header
        Text(
            "MangroveEye",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Mg.Mangrove
        )
        Text(
            "Mangrove Health Assessment",
            fontSize = 15.sp,
            color = Mg.InkMuted,
            fontWeight = FontWeight.Normal
        )
        LocationLine(Mg.MangroveLight, Modifier.padding(vertical = 4.dp),
            text = vm.ortho?.info?.name?.ifEmpty { null }
                ?: if (vm.ortho?.custom == true) "Your QGIS map" else Config.SITE_NAME)

        // Last Assessment Card
        val lastCardColor = if (last != null) Mg.MangroveSoft.copy(alpha = 0.6f) else Mg.GreySoft.copy(alpha = 0.3f)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(lastCardColor)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "LAST ASSESSMENT",
                fontSize = 11.sp,
                letterSpacing = 1.sp,
                color = Mg.MangroveLight,
                fontWeight = FontWeight.Bold
            )
            Text(
                last?.let {
                    if (it.showsPercent) "${it.status.label} · ${it.healthy}% healthy" else it.status.label
                } ?: "No assessments yet",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Mg.Mangrove
            )
        }

        // Action Buttons
        MgButton(Icons.Outlined.Map, "Map", "Start assessment or view results", primary = true) {
            nav.navigate("map")
        }

        // Info Text
        Text(
            "Uploading assessments now happens in the Map section, right where the results are shown.",
            fontSize = 13.5.sp,
            color = Mg.InkMuted,
            lineHeight = 19.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ---------------- 3. UPLOAD ASSESSMENT (single or batch) ----------------
@Composable
fun UploadScreen(vm: AppViewModel, nav: NavController) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.pickPhotos(uris)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Mg.Background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Mg.CardBackground)
                    .clickable { vm.clearForm(); nav.popBackStack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    "Back",
                    tint = Mg.Ink,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                "Upload Assessment",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Mg.Ink
            )
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // Instructions
            Text(
                "Add one photo or a whole batch. GPS is automatically read from each image.",
                fontSize = 13.5.sp,
                color = Mg.InkMuted,
                lineHeight = 19.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            SectionLabel(Icons.Outlined.Image, "DRONE PHOTOS")
            Spacer(Modifier.height(8.dp))

            // Photo Selection Area
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Mg.CardBackground)
                    .clickable { picker.launch(arrayOf("image/jpeg", "image/*")) }
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (vm.photos.isEmpty()) {
                    DottedIcon(Icons.Outlined.PhotoLibrary, 64.dp)
                } else {
                    DottedIcon(Icons.Outlined.CheckCircle, 64.dp, tint = Mg.MangroveLight)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (vm.photos.isEmpty()) "Tap to add photos" else "${vm.photos.size} selected",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Mg.Mangrove,
                    textAlign = TextAlign.Center
                )
                Text(
                    "Pick one photo or many at once",
                    fontSize = 12.5.sp,
                    color = Mg.InkMuted,
                    textAlign = TextAlign.Center
                )
            }

            if (vm.photos.size > 1) {
                TextButton(
                    onClick = { vm.clearPhotos() },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Clear all photos",
                        color = Mg.Unhealthy,
                        fontSize = 13.sp
                    )
                }
            }

            vm.message?.let {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Mg.GreenSoft.copy(alpha = 0.3f))
                        .padding(12.dp)
                ) {
                    Text(it, fontSize = 13.sp, color = Mg.GreenText)
                }
            }

            // Photo List
            vm.photos.forEachIndexed { i, p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Mg.CardBackground)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Image,
                            null,
                            tint = Mg.Mangrove,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            p.name,
                            Modifier.weight(1f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Mg.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            { vm.removePhoto(i) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                "Remove",
                                tint = Mg.InkMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (p.fromExif) {
                        Text(
                            "GPS ${p.lat}, ${p.lon} (read from image)",
                            fontSize = 12.5.sp,
                            color = Mg.GreenText
                        )
                        if (vm.isOutside(p)) {
                            Text(
                                "Outside your QGIS map. Will be marked Invalid (unpredictable AI).",
                                fontSize = 12.5.sp,
                                color = Mg.AmberText,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    } else {
                        Text(
                            "No GPS in this image. Will be skipped.",
                            fontSize = 12.5.sp,
                            color = Mg.AmberText,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Site Name Field
            OutlinedTextField(
                value = vm.siteName,
                onValueChange = { vm.siteName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Site name (optional)", color = Mg.Mangrove) },
                placeholder = { Text("e.g. North channel", color = Mg.InkMuted.copy(alpha = 0.6f)) },
                supportingText = {
                    if (vm.photos.size > 1) {
                        Text("Batch photos: name 1, name 2, ...")
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Mg.MangroveLight,
                    unfocusedBorderColor = Mg.LightDivider,
                    focusedLabelColor = Mg.MangroveLight,
                    unfocusedLabelColor = Mg.InkMuted.copy(alpha = 0.7f)
                )
            )
        }

        Spacer(Modifier.height(12.dp))

        // Action Buttons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MgButton(
                null,
                "Cancel",
                modifier = Modifier.weight(1f)
            ) {
                vm.clearForm()
                nav.popBackStack()
            }
            val photosWithGps = vm.photos.count { it.latLon() != null }
            MgButton(
                null,
                if (photosWithGps > 1) "Analyze $photosWithGps" else "Analyze",
                primary = true,
                modifier = Modifier.weight(1f)
            ) {
                val started = vm.analyze(
                    onDone = { nav.navigate("map") { popUpTo("home") } },
                    onFail = { nav.popBackStack() }
                )
                if (started) nav.navigate("analyzing")
            }
        }
    }
}

// ---------------- 4. ANALYZING ----------------
@Composable
fun AnalyzingScreen(vm: AppViewModel) {
    val steps = listOf("Extract GPS from EXIF", "Split into 512 px tiles", "AI classifies tiles", "Match coordinates")
    val p = vm.progress

    Column(
        Modifier
            .fillMaxSize()
            .background(Mg.Background)
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(20.dp))

        // Progress Ring
        CircularProgressIndicator(
            progress = { p },
            modifier = Modifier.size(120.dp),
            color = Mg.MangroveLight,
            trackColor = Mg.MangroveSoft,
            strokeWidth = 8.dp,
            strokeCap = StrokeCap.Round
        )

        Spacer(Modifier.height(28.dp))

        // Title
        Text(
            if (vm.photoTotal > 1) "Analyzing photo ${vm.photoIndex} of ${vm.photoTotal}" else "Analyzing Image",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Mg.Mangrove
        )

        Spacer(Modifier.height(10.dp))

        // Subtitle
        Text(
            "Classifying mangrove health from your drone images.",
            fontSize = 14.sp,
            color = Mg.InkMuted,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(30.dp))

        // Progress Bar
        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = Mg.MangroveLight,
            trackColor = Mg.MangroveSoft,
            strokeCap = StrokeCap.Round
        )

        Spacer(Modifier.height(16.dp))

        // Percentage
        Text(
            "${(p * 100).toInt()}%",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Mg.Mangrove,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(Modifier.height(40.dp))

        // Steps List
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            steps.forEachIndexed { i, s ->
                val progress = when (i) {
                    0 -> p
                    1 -> p
                    2 -> p.coerceIn(0.67f, 1f)
                    3 -> p.coerceIn(0.84f, 1f)
                    else -> 1f
                }
                val currentStep = i == 0 && p in 0.17f..0.34f ||
                                 i == 1 && p in 0.34f..0.66f ||
                                 i == 2 && p in 0.66f..0.83f ||
                                 i == 3 && p in 0.83f..1f

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentStep) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Mg.MangroveLight,
                                strokeWidth = 2.dp
                            )
                        } else {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(20.dp),
                                color = Mg.MangroveSoft,
                                trackColor = Mg.MangroveSoft,
                                strokeWidth = 2.dp
                            )
                        }
                        if (currentStep) {
                            Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp), tint = Mg.MangroveLight)
                        } else if (progress >= 1f) {
                            Icon(Icons.Outlined.CheckCircle, null, modifier = Modifier.size(16.dp), tint = Mg.Mangrove)
                        }
                    }
                    Text(
                        s,
                        fontSize = 15.sp,
                        color = if (currentStep) Mg.Mangrove
                                else if (progress >= 1f) Mg.Ink
                                else Mg.InkMuted,
                        fontWeight = if (currentStep) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

// ---------------- 5. MAP ----------------
@Composable
fun MapScreen(vm: AppViewModel, nav: NavController) {
    val ctx = LocalContext.current
    val online = rememberOnline()
    var recenter by remember { mutableIntStateOf(0) }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.pickMap(uri,
                onValid = { /* info form will show */ },
                onInvalid = { Toast.makeText(ctx, "No XYZ tiles found in that zip", Toast.LENGTH_SHORT).show() }
            )
        }
    }
    var editing by remember { mutableStateOf<Assessment?>(null) }
    var deleting by remember { mutableStateOf<Assessment?>(null) }
    var viewing by remember { mutableStateOf<Assessment?>(null) }       // tile view dialog
    var clearing by remember { mutableStateOf(false) }
    var removingMap by remember { mutableStateOf(false) }               // confirm: remove my QGIS map
    // Three views of the same map: 0 = Map 1 AI prediction/pinpointing, 1 = Heatmap, 2 = Map 2 capture tracking
    var viewMode by remember { mutableIntStateOf(0) }
    val shown = vm.mapAssessments.filter { vm.filter == null || it.status == vm.filter }   // All / Healthy / Unhealthy, for the selected QGIS map
    val onMap = if (vm.showPins) shown.filter { !it.outOfReach } else emptyList()        // Invalid photos get no pin; the Pins chip hides all pins

    // Pending import dialog state
    var showMapInfoForm by remember { mutableStateOf(false) }
    var pendingInfo by remember { mutableStateOf<MapInfo?>(null) }

    // Auto-trigger map info form when pickupMap succeeds
    LaunchedEffect(vm.pendingMapUri) {
        if (vm.pendingMapUri != null) {
            showMapInfoForm = true
            pendingInfo = vm.ortho?.info
        }
    }

    // Handle map info form confirmation
    if (showMapInfoForm) {
        MapInfoForm(
            initialInfo = pendingInfo ?: MapInfo(),
            onConfirm = { info ->
                vm.confirmImportMap(info) { src ->
                    showMapInfoForm = false
                    pendingInfo = null
                    if (src != null) {
                        Toast.makeText(ctx,
                            "QGIS map added: ${src.info.name.ifEmpty { src.label }}" +
                                    (src.info.location.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""),
                            Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(ctx, "No XYZ tiles found in that zip", Toast.LENGTH_LONG).show()
                    }
                }
                showMapInfoForm = false
                pendingInfo = null
            },
            onCancel = {
                showMapInfoForm = false
                vm.cancelImportMap()
                pendingInfo = null
            }
        )
    }

    Column(Modifier.fillMaxSize().background(Mg.Background).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Map", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        LocationLine(Mg.Muted, Modifier.padding(bottom = 8.dp),
            text = vm.ortho?.info?.name?.ifEmpty { null }
                ?: if (vm.ortho?.custom == true) "Your QGIS map" else Config.SITE_NAME)
        if (!online && vm.baseMap != BaseMap.NONE) {
            Text("Offline: your QGIS map still works. The ${vm.baseMap.label.lowercase()} background needs internet.",
                fontSize = 12.sp, color = Mg.AmberText,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp))
                    .background(Mg.AmberSoft).padding(8.dp))
        }

        // View switcher: Map 1 AI / Heatmap / Map 2 Capture Tracking
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mg.MangroveSoft.copy(alpha = 0.5f)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                .background(if (viewMode == 0) Mg.MangroveLight else Color.Transparent)
                .clickable { viewMode = 0 }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Insights, null, Modifier.size(15.dp),
                        tint = if (viewMode == 0) Mg.MangroveSoft else Mg.Mangrove)
                    Text("Map 1 · AI", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        color = if (viewMode == 0) Mg.MangroveSoft else Mg.Mangrove)
                }
            }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                .background(if (viewMode == 1) Mg.MangroveLight else Color.Transparent)
                .clickable { viewMode = 1 }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.BubbleChart, null, Modifier.size(15.dp),
                        tint = if (viewMode == 1) Mg.MangroveSoft else Mg.Mangrove)
                    Text("GIS", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        color = if (viewMode == 1) Mg.MangroveSoft else Mg.Mangrove)
                }
            }
            Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                .background(if (viewMode == 2) Mg.MangroveLight else Color.Transparent)
                .clickable { viewMode = 2 }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.FlightTakeoff, null, Modifier.size(15.dp),
                        tint = if (viewMode == 2) Mg.MangroveSoft else Mg.Mangrove)
                    Text("Map 2 · Capture", fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
                        color = if (viewMode == 2) Mg.MangroveSoft else Mg.Mangrove)
                }
            }
        }

        // Map view area
        Box(Modifier.fillMaxWidth().height(screenH * 0.35f).clip(RoundedCornerShape(12.dp))) {
            // Map: pins + individual tile polygons (Map 1), heatmap blobs (Heatmap), or Done / Not Captured cells (Map 2).
            // All views share the same QGIS orthomosaic and study-area boundary.
            OsmMap(
                items = if (viewMode == 0) onMap else emptyList(),
                selectedId = vm.selectedId,  // selection works across all views
                baseMap = vm.baseMap,
                ortho = vm.ortho,
                recenterTick = recenter,
                onSelect = { vm.selectedId = it },
                modifier = Modifier.fillMaxSize(),
                showPins = viewMode == 0 && vm.showPins,  // pins only in Map 1, controlled by chip
                showTileGis = viewMode == 0 && vm.showTileGis,
                tileGisFeatures = vm.tileGisResult.features,
                pinTiles = vm.coverage.tiles,
                showCoverage = viewMode == 2,
                showHeatmap = viewMode == 1 && vm.showHeatmap,
                heatmapPoints = vm.heatmapResult.points,
                // Boundary shows with the heatmap (GIS + Heatmap combined) and in Map 2 - Capture
                showBoundary = viewMode == 1 || viewMode == 2,
                // Map 2: interactive bounding box for selection
                boundingBox = if (viewMode == 2) vm.boundingBox else null,
                // Map 2: capture pins at assessment locations (OUT_OF_REACH items filtered in update block)
                captureItems = shown
            )

            // Floating action buttons
            Column(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { vm.selectedId = null; recenter++ },
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape, color = Color.White, shadowElevation = 3.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.MyLocation, "Recenter on mangrove site", tint = Mg.Forest2)
                    }
                }
            }

            // North arrow over the map
            NorthArrow(Modifier.align(Alignment.TopStart).padding(8.dp))

            // North indicator (top-left) for heatmap
            if (viewMode == 1) {
                Surface(Modifier.align(Alignment.TopStart).padding(8.dp), shape = CircleShape, color = Color.White, shadowElevation = 3.dp) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Text("N", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
                    }
                }
            }

            // Map 2 legend, overlaid on the map so the colours explain themselves
            if (viewMode == 2 && vm.coverage.hasMap) {
                CoverageLegend(Modifier.align(Alignment.BottomStart).padding(8.dp), vm.coverage, vm.boundingBox)
            }

            // Heatmap legend (left side): Unhealthy Area / Healthy Area / Capture status
            if (viewMode == 1 && vm.heatmapResult.points.isNotEmpty()) {
                HeatmapLegend(Modifier.align(Alignment.TopStart).padding(8.dp))
            }

            // Controls on the bottom-right (zoom in / out)
            if (viewMode == 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = RoundedCornerShape(4.dp), color = Color.White, shadowElevation = 3.dp, onClick = { }) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Text("−", fontSize = 24.sp, color = Mg.Ink) }
                    }
                    Surface(shape = RoundedCornerShape(4.dp), color = Color.White, shadowElevation = 3.dp, onClick = { }) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Text("+", fontSize = 24.sp, color = Mg.Ink) }
                    }
                }
            }
        }

        // Heatmap headline stats
        if (viewMode == 1 && vm.heatmapResult.points.isNotEmpty()) {
            HeatmapStats(vm.heatmapResult)
        }

        // Map 2 headline stats: captured / uncaptured area in m²
        if (viewMode == 2 && vm.coverage.hasMap) {
            CoverageStats(vm.coverage, vm.boundingBox)
        }

        // Map controls
        Text(vm.baseMap.credit, fontSize = 10.sp, color = Mg.Muted, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BaseMap.values().filter { it != BaseMap.NONE && (it != BaseMap.MAPTILER || Config.MAPTILER_KEY.isNotBlank()) }.forEach { b ->
                FilterChip(selected = vm.baseMap == b, onClick = { vm.baseMap = b }, label = { Text(b.label, fontSize = 13.sp) })
            }
            if (viewMode == 0) {
                // Pins chip (Map 1 only)
                FilterChip(
                    selected = vm.showPins,
                    onClick = { vm.showPins = !vm.showPins; vm.selectedId = null },
                    label = { Text("Pins", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(16.dp)) }
                )
            }
            if (viewMode == 1) {
                // Heatmap on/off chip
                FilterChip(
                    selected = vm.showHeatmap,
                    onClick = { vm.showHeatmap = !vm.showHeatmap },
                    label = { Text("GIS", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Outlined.BubbleChart, null, Modifier.size(16.dp)) }
                )
            }
            // Upload button - inside the Map section, both views
            FilterChip(
                selected = false,
                onClick = { nav.navigate("upload") },
                label = { Text("Upload", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.FileUpload, null, Modifier.size(16.dp)) }
            )
        }

        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                // Column info for current map. The map name/location the user entered lead the label
                // (the built-in map has no info, so it keeps its original wording).
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(vm.ortho?.let { o ->
                        (o.info.name.ifEmpty { if (o.custom) "Your QGIS map" else "Built-in QGIS map" }) + " · ${o.label}"
                    } ?: "No QGIS map",
                        fontSize = 12.sp, color = Mg.Muted)
                    // Map info entered in the form: location name, description and additional details
                    vm.ortho?.info?.let { info ->
                        if (info.location.isNotBlank()) {
                            Text("Location: ${info.location}", fontSize = 11.sp, color = Mg.Muted)
                        }
                        if (info.description.isNotBlank()) {
                            Text(info.description, fontSize = 11.sp, color = Mg.Muted,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (info.additionalInfo.isNotBlank()) {
                            Text(info.additionalInfo, fontSize = 11.sp, color = Mg.Muted,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (vm.ortho?.custom == true) {
                        Text("Study area: ${vm.ortho?.info?.let { if (it.totalAreaSqm > 0) String.format("%.0f m²", it.totalAreaSqm) else "not set" } ?: "unknown"}",
                            fontSize = 11.sp, color = Mg.Muted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton(Icons.Outlined.Layers, if (vm.hasCustomMap) "Replace" else "QGIS layer") {
                            mapPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                        }
                        SmallButton(Icons.Outlined.Add, "New map") {
                            pendingInfo = vm.ortho?.info
                            showMapInfoForm = true
                        }
                        if (vm.hasCustomMap) {
                            if (vm.ortho?.custom == true) SmallButton(Icons.Outlined.Restore, "Built-in") { vm.useBuiltInMap() }
                            else SmallButton(Icons.Outlined.SwapHoriz, "My map") { vm.useMyMap() }
                            SmallButton(Icons.Outlined.Delete, "Remove") { removingMap = true }
                        }
                    }
                }
            }

            // Assessment log list
            item {
                shown.firstOrNull { it.id == vm.selectedId }?.let { a -> SelectedCard(a) { viewing = a } }
            }

                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Assessment Log", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
                        Text(if (vm.filter == null) "${vm.mapAssessments.size} sites" else "${shown.size} of ${vm.mapAssessments.size} sites",
                            fontSize = 12.5.sp, color = Mg.Muted)
                    }
                }
                if (vm.mapAssessments.isNotEmpty()) item {
                    Text("Filter by status:", fontSize = 13.sp, color = Mg.Muted, modifier = Modifier.padding(top = 4.dp))
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MgButton(Icons.Outlined.PictureAsPdf, "Save Log to PDF", "Timestamp, status, action", modifier = Modifier.weight(1f)) {
                            val ok = PdfExporter.save(ctx, shown)
                            Toast.makeText(ctx, if (ok) "PDF saved to Downloads" else "Could not save PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                if (vm.mapAssessments.isNotEmpty()) item {
                    SmallButton(Icons.Outlined.DeleteSweep, "Clear log") { clearing = true }
                }
                if (shown.isEmpty()) item {
                    Text(if (vm.mapAssessments.isEmpty()) "No assessments yet. Tap Upload to add photos." else "No sites match this filter.",
                        fontSize = 14.sp, color = Mg.Muted)
                }
                items(shown, key = { it.id }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                            .background(if (a.id == vm.selectedId) Mg.GreenSoft else Color.White)
                            .clickable { vm.selectedId = a.id }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Thumb(a, 52.dp, Modifier.clickable { if (!a.outOfReach) viewing = a })
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
                            Box(Modifier.padding(vertical = 2.dp)) { StatusPill(a.status) }
                            Text(a.timeMillis.fmt(), fontSize = 12.sp, color = Mg.Muted)
                            Text("GPS ${a.gpsText}", fontSize = 11.5.sp, color = Mg.Muted)
                            Text(a.tilesText, fontSize = 11.5.sp, color = Mg.Muted)
                        }
                        IconButton({ editing = a }, Modifier.size(38.dp)) {
                            Icon(Icons.Outlined.Edit, "Rename", tint = Mg.Muted, modifier = Modifier.size(20.dp))
                        }
                        IconButton({ deleting = a }, Modifier.size(38.dp)) {
                            Icon(Icons.Outlined.Delete, "Delete", tint = Mg.Muted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

    viewing?.let { a -> TileViewDialog(a) { viewing = null } }
    if (removingMap) {
        AlertDialog(
            onDismissRequest = { removingMap = false },
            title = { Text("Remove your QGIS map?") },
            text = { Text("This deletes the map you added from this phone and shows the built-in map again. Your assessments are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeMyMap(); removingMap = false
                    Toast.makeText(ctx, "Your QGIS map was removed", Toast.LENGTH_SHORT).show()
                }) { Text("Remove", color = Mg.Red) }
            },
            dismissButton = { TextButton(onClick = { removingMap = false }) { Text("Cancel") } }
        )
    }

    if (clearing) {
        AlertDialog(
            onDismissRequest = { clearing = false },
            title = { Text("Clear the whole log?") },
            text = { Text("This removes all ${vm.assessments.size} sites, their dots on the map and their preview photos. It cannot be undone.") },
            confirmButton = { TextButton({ vm.clearLog(); clearing = false }) { Text("Clear log") } },
            dismissButton = { TextButton({ clearing = false }) { Text("Cancel") } }
        )
    }
    editing?.let { a -> RenameDialog(a.name, { editing = null }) { vm.rename(a.id, it); editing = null } }
    deleting?.let { a ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${a.name}?") },
            text = { Text("This removes it from the log and the map.") },
            confirmButton = { TextButton({ vm.delete(a.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } }
        )
    }
}

/** Small preview photo of an assessment (or an icon if none is saved). */
@Composable
fun Thumb(a: Assessment, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val img = remember(a.thumb) {
        if (a.thumb.isEmpty()) null else BitmapFactory.decodeFile(Thumbs.file(ctx, a.thumb).path)?.asImageBitmap()
    }
    Box(modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Mg.GreenSoft), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, "Photo preview", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Outlined.Image, null, tint = Mg.Forest2, modifier = Modifier.size(size * 0.5f))
    }
}

/** Details of the selected dot (shown above the log when you tap a dot or a log row). */
@Composable
private fun SelectedCard(a: Assessment, onTiles: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mg.GreenSoft).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
            StatusPill(a.status)
        }
        Text(a.action, fontSize = 12.5.sp, color = Mg.Ink, modifier = Modifier.padding(top = 4.dp))
        Text("GPS ${a.gpsText}", fontSize = 11.5.sp, color = Mg.Muted)
        Text(a.tilesText, fontSize = 11.5.sp, color = Mg.Muted)
        if (!a.outOfReach) Box(Modifier.padding(top = 8.dp)) { SmallButton(Icons.Outlined.GridView, "View tiles", onTiles) }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(color).border(1.dp, Mg.Line, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, color = Mg.Muted)
    }
}

/**
 * The photo with its 512 px tiles drawn on top: green = Healthy, red = Unhealthy, clear = Non-mangrove.
 * Tap a tile to see its details: size (m²), location (GPS) and how much of the tile is Healthy / Unhealthy.
 */
@Composable
private fun TileViewDialog(a: Assessment, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val photo = remember(a.thumb) {
        if (a.thumb.isEmpty()) null else BitmapFactory.decodeFile(Thumbs.file(ctx, a.thumb).path)?.asImageBitmap()
    }
    val rows = remember(a.grid) { a.grid.split("/").filter { it.isNotEmpty() } }
    var picked by remember { mutableStateOf<Pair<Int, Int>?>(null) }          // (row, column) of the tapped tile
    val info = picked?.let { a.tileInfo(it.first, it.second) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(a.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (photo != null && rows.isNotEmpty()) {
                    val nCols = rows.maxOf { it.length }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(photo.width / photo.height.toFloat())
                            .pointerInput(rows) {
                                detectTapGestures { p ->
                                    val c = (p.x / (size.width / nCols)).toInt().coerceIn(0, nCols - 1)
                                    val r = (p.y / (size.height / rows.size)).toInt().coerceIn(0, rows.size - 1)
                                    picked = r to c
                                }
                            }
                    ) {
                        Image(photo, "Photo with tiles", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        Canvas(Modifier.fillMaxSize()) {
                            val cw = size.width / nCols
                            val ch = size.height / rows.size
                            rows.forEachIndexed { r, line ->
                                line.forEachIndexed { c, t ->
                                    val col = when (t) { 'H' -> Mg.Green; 'U' -> Mg.Red; else -> null }
                                    if (col != null) drawRect(col.copy(alpha = 0.5f), Offset(c * cw, r * ch), Size(cw, ch))
                                    drawRect(Color.White.copy(alpha = 0.7f), Offset(c * cw, r * ch), Size(cw, ch), style = Stroke(1.5f))
                                }
                            }
                            picked?.let { (r, c) ->                                  // thick outline on the tapped tile
                                drawRect(Color.White, Offset(c * cw, r * ch), Size(cw, ch), style = Stroke(6f))
                            }
                        }
                    }
                } else {
                    Text("No tile view saved for this entry (it was analyzed before this feature). Clear the log and analyze it again.",
                        fontSize = 13.sp, color = Mg.Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(Mg.Green, "Healthy"); LegendDot(Mg.Red, "Unhealthy"); LegendDot(Color.White, "Non-mangrove")
                }
                Text(a.tilesText, fontSize = 13.sp, color = Mg.Ink)
                Text("Photo GPS ${a.gpsText}", fontSize = 12.sp, color = Mg.Muted)
                if (a.coveredM2 > 0) {
                    Text(String.format(Locale.US, "Area covered by the tiles: %,.0f m² (%d tiles)", a.coveredM2, rows.sumOf { it.length }),
                        fontSize = 12.sp, color = Mg.Muted)
                }
                TileDetails(a, picked, info)
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } }
    )
}

/** Details of the tapped tile: size, Healthy / Unhealthy share of its area, and its geographic location. */
@Composable
private fun TileDetails(a: Assessment, picked: Pair<Int, Int>?, info: TileInfo?) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Mg.GreenSoft).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        when {
            a.geo.isEmpty() -> Text(
                "No tile details saved for this entry (it was analyzed before this feature). Delete it and analyze the photo again.",
                fontSize = 12.5.sp, color = Mg.Muted)
            picked == null || info == null -> Text("Tap a tile on the photo to see its details.", fontSize = 12.5.sp, color = Mg.Muted)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tile ${picked.first + 1} · ${picked.second + 1}", Modifier.weight(1f),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
                    StatusPill(info.status)
                }
                Text("Row ${picked.first + 1}, column ${picked.second + 1}", fontSize = 11.5.sp, color = Mg.Muted)
                Text(String.format(Locale.US, "Size: %.1f m × %.1f m = %.1f m²", info.sideM, info.sideM, info.areaM2),
                    fontSize = 13.sp, color = Mg.Ink)
                Text(String.format(Locale.US, "Healthy: %d%% of the tile (%.1f m²)", info.healthyPct, info.areaOf(info.healthyPct)),
                    fontSize = 13.sp, color = Mg.GreenText)
                Text(String.format(Locale.US, "Unhealthy: %d%% of the tile (%.1f m²)", info.unhealthyPct, info.areaOf(info.unhealthyPct)),
                    fontSize = 13.sp, color = Mg.RedText)
                if (info.nonMangrovePct > 0) {
                    Text(String.format(Locale.US, "Non-mangrove: %d%% of the tile (%.1f m²)", info.nonMangrovePct, info.areaOf(info.nonMangrovePct)),
                        fontSize = 13.sp, color = Mg.GreyText)
                }
                Text(String.format(Locale.US, "Location (tile centre): %.6f, %.6f", info.lat, info.lon),
                    fontSize = 12.5.sp, color = Mg.Ink, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
private fun RenameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename site") },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Site name") }) },
        confirmButton = { TextButton({ if (text.isNotBlank()) onSave(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

/**
 * Map 2 - legend for the capture tracking view, two states: green = Captured (a drone photo
 * covered the spot), yellow = Uncaptured (the rest of the selection area). Drawn over the map
 * so the colours explain themselves.
 */
@Composable
private fun CoverageLegend(modifier: Modifier = Modifier, cov: CoverageResult, boundingBox: GeoRotatableBox? = null) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text("Capture status", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Mg.Ink, letterSpacing = 0.5.sp)
        LegendDot(Mg.Healthy.copy(alpha = 0.45f), "Captured")
        LegendDot(Mg.Amber, "Uncaptured")
        LegendDot(Color(0xFF000000), "Study-area boundary")
        if (boundingBox != null) {
            LegendDot(Color(0xFFFF0000), "Selection box")
        }
        if (cov.photos > 0) {
            Text("From ${cov.photos} drone photo${if (cov.photos > 1) "s" else ""} · ${cov.capturedPct}% covered",
                fontSize = 10.sp, color = Mg.Muted)
        }
    }
}

/**
 * Heatmap legend, left side of the map, matching the reference image:
 * Unhealthy Area (red ramp) and Healthy Area (green ramp),
 * plus Capture status (Done / Not Captured).
 */
@Composable
private fun HeatmapLegend(modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Healthy Area", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        val healthyList = listOf(
            Color(0xFFEEF9EA) to "Low Density",
            Color(0xFFA8F0A8) to "Medium Density",
            Color(0xFF7FE07F) to "Mild Density",
            Color(0xFF2A8A2A) to "High Density"
        )
        healthyList.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 11.sp, color = Mg.Ink)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Unhealthy Area", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        val unhealthyList = listOf(
            Color(0xFFFFE5E0) to "Low Density",
            Color(0xFFFFA08A) to "Medium Density",
            Color(0xFFFF7B5A) to "Mild Density",
            Color(0xFFC81212) to "High Density"
        )
        unhealthyList.forEach { (color, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(color))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 11.sp, color = Mg.Ink)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Capture status", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(Mg.Healthy.copy(alpha = 0.45f)))
            Spacer(Modifier.width(6.dp))
            Text("Done (Captured)", fontSize = 11.sp, color = Mg.Ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(Color(0xFFFFC107)))
            Spacer(Modifier.width(6.dp))
            Text("Not Captured", fontSize = 11.sp, color = Mg.Ink)
        }
    }
}

/**
 * Heatmap stats header: total healthy / unhealthy area and % coverage.
 */
@Composable
private fun HeatmapStats(res: HeatmapResult) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mg.CardBackground)
            .border(1.dp, Mg.Line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Healthy", fontSize = 11.sp, color = Mg.GreenText, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%,.0f m²", res.healthyM2), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text("Unhealthy", fontSize = 11.sp, color = Mg.RedText, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%,.0f m²", res.unhealthyM2), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text("Total area", fontSize = 11.sp, color = Mg.Muted, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%,.0f m²", res.totalM2), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        }
        Box(
            Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { res.healthyPct / 100f },
                modifier = Modifier.fillMaxSize(),
                color = Mg.Healthy,
                trackColor = Color(0xFFFFE0B2),
                strokeWidth = 4.dp
            )
            Text("${res.healthyPct}%", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        }
    }
}

/**
 * Map 2 - the estimated area numbers under the map: how many m² of the selection area are
 * captured by the uploaded drone images and how many are not (uncaptured = selection area
 * − captured), updating live as photos are analyzed. Selection area is the user's selection box.
 * Displays: % covered, captured area (m²), uncaptured area (m²), selection area (m²).
 */
@Composable
private fun CoverageStats(cov: CoverageResult, boundingBox: GeoRotatableBox? = null) {
    val boxArea = boundingBox?.areaM2
    val boxLabel = if (boxArea != null) String.format(Locale.US, "%,.0f m²", boxArea) else "..."

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mg.CardBackground)
            .border(1.dp, Mg.Line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // % Covered indicator with circular progress
        Box(
            Modifier.size(52.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { if (cov.totalM2 > 0) cov.capturedPct / 100f else 0f },
                modifier = Modifier.size(46.dp),
                color = Mg.Healthy,
                trackColor = Color(0xFFFFE0B2),
                strokeWidth = 4.dp
            )
            Text("${cov.capturedPct}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text("Captured", fontSize = 11.sp, color = Mg.GreenText, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%,.0f m²", cov.capturedM2), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text("Uncaptured", fontSize = 11.sp, color = Mg.Amber, fontWeight = FontWeight.Bold)
            Text(String.format(Locale.US, "%,.0f m²", cov.uncapturedM2), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        }
        Column(Modifier.weight(1f)) {
            Text("Selection area", fontSize = 11.sp, color = if (boundingBox != null) Mg.Ink else Mg.Muted, fontWeight = FontWeight.Bold)
            Text(boxLabel, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (boundingBox != null) Mg.Ink else Mg.Muted)
        }
    }
}
