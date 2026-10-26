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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

private fun Long.fmt(): String = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.US).format(this)

// ---------------- 1. SPLASH ----------------
@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(1800); onDone() }
    Column(
        Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Mg.Forest2, Mg.Forest))).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        DottedIcon(Icons.Outlined.Eco, 88.dp, tint = Mg.Cream)
        Spacer(Modifier.height(18.dp))
        Text("MangroveEye", color = Mg.Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Drone Based Assessment of\nMangrove Forest Health in Davao City",
            color = Color(0xFFB7CDBF), fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

// ---------------- 2. HOME ----------------
@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val last = vm.assessments.firstOrNull()
    Column(Modifier.fillMaxSize().background(Mg.Cream).padding(18.dp)) {
        Text("MangroveEye", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        Text("Mangrove Health Assessment", fontSize = 15.sp, color = Mg.Muted)
        LocationLine(Mg.Green, Modifier.padding(vertical = 12.dp))

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Mg.Forest).padding(16.dp)) {
            Text("LAST ASSESSMENT", fontSize = 12.sp, letterSpacing = 0.8.sp, color = Color(0xFFB7CDBF))
            Text(last?.let { if (it.showsPercent) "${it.status.label} · ${it.healthy}% healthy" else it.status.label } ?: "No assessments yet",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Mg.Cream)
        }
        Spacer(Modifier.height(16.dp))
        MgButton(Icons.Outlined.FileUpload, "Upload Assessment", "Single photo or batch", primary = true) { nav.navigate("upload") }
        Text("Upload one drone photo or a whole batch. The GPS is read from each photo first and stays with its result.",
            fontSize = 13.sp, color = Mg.Muted, modifier = Modifier.padding(vertical = 8.dp))
        MgButton(Icons.Outlined.Map, "Map", "View all sites") { nav.navigate("map") }
    }
}

// ---------------- 3. UPLOAD ASSESSMENT (single or batch) ----------------
@Composable
fun UploadScreen(vm: AppViewModel, nav: NavController) {
    // OpenDocument / OpenMultipleDocuments give the original files, so the EXIF GPS tags are kept.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.pickPhotos(uris)       // one photo or many: same box
    }

    Column(Modifier.fillMaxSize().background(Mg.Cream).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White)
                .clickable { vm.clearForm(); nav.popBackStack() }, contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Mg.Ink) }
            Spacer(Modifier.width(10.dp))
            Text("Upload Assessment", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("Add one photo or a whole batch in the same box. The GPS is read from each photo first and stays with its result.",
                fontSize = 13.sp, color = Mg.Muted, modifier = Modifier.padding(vertical = 14.dp))

            SectionLabel(Icons.Outlined.Image, "DRONE PHOTOS")
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth()
                    .drawBehind {
                        drawRoundRect(Mg.Line, cornerRadius = CornerRadius(40f),
                            style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                    }
                    .clip(RoundedCornerShape(14.dp)).background(Color(0xFFFAF9F4))
                    .clickable { picker.launch(arrayOf("image/jpeg", "image/*")) }.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DottedIcon(if (vm.photos.isEmpty()) Icons.Outlined.PhotoLibrary else Icons.Outlined.CheckCircle, 60.dp)
                Spacer(Modifier.height(10.dp))
                Text(if (vm.photos.isEmpty()) "Tap to choose photos" else "${vm.photos.size} selected · tap to add more",
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Mg.Ink)
                Text("Pick one photo or many at once", fontSize = 12.5.sp, color = Mg.Muted)
            }
            if (vm.photos.size > 1) {
                TextButton(onClick = { vm.clearPhotos() }, modifier = Modifier.align(Alignment.End)) {
                    Text("Clear all", color = Mg.Red, fontSize = 13.sp)
                }
            }
            vm.message?.let { Text(it, fontSize = 13.sp, color = Mg.Green, modifier = Modifier.padding(top = 8.dp)) }

            vm.photos.forEachIndexed { i, p ->
                Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color.White).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Image, null, tint = Mg.Forest2, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(p.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = Mg.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton({ vm.removePhoto(i) }, Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Close, "Remove", tint = Mg.Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (p.fromExif) {
                        Text("GPS ${p.lat}, ${p.lon} (read from the photo)", fontSize = 12.5.sp, color = Mg.Green)
                        if (vm.isOutside(p)) {
                            Text("Outside your QGIS map. It will be marked Invalid (no AI prediction) and not shown on the map.",
                                fontSize = 12.5.sp, color = Mg.AmberText, modifier = Modifier.padding(top = 4.dp))
                        }
                    } else {
                        Text("No GPS in this photo. It can't be placed on the map, so it will be skipped.",
                            fontSize = 12.5.sp, color = Mg.AmberText, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(vm.siteName, { vm.siteName = it }, Modifier.fillMaxWidth(),
                label = { Text("Site name (optional)") }, placeholder = { Text("e.g. North channel") },
                supportingText = { if (vm.photos.size > 1) Text("Batch photos are named: name 1, name 2, ...") },
                singleLine = true)
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MgButton(null, "Cancel", modifier = Modifier.weight(1f)) { vm.clearForm(); nav.popBackStack() }
            MgButton(null, vm.photos.count { it.latLon() != null }.let { if (it > 1) "Analyze $it" else "Analyze" }, primary = true,
                modifier = Modifier.weight(1f)) {
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
    val steps = listOf("Extract GPS from EXIF", "Split into 512 x 512 px tiles", "AI classifies tiles", "Match geographic coordinates")
    val p = vm.progress
    Column(Modifier.fillMaxSize().background(Mg.Cream).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(40.dp))
        CircularProgressIndicator(progress = { p }, Modifier.size(120.dp), color = Mg.Forest2, trackColor = Mg.Line, strokeWidth = 8.dp)
        Spacer(Modifier.height(24.dp))
        Text(if (vm.photoTotal > 1) "Analyzing photo ${vm.photoIndex} of ${vm.photoTotal}" else "Analyzing Image",
            fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        Text("Tiling the image and classifying each tile as healthy or unhealthy.",
            fontSize = 13.5.sp, color = Mg.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 6.dp))
        LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().padding(top = 8.dp), color = Mg.Forest2, trackColor = Mg.Line)
        Text("${(p * 100).toInt()}%", fontSize = 12.5.sp, color = Mg.Muted, modifier = Modifier.align(Alignment.Start).padding(vertical = 6.dp))
        steps.forEachIndexed { i, s ->
            val done = i < 2 || (i == 2 && p >= 1f) || (i == 3 && p >= 1f)
            val current = !done && (i == 2 || (i == 3 && p >= 1f))
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s, fontSize = 14.sp, color = if (current) Mg.Forest2 else if (done) Mg.Ink else Mg.Muted,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal)
                Text(if (done) "✓" else if (current) "●" else "", color = Mg.Green)
            }
            HorizontalDivider(color = Color(0xFFEEE6D8))
        }
    }
}

// ---------------- 5. MAP ----------------
@Composable
fun MapScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val online = rememberOnline()
    var recenter by remember { mutableIntStateOf(0) }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val mapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            vm.importMap(u) { src ->
                Toast.makeText(ctx,
                    if (src != null) "QGIS map added: ${src.label} (zoom ${src.minZoom}-${src.maxZoom})"
                    else "No XYZ tiles found in that zip", Toast.LENGTH_LONG).show()
            }
        }
    }
    var editing by remember { mutableStateOf<Assessment?>(null) }
    var deleting by remember { mutableStateOf<Assessment?>(null) }
    var viewing by remember { mutableStateOf<Assessment?>(null) }       // tile view dialog
    var clearing by remember { mutableStateOf(false) }
    var removingMap by remember { mutableStateOf(false) }               // confirm: remove my QGIS map
    val gisRes = vm.gis                                                   // built from the uploaded photos
    val showZones = vm.showGis && gisRes != null
    var gisPick by remember { mutableStateOf<GisCell?>(null) }          // the GIS tile you tapped
    LaunchedEffect(vm.assessments.size) { gisPick = null }              // a photo was added or deleted: clear the pick
    val shown = vm.assessments.filter { vm.filter == null || it.status == vm.filter }   // All / Healthy / Unhealthy
    val onMap = if (vm.showPins) shown.filter { !it.outOfReach } else emptyList()        // Invalid photos get no pin; the Pins chip hides all pins

    Column(Modifier.fillMaxSize().background(Mg.Cream).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Map", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        LocationLine(Mg.Muted, Modifier.padding(bottom = 8.dp))
        if (!online && vm.baseMap != BaseMap.NONE) {
            Text("Offline: your QGIS map still works. The ${vm.baseMap.label.lowercase()} background needs internet.",
                fontSize = 12.sp, color = Mg.AmberText,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp))
                    .background(Mg.AmberSoft).padding(8.dp))
        }
        Box(Modifier.fillMaxWidth().height(screenH * 0.35f).clip(RoundedCornerShape(12.dp))) {
            OsmMap(onMap, vm.selectedId, vm.baseMap, vm.ortho, recenter, { vm.selectedId = it }, Modifier.fillMaxSize(),
                gisCells = if (showZones) gisRes!!.cells else emptyList(), gisVisible = vm.gisVisible,
                gisSelected = if (showZones) gisPick else null, onGisTap = { gisPick = it })
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
            if (showZones) {
                NorthArrow(Modifier.align(Alignment.TopStart).padding(8.dp))
                GisLegend(vm.gisVisible, Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        Text(vm.baseMap.credit, fontSize = 10.sp, color = Mg.Muted, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BaseMap.values().filter { it != BaseMap.MAPTILER || Config.MAPTILER_KEY.isNotBlank() }.forEach { b ->
                FilterChip(selected = vm.baseMap == b, onClick = { vm.baseMap = b }, label = { Text(b.label, fontSize = 13.sp) })
            }
            // GIS chip, right next to "QGIS only": the tiles of the photos you uploaded, as coloured squares.
            FilterChip(
                selected = vm.showGis,
                onClick = { vm.showGis = !vm.showGis; gisPick = null },
                label = { Text("GIS", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Layers, null, Modifier.size(16.dp)) }
            )
            // Pins chip, right next to "GIS": hides / shows the coloured pins so the GIS tiles are easy to see.
            FilterChip(
                selected = vm.showPins,
                onClick = { vm.showPins = !vm.showPins; vm.selectedId = null },
                label = { Text("Pins", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.LocationOn, null, Modifier.size(16.dp)) }
            )
        }

        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                // Filter and legend in one row: the colored dot is the legend for the map dots.
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = vm.filter == null, onClick = { vm.filter = null },
                        label = { Text("All ${vm.assessments.count { !it.outOfReach }}", fontSize = 13.sp) })
                    listOf(Status.HEALTHY, Status.UNHEALTHY).forEach { st ->     // All / Healthy / Unhealthy
                        FilterChip(
                            selected = vm.filter == st,
                            onClick = { vm.filter = if (vm.filter == st) null else st },
                            label = { Text(st.label, fontSize = 13.sp) },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(st.dot())) }
                        )
                    }
                }
            }
            if (vm.showGis && gisRes == null) item {
                Text("GIS shows the tiles of the photos you upload. Upload a photo that is inside the QGIS map first.",
                    fontSize = 13.sp, color = Mg.Muted)
            }
            if (showZones) item { GisTileCard(gisPick) }
            shown.firstOrNull { it.id == vm.selectedId }?.let { a -> item { SelectedCard(a) { viewing = a } } }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(vm.ortho?.let { (if (it.custom) "Your QGIS map" else "Built-in QGIS map") + " · ${it.label}" } ?: "No QGIS map",
                        fontSize = 12.sp, color = Mg.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton(Icons.Outlined.Layers, if (vm.hasCustomMap) "Replace" else "QGIS layer") {
                            mapPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                        }
                        if (vm.hasCustomMap) {
                            if (vm.ortho?.custom == true) SmallButton(Icons.Outlined.Restore, "Built-in") { vm.useBuiltInMap() }
                            else SmallButton(Icons.Outlined.SwapHoriz, "My map") { vm.useMyMap() }
                            SmallButton(Icons.Outlined.Delete, "Remove") { removingMap = true }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Assessment Log", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
                    Text(if (vm.filter == null) "${vm.assessments.size} sites" else "${shown.size} of ${vm.assessments.size} sites",
                        fontSize = 12.5.sp, color = Mg.Muted)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MgButton(Icons.Outlined.PictureAsPdf, "Save Log to PDF", "Timestamp, status, action", modifier = Modifier.weight(1f)) {
                        val ok = PdfExporter.save(ctx, vm.assessments)
                        Toast.makeText(ctx, if (ok) "PDF saved to Downloads" else "Could not save PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (vm.gis != null) {
                item {
                    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(System.currentTimeMillis())
                    val geoJsonFileName = "MangroveEye_GIS_Export_$dateFmt.geojson"
                    val geoJsonExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/geo+json")) { uri ->
                        uri?.let { u ->
                            try {
                                ctx.contentResolver.openOutputStream(u)?.use { stream ->
                                    stream.write(vm.gis!!.toGeoJson().toByteArray())
                                }
                                Toast.makeText(ctx, "GeoJSON saved successfully", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Failed to save GeoJSON", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    SmallButton(Icons.Outlined.Download, "Download GeoJSON") {
                        geoJsonExporter.launch(geoJsonFileName)
                    }
                }
            }
            if (vm.assessments.isNotEmpty()) item {
                SmallButton(Icons.Outlined.DeleteSweep, "Clear log") { clearing = true }
            }
            if (shown.isEmpty()) item {
                Text(if (vm.assessments.isEmpty()) "No assessments yet. Upload one from Home." else "No sites match this filter.",
                    fontSize = 14.sp, color = Mg.Muted)
            }
            items(shown, key = { it.id }) { a ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                        .background(if (a.id == vm.selectedId) Mg.GreenSoft else Color.White)
                        .clickable { vm.selectedId = a.id }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Thumb(a, 52.dp, Modifier.clickable { if (!a.outOfReach) viewing = a })     // tap the photo to see the tiles (not for Invalid photos)
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

// ---------------- 6. GIS HEALTH MAP ----------------
// Drawn on the Map screen by the GIS chip. It uses the tiles of the photos in the assessment log (see Gis.kt).

/** Details of the GIS tile you tapped: location, area, and its Healthy / Unhealthy share. */
@Composable
private fun GisTileCard(c: GisCell?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White).padding(12.dp)) {
        if (c == null) {
            Text("GIS tiles: tap a tile on the map to see its location, area and Healthy / Unhealthy share.", fontSize = 13.sp, color = Mg.Muted)
            return@Column
        }
        fun area(m2: Double) = String.format(Locale.US, "%,.1f m²", m2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(Color(c.cls.color)))
            Spacer(Modifier.width(8.dp))
            Text("GIS tile · ${c.cls.label}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        }
        if (c.source.isNotEmpty()) Text("From photo: ${c.source}", fontSize = 12.5.sp, color = Mg.Muted,
            modifier = Modifier.padding(top = 2.dp))
        Text("Location (centre): ${String.format(Locale.US, "%.6f, %.6f", c.centerLat, c.centerLon)}",
            fontSize = 12.5.sp, color = Mg.Ink, modifier = Modifier.padding(top = 6.dp))
        Text(String.format(Locale.US, "Corners: N %.6f · S %.6f · W %.6f · E %.6f", c.north, c.south, c.west, c.east),
            fontSize = 11.5.sp, color = Mg.Muted)
        Text("Tile area: ${area(c.areaM2)} (${String.format(Locale.US, "%.4f", c.areaM2 / 10_000.0)} ha)",
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Mg.Ink, modifier = Modifier.padding(top = 6.dp))
        for ((label, pct, m2, col) in listOf(
            Quad("Healthy", c.healthyOfTile, c.healthyM2, GisClass.HEALTHY.color),
            Quad("Unhealthy", c.unhealthyOfTile, c.unhealthyM2, GisClass.UNHEALTHY.color),
            Quad("Non-mangrove", c.nonMangroveOfTile, c.nonMangroveM2, GisClass.NON_MANGROVE.color)
        )) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 14.dp, height = 8.dp).background(Color(col)))
                Spacer(Modifier.width(8.dp))
                Text(label, Modifier.weight(1f), fontSize = 13.sp, color = Mg.Ink)
                Text("$pct%  ·  ${area(m2)}", fontSize = 13.sp, color = Mg.Ink)
            }
        }
        Text("Healthy share of the mangrove in this tile: ${c.healthyPct}%", fontSize = 11.5.sp, color = Mg.Muted,
            modifier = Modifier.padding(top = 6.dp))
    }
}

private data class Quad(val label: String, val pct: Int, val m2: Double, val color: Int)

/** North arrow in the map corner. */
@Composable
private fun NorthArrow(modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("N", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        Canvas(Modifier.size(width = 16.dp, height = 24.dp)) {
            val w = size.width
            val h = size.height
            drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(w, h); lineTo(w / 2, h * 0.72f); close() }, Mg.Ink)
            drawPath(Path().apply { moveTo(w / 2, 0f); lineTo(0f, h); lineTo(w / 2, h * 0.72f); close() }, Color(0xFF9AA39E))
        }
    }
}

/** Map legend: one colour swatch per class (a class you untick in the layers card is greyed out). */
@Composable
private fun GisLegend(visible: Set<GisClass>, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.9f)).padding(8.dp)) {
        Text("Legend", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        for (c in GisClass.values()) {
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 18.dp, height = 10.dp).background(Color(c.color)))
                Spacer(Modifier.width(6.dp))
                Text(c.label, fontSize = 10.5.sp, color = if (c in visible) Mg.Ink else Mg.Muted)
            }
        }
    }
}