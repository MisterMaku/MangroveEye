package com.example.mangrove.theme

import androidx.compose.ui.graphics.Color

/**
 * MangroveEye Color Theme
 * A refined palette designed for mangrove forest assessment with nature-inspired colors
 */
object Mg {
    // ============ PRIMARY BRANDING COLORS ============
    val Mangrove = Color(0xFF2D5A27)          // Deep green - main brand color
    val MangroveLight = Color(0xFF4A7A44)     // Lighter green - accents
    val MangroveSoft = Color(0xFFE8F5E9)      // Very light green - backgrounds

    // ============ FOREST & NATURE TONES ============
    val Forest = Mangrove                     // Splash gradient end
    val Forest1 = Color(0xFF4CAF50)           // Vibrant green
    val Forest2 = Color(0xFF81C784)           // Soft green - icons on cards
    val Forest3 = Color(0xFFA5D6A7)           // Pale green

    // ============ HEALTH STATUS COLORS ============
    val Healthy = Color(0xFF4CAF50)           // Green - healthy mangrove
    val Unhealthy = Color(0xFFE57373)         // Red - unhealthy vegetation
    val NonMangrove = Color(0xFF9E9E9E)       // Grey - water, mud, land

    // ============ TEXT COLORS ============
    val Ink = Color(0xFF212121)              // Primary text (almost black)
    val InkMuted = Color(0xFF616161)         // Secondary text (grey)
    val Inktone = Color(0xFF757575)           // Tertiary text (light grey)

    // ============ UI ACCENT COLORS ============
    val Amber = Color(0xFFFFCA28)            // Amber for notifications

    // ============ BACKGROUND & SURFACE COLORS ============
    val Background = Color(0xFFFAFFF8)        // Off-white cream background
    val CardBackground = Color(0xFFFFFFFF)    // White card backgrounds

    // ============ SHADOW & DIVIDER ============
    val Divider = Color(0xFFE0E0E0)          // Light divider lines
    val LightDivider = Color(0xFFF5F5F5)     // Very light dividers
    val Line = Color(0xFFDCD6C6)             // Card / button outline

    // ============ UTILITY COMBINATIONS ============
    val GreenText = Healthy
    val GreenSoft = MangroveSoft
    val RedText = Unhealthy
    val AmberText = Amber
    val GreyText = Inktone

    // Compatibility aliases (kept because the UI references them by these names)
    val Cream = Background
    val Green = Forest1
    val AmberSoft = Color(0xFFF6E6CC)
    val RedSoft = Color(0xFFF5DDD7)
    val Red = Unhealthy
    val Muted = InkMuted
    val Grey = Inktone
    val GreySoft = Inktone.copy(alpha = 0.15f)
}
