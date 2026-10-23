package com.example.mangrove.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.example.mangrove.data.Config
import com.example.mangrove.data.Status

/** Colors copied from mangrove_mockup_v2.html */
object Mg {
    val Cream = Color(0xFFF2EFE6); val Ink = Color(0xFF1C2B24)
    val Forest = Color(0xFF173D30); val Forest2 = Color(0xFF1F5340)
    val Green = Color(0xFF4A7C59); val GreenSoft = Color(0xFFDCEAD9); val GreenText = Color(0xFF2C5A3A)
    val Amber = Color(0xFFC98A3C); val AmberSoft = Color(0xFFF6E6CC); val AmberText = Color(0xFF8A5A1E)
    val Red = Color(0xFFB8493A); val RedSoft = Color(0xFFF5DDD7); val RedText = Color(0xFF8A3325)
    val Line = Color(0xFFDCD6C6); val Muted = Color(0xFF71695A)
    val Grey = Color(0xFF8C9AA5); val GreySoft = Color(0xFFE6EAED); val GreyText = Color(0xFF4F5B63)
}

fun Status.dot() = when (this) { Status.HEALTHY -> Mg.Green; Status.UNHEALTHY -> Mg.Red; Status.NON_MANGROVE -> Mg.Grey; Status.OUT_OF_REACH -> Mg.Amber }
fun Status.soft() = when (this) { Status.HEALTHY -> Mg.GreenSoft; Status.UNHEALTHY -> Mg.RedSoft; Status.NON_MANGROVE -> Mg.GreySoft; Status.OUT_OF_REACH -> Mg.AmberSoft }
fun Status.text() = when (this) { Status.HEALTHY -> Mg.GreenText; Status.UNHEALTHY -> Mg.RedText; Status.NON_MANGROVE -> Mg.GreyText; Status.OUT_OF_REACH -> Mg.AmberText }

@Composable
fun MangroveTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(primary = Mg.Forest2, background = Mg.Cream, surface = Color.White, onBackground = Mg.Ink),
    content = content
)

@Composable
fun StatusPill(s: Status) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(s.soft()).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(s.dot()))
        Spacer(Modifier.width(5.dp))
        Text(s.label, color = s.text(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MgButton(
    icon: ImageVector?, title: String, sub: String? = null, primary: Boolean = false,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (primary) Mg.Forest2 else Color.White,
        contentColor = if (primary) Mg.Cream else Mg.Ink,
        border = if (primary) null else BorderStroke(1.dp, Mg.Line),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(10.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                DottedIcon(icon, 38.dp, tint = if (primary) Mg.Cream else Mg.Forest2)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (sub != null) Text(sub, fontSize = 12.5.sp, color = LocalContentColor.current.copy(0.75f))
            }
        }
    }
}

/** Small, simple pill button (used for the QGIS layer buttons). */
@Composable
fun SmallButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(32.dp),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        border = BorderStroke(1.dp, Mg.Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Mg.Forest2)
    ) {
        Icon(icon, null, Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Line icon inside a dotted ring (replaces the emojis). */
@Composable
fun DottedIcon(icon: ImageVector, size: Dp = 38.dp, tint: Color = Mg.Forest2) {
    Box(
        Modifier.size(size).drawBehind {
            drawCircle(tint.copy(alpha = 0.8f), style = Stroke(width = 4f, cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 11f))))
        },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
fun LocationLine(color: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.LocationOn, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(Config.SITE_NAME, fontSize = 14.sp, color = color)
    }
}

@Composable
fun SectionLabel(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Mg.Forest2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Mg.Forest2, letterSpacing = 0.6.sp)
    }
}