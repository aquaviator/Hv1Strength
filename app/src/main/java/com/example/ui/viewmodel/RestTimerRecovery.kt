package com.example.ui.viewmodel

internal data class RestTimerRecovery(
    val durationSeconds: Int,
    val remainingSeconds: Int,
    val paused: Boolean,
    val endTimestamp: Long
)

internal fun recoverRestTimer(
    durationSeconds: Int?,
    remainingAtSaveSeconds: Int?,
    endTimestamp: Long?,
    paused: Boolean,
    nowMillis: Long
): RestTimerRecovery? {
    val duration = durationSeconds?.takeIf { it > 0 }
    if (paused) {
        val remaining = remainingAtSaveSeconds?.coerceAtLeast(0)?.takeIf { it > 0 } ?: return null
        return RestTimerRecovery(
            durationSeconds = maxOf(duration ?: remaining, remaining),
            remainingSeconds = remaining,
            paused = true,
            endTimestamp = nowMillis + remaining * 1_000L
        )
    }
    val end = endTimestamp?.takeIf { it > 0L } ?: return null
    val remainingMillis = end - nowMillis
    if (remainingMillis <= 0L) return null
    val remaining = ((remainingMillis + 999L) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return RestTimerRecovery(
        durationSeconds = maxOf(duration ?: remaining, remaining),
        remainingSeconds = remaining,
        paused = false,
        endTimestamp = end
    )
}
