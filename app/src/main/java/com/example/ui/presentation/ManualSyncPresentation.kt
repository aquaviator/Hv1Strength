package com.example.ui.presentation

import com.example.core.sync.ManualSyncResult
import java.text.DateFormat
import java.util.Date

fun manualSyncDetail(result: ManualSyncResult, now: Long = System.currentTimeMillis()): String = when (result.phase) {
    "QUEUED" -> "Check queued…"
    "CHECKING" -> "Checking…"
    "UPDATED" -> "Updated — ${result.downloaded} downloaded, ${result.uploaded} uploaded · Checked ${formatSyncTime(result.completedAt, now)}"
    "UP_TO_DATE" -> "Up to date · Checked ${formatSyncTime(result.completedAt, now)}"
    "ATTENTION" -> "Some items need attention · Checked ${formatSyncTime(result.completedAt, now)}"
    "OFFLINE" -> "Offline · Last checked ${formatSyncTime(result.lastSuccessfulCompletedAt, now)}"
    "FAILED" -> "Synchronization failed — ${result.reason ?: "try again"} · Last successful check ${formatSyncTime(result.lastSuccessfulCompletedAt, now)}"
    else -> "Never checked"
}

fun formatSyncTime(timestamp: Long?, now: Long = System.currentTimeMillis(), formatter: (Date) -> String = {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)
}): String {
    if (timestamp == null || timestamp <= 0L) return "never"
    val delta = now - timestamp
    if (delta < 0L) return formatter(Date(timestamp))
    val seconds = delta / 1000L
    return when {
        seconds < 10 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        else -> formatter(Date(timestamp))
    }
}
