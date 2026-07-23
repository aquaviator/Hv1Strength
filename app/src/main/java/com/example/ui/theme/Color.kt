package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Human V1 Platform Brand Design System - Color Tokens
// ============================================================================

// Brand Core Accent
val HumanElectricBlue = Color(0xFF0066FF)          // Electric royal blue from SVG master & banner
val HumanElectricBluePressed = Color(0xFF0052D4)   // Pressed / Active state
val HumanElectricBlueMuted = Color(0xFF003399)     // Secondary subtle state
val HumanElectricBlueContainer = Color(0xFF1A3B70) // Dark container accent background

// Dark Theme Palette - Deep Charcoal & Graphite Canvas
val HumanDarkBackground = Color(0xFF0A0D10)         // Near-black graphite canvas (from SVG master #0A0D10)
val HumanDarkSurface = Color(0xFF121519)            // Elevated card surface container
val HumanDarkSurfaceElevated = Color(0xFF1C2026)    // Lighter interactive container
val HumanDarkOnBackground = Color(0xFFF4F5F7)       // Primary crisp text
val HumanDarkOnSurface = Color(0xFFF4F5F7)          // Surface card text
val HumanDarkOnSurfaceVariant = Color(0xFF8E95A0)   // Muted slate gray secondary text
val HumanDarkOutline = Color(0xFF262A30)            // Subtle graphite border outline

// Light Theme Palette - Minimal Cool Neutral Canvas
val HumanLightBackground = Color(0xFFF4F6F8)        // Off-white neutral background
val HumanLightSurface = Color(0xFFFFFFFF)           // Pure white card background
val HumanLightSurfaceElevated = Color(0xFFEAEFF5)   // Segmented control / textfield surface
val HumanLightOnBackground = Color(0xFF0F141A)      // Deep graphite text
val HumanLightOnSurface = Color(0xFF0F141A)         // Card text
val HumanLightOnSurfaceVariant = Color(0xFF5A6573)  // Muted slate gray text
val HumanLightOutline = Color(0xFFD5DDE5)           // Soft gray border outline

// System Semantic States
val HumanSuccess = Color(0xFF10B981)               // Controlled emerald green
val HumanSuccessDim = Color(0xFF064E3B)
val HumanWarning = Color(0xFFF59E0B)               // Amber caution
val HumanError = Color(0xFFEF4444)                 // Accessible red
val HumanErrorContainer = Color(0xFF450A0A)

// Backward compatibility / legacy alias tokens
val AlertRed = HumanError
val AlertRedDim = Color(0xFF7F1D1D)
val SuccessGreen = HumanSuccess
val SuccessGreenDim = HumanSuccessDim
val KineticAccent = Color(0xFF00E5FF)               // Legacy cyan token (preserved)
val HumanPrimaryAccent = HumanElectricBlue
val HumanDarkSurfaceVariant = HumanDarkSurfaceElevated
val HumanElectricBlueDark = HumanElectricBlueMuted
val HumanElectricBlueLight = Color(0xFFE0EBFF)
val SlateBackground = HumanDarkBackground
val SlateElevatedSurface = HumanDarkSurfaceElevated
val SlateBorderColor = HumanDarkOutline
val SlateMutedText = HumanDarkOnSurfaceVariant
val SlateSuccess = HumanSuccess


