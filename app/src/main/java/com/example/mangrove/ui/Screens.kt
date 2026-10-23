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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
        Spacer(Modifier.height(6.dp))
        Text("AERIAL CANOPY INTELLIGENCE", color = Color(0xFFB7CDBF), fontSize = 12.sp, letterSpacing = 1.4.sp)
    }
}

// ---------------- 2. HOME ----------------
@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val last = vm.assessments.firstOrNull()
    Column(Modifier.fillMaxSize().background(Mg.Cream).padding(18.dp)) {
        Text("AERIAL CANOPY INTELLIGENCE", fontSize = 11.sp, letterSpacing = 1.4.sp, color = Mg.Muted)
        Text("MangroveEye", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        Text("Mangrove Health Assessment", fontSize = 15.sp, color = Mg.Muted)
        LocationLine(Mg.Green, Modifier.padding(vertical = 12.dp))

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Mg.Forest).padding(16.dp)) {
            Text("LAST ASSESSMENT", fontSize = 12.sp, letterSpacing = 0.8.sp, color = Color(0xFFB7CDBF))
            Text(last?.let { "${it.status.label} · ${it.healthy}% healthy" } ?: "No assessments yet",
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
                    } else {
                        Text("No GPS in this photo. It can't be placed on the map.",
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
    val shown = vm.assessments.filter { vm.filter == null || it.status == vm.filter }   // All / Healthy / Unhealthy

    Column(Modifier.fillMaxSize().background(Mg.Cream).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Map", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
        LocationLine(Mg.Muted, Modifier.padding(bottom = 8.dp))
        if (!online && vm.baseMap != BaseMap.NONE) {
            Text("Offline: your QGIS map still works. The ${vm.baseMap.label.lowercase()} background needs internet.",
                fontSize = 12.sp, color = Mg.AmberText,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(8.dp))
                    .background(Mg.AmberSoft).padding(8.dp))
        }
        Box(Modifier.fillMaxWidth().height(screenH * 0.31f).clip(RoundedCornerShape(12.dp))) {
            OsmMap(shown, vm.selectedId, vm.baseMap, vm.ortho, recenter, { vm.selectedId = it }, Modifier.fillMaxSize())
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
        }
        Text(vm.baseMap.credit, fontSize = 10.sp, color = Mg.Muted, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BaseMap.values().filter { it != BaseMap.MAPTILER || Config.MAPTILER_KEY.isNotBlank() }.forEach { b ->
                FilterChip(selected = vm.baseMap == b, onClick = { vm.baseMap = b }, label = { Text(b.label, fontSize = 13.sp) })
            }
        }

        LazyColumn(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                // Filter and legend in one row: the colored dot is the legend for the map dots.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = vm.filter == null, onClick = { vm.filter = null },
                        label = { Text("All ${vm.assessments.size}", fontSize = 13.sp) })
                    Status.values().forEach { st ->
                        FilterChip(
                            selected = vm.filter == st,
                            onClick = { vm.filter = if (vm.filter == st) null else st },
                            label = { Text(st.label, fontSize = 13.sp) },
                            leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(st.dot())) }
                        )
                    }
                }
            }
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
                MgButton(Icons.Outlined.PictureAsPdf, "Save Log to PDF", "Timestamp, status, action") {
                    val ok = PdfExporter.save(ctx, vm.assessments)
                    Toast.makeText(ctx, if (ok) "PDF saved to Downloads" else "Could not save PDF", Toast.LENGTH_SHORT).show()
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
                    Thumb(a, 52.dp, Modifier.clickable { viewing = a })     // tap the photo to see the tiles
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
                        Box(Modifier.padding(vertical = 2.dp)) { StatusPill(a.status) }
                        Text(a.timeMillis.fmt(), fontSize = 12.sp, color = Mg.Muted)
                        Text("GPS ${a.gpsText}", fontSize = 11.5.sp, color = Mg.Muted)
                        Text(a.tilesText, fontSize = 11.5.sp, color = Mg.Muted)
                        a.note?.let { Text(it, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Mg.AmberText) }
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
        a.note?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Mg.AmberText, modifier = Modifier.padding(top = 2.dp)) }
        Box(Modifier.padding(top = 8.dp)) { SmallButton(Icons.Outlined.GridView, "View tiles", onTiles) }
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

/** The photo with its 512 px tiles drawn on top: green = Healthy, red = Unhealthy, clear = Non-mangrove. */
@Composable
private fun TileViewDialog(a: Assessment, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val photo = remember(a.thumb) {
        if (a.thumb.isEmpty()) null else BitmapFactory.decodeFile(Thumbs.file(ctx, a.thumb).path)?.asImageBitmap()
    }
    val rows = remember(a.grid) { a.grid.split("/").filter { it.isNotEmpty() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(a.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (photo != null && rows.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().aspectRatio(photo.width / photo.height.toFloat())) {
                        Image(photo, "Photo with tiles", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                        Canvas(Modifier.fillMaxSize()) {
                            val cw = size.width / rows.maxOf { it.length }
                            val ch = size.height / rows.size
                            rows.forEachIndexed { r, line ->
                                line.forEachIndexed { c, t ->
                                    val col = when (t) { 'H' -> Mg.Green; 'U' -> Mg.Red; else -> null }
                                    if (col != null) drawRect(col.copy(alpha = 0.5f), Offset(c * cw, r * ch), Size(cw, ch))
                                    drawRect(Color.White.copy(alpha = 0.7f), Offset(c * cw, r * ch), Size(cw, ch), style = Stroke(1.5f))
                                }
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
                a.note?.let { Text(it, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Mg.AmberText) }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } }
    )
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