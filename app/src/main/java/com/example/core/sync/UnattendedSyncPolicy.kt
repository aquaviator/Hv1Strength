package com.example.core.sync

import com.example.data.CommandQueueEntity

internal object UnattendedSyncPolicy {
    private val retryableStates = setOf("PENDING", "PROCESSING", "FAILED")

    fun isRetryable(command: CommandQueueEntity): Boolean =
        command.status in retryableStates && command.attempts < 5

    fun outstandingCount(commands: List<CommandQueueEntity>): Int = commands.count(::isRetryable)

    fun workerShouldRetry(syncSucceeded: Boolean, commands: List<CommandQueueEntity>): Boolean =
        !syncSucceeded || commands.any(::isRetryable)

    fun completionStatus(conflicts: Int, outstanding: Int): String = when {
        conflicts > 0 -> "ItemsNeedReview"
        outstanding > 0 -> "SavedRetrying"
        else -> "Synced"
    }

    fun shouldRequestForegroundSync(
        loggedIn: Boolean,
        provider: String?,
        profileHandoffComplete: Boolean
    ): Boolean = loggedIn && provider == "google" && profileHandoffComplete
}
