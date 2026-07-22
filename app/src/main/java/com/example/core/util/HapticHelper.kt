package com.example.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

val LocalVibrationEnabled = staticCompositionLocalOf { true }

/**
 * Shared central decision point for application-controlled haptics.
 * Suppresses all haptic feedback when user disables vibration in settings.
 */
fun HapticFeedback?.performIfEnabled(isEnabled: Boolean, type: HapticFeedbackType = HapticFeedbackType.LongPress) {
    if (isEnabled && this != null) {
        try {
            this.performHapticFeedback(type)
        } catch (_: Throwable) {
            // Safe fallback if device haptics are unsupported
        }
    }
}

class AppHapticFeedback(
    private val hapticFeedback: HapticFeedback,
    val isEnabled: Boolean
) {
    fun perform(type: HapticFeedbackType = HapticFeedbackType.LongPress) {
        if (isEnabled) {
            try {
                hapticFeedback.performHapticFeedback(type)
            } catch (_: Throwable) {}
        }
    }
}

@Composable
fun rememberAppHapticFeedback(vibrationEnabled: Boolean = LocalVibrationEnabled.current): AppHapticFeedback {
    val haptic = LocalHapticFeedback.current
    return androidx.compose.runtime.remember(haptic, vibrationEnabled) {
        AppHapticFeedback(haptic, vibrationEnabled)
    }
}
