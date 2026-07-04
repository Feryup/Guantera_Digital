package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Light Theme Colors
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightTextPrimary = Color(0xFF111827)
val LightTextSecondary = Color(0xFF6B7280)
val LightBorders = Color(0xFFE5E7EB)
val LightIconDefault = Color(0xFF374151)
val LightButtonPrimary = Color(0xFF111827)
val LightNavActive = Color(0xFF111827)
val LightNavInactive = Color(0xFF9CA3AF)

// Dark Theme Colors
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkTextPrimary = Color(0xFFF1F5F9)
val DarkTextSecondary = Color(0xFF94A3B8)
val DarkBorders = Color(0x10FFFFFF) // 6% white opacity
val DarkIconDefault = Color(0xFFCBD5E1)
val DarkButtonPrimary = Color(0xFF60A5FA)
val DarkNavActive = Color(0xFFF1F5F9)
val DarkNavInactive = Color(0xFF64748B)

// Cards destacadas Gradients
val LightFeaturedGradientStart = Color(0xFF1A1A2E)
val LightFeaturedGradientEnd = Color(0xFF16213E)

val DarkFeaturedGradientStart = Color(0xFF1A2550)
val DarkFeaturedGradientEnd = Color(0xFF0A1230)

// Semantic Badge Colors (Same for Light and Dark)
val StateVigenteDot = Color(0xFF22C55E)
val StateVigenteBg = Color(0x2622C55E) // 15% opacity

val StatePorVencerDot = Color(0xFFF59E0B)
val StatePorVencerBg = Color(0x26F59E0B) // 15% opacity

val StateVencidoDot = Color(0xFFEF4444)
val StateVencidoBg = Color(0x26EF4444) // 15% opacity

// Backward Compatibility Color Aliases for legacy views and wizard components
val ElectricCyan = Color(0xFF60A5FA)
val NeonGreen = StateVigenteDot
val CarbonAccent = Color(0xFF1E293B)
val LightCarbonAccent = Color(0xFFF1F5F9)
val CrimsonRed = StateVencidoDot
val LightCrimsonRed = StateVencidoDot
val SoftOrange = StatePorVencerDot
val TextPrimary = DarkTextPrimary
val TextSecondary = DarkTextSecondary
val AvatarBg = Color(0xFF1E293B)
val DarkPurpleText = Color(0xFF111827)
val MutedPurpleAccent = Color(0xFF64748B)
