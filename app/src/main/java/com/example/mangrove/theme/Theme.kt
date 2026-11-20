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
import androidx.compose.ui.draw.shadow
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
import com.example.mangrove.theme.Mg

@Composable
fun MangroveTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = Mg.MangroveLight,
        onPrimary = Mg.MangroveSoft,
        primaryContainer = Mg.MangroveSoft,
        onPrimaryContainer = Mg.Mangrove,
        background = Mg.Background,
        onBackground = Mg.Ink,
        surface = Mg.CardBackground,
        onSurface = Mg.Ink,
        surfaceVariant = Mg.MangroveSoft,
        onSurfaceVariant = Mg.InkMuted,
        outline = Mg.Line
    ),
    typography = Typography(),
    content = content
)

@Composable
fun StatusPill(s: Status) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(
            if (s == Status.HEALTHY) Mg.GreenSoft.copy(alpha = 0.9f)
            else if (s == Status.UNHEALTHY) Mg.RedSoft.copy(alpha = 0.9f)
            else if (s == Status.NON_MANGROVE) Mg.GreySoft.copy(alpha = 0.9f)
            else Mg.AmberSoft.copy(alpha = 0.9f)
        ).padding(horizontal = 12.dp, vertical = 4.dp).shadow(1.dp, RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(
            if (s == Status.HEALTHY) Mg.Healthy
            else if (s == Status.UNHEALTHY) Mg.Unhealthy
            else if (s == Status.NON_MANGROVE) Mg.NonMangrove
            else Mg.Amber
        ))
        Spacer(Modifier.width(6.dp))
        Text(
            s.label,
            color = if (s == Status.HEALTHY) Mg.GreenText
                    else if (s == Status.UNHEALTHY) Mg.RedText
                    else if (s == Status.NON_MANGROVE) Mg.GreyText
                    else Mg.AmberText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun MgButton(
    icon: ImageVector?, title: String, sub: String? = null, primary: Boolean = false,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .shadow(if (primary) 3.dp else 0.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = if (primary) Mg.MangroveLight else Mg.CardBackground,
        contentColor = if (primary) Mg.MangroveSoft else Mg.Ink,
        border = if (primary) null else BorderStroke(1.dp, Mg.Line),
        shadowElevation = if (primary) 4.dp else 0.dp
    ) {
        Row(
            Modifier.padding(16.dp, 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    null,
                    modifier = Modifier.size(28.dp),
                    tint = if (primary) Mg.MangroveSoft else Mg.Mangrove
                )
                Divider(
                    modifier = Modifier.width(1.dp).height(24.dp),
                    color = if (primary) Mg.MangroveSoft.copy(0.5f) else Mg.Line
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (sub != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        sub,
                        fontSize = 12.5.sp,
                        color = LocalContentColor.current.copy(0.7f),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Small, simple pill button (used for the QGIS layer buttons). */
@Composable
fun SmallButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = Mg.MangroveSoft.copy(alpha = 0.3f),
        contentColor = Mg.Mangrove,
        shadowElevation = 1.dp
    ) {
        Row(
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, Modifier.size(16.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
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
fun LocationLine(color: Color, modifier: Modifier = Modifier, text: String = Config.SITE_NAME) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.LocationOn, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 14.sp, color = color)
    }
}

@Composable
fun SectionLabel(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(icon, null, tint = Mg.MangroveLight, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = Mg.MangroveLight,
            letterSpacing = 0.5.sp
        )
    }
}

/** Small "N" north arrow in the top-left of the map, so the orientation is clear. */
@Composable
fun NorthArrow(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.85f))
            .shadow(2.dp, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("N", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Mg.Ink)
    }
}