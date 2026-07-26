package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// Human V1 Platform Brand Design System - Color Tokens
// ============================================================================

// Brand Core Accent
val HumanElectricBlue = Color(0xFF0072FF)          // Primary Electric Blue (#0072FF)
val HumanSupportingBlue = Color(0xFF338BFF)        // Supporting Blue (#338BFF)
val HumanElectricBluePressed = Color(0xFF0052D4)   // Pressed / Active state
val HumanElectricBlueMuted = Color(0xFF003399)     // Secondary subtle state
val HumanElectricBlueContainer = Color(0xFF1A3B70) // Dark container accent background

// Dark Theme Palette - Deep Charcoal & Graphite Canvas
val HumanDarkBackground = Color(0xFF0A0D10)         // Near-black graphite canvas (#0A0D10)
val HumanDarkSurface = Color(0xFF12161A)            // Elevated card surface container (#12161A)
val HumanDarkSurfaceElevated = Color(0xFF1A2026)    // Lighter interactive container (#1A2026)
val HumanDarkOnBackground = Color(0xFFFFFFFF)       // Primary text (#FFFFFF)
val HumanDarkOnSurface = Color(0xFFFFFFFF)          // Surface card text
val HumanDarkOnSurfaceVariant = Color(0xFF94A3B8)   // Secondary text (#94A3B8)
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
val KineticAccent = HumanElectricBlue               // Brand primary accent (formerly cyan, now consolidated to electric blue)
val HumanPrimaryAccent = HumanElectricBlue
val HumanDarkSurfaceVariant = HumanDarkSurfaceElevated
val HumanElectricBlueDark = HumanElectricBlueMuted
val HumanElectricBlueLight = Color(0xFFE0EBFF)
val SlateBackground = HumanDarkBackground
val SlateElevatedSurface = HumanDarkSurfaceElevated
val SlateBorderColor = HumanDarkOutline
val SlateMutedText = HumanDarkOnSurfaceVariant
val SlateSuccess = HumanSuccess


