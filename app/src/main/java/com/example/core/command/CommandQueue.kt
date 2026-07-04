package com.example.core.command

import kotlinx.coroutines.flow.StateFlow

/**
 * Supported transactional mutation commands on the Human platform.
 */
sealed class PlatformCommand {
    abstract val commandId: String
    abstract val timestamp: Long
    abstract val humanUserId: String

    data class WorkoutCreated(
        override val commandId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val sessionId: Int,
        val templateId: Int?,
        val templateName: String?,
        val startTime: Long
    ) : PlatformCommand()

    data class WorkoutUpdated(
        override val commandId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val sessionId: Int,
        val endTime: Long?
    ) : PlatformCommand()

    data class WorkoutDeleted(
        override val commandId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val sessionId: Int
    ) : PlatformCommand()

    data class SetCompleted(
        override val commandId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val setId: Int,
        val reps: Int,
        val weight: Float,
        val rpe: Int?
    ) : PlatformCommand()

    data class MeasurementLogged(
        override val commandId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val measurementId: Int,
        val type: String, // "BodyWeight" or "Tape"
        val value: Double
    ) : PlatformCommand()

    data class TemplateUpdated(
        override val commandId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val templateId: Int,
        val name: String
    ) : PlatformCommand()
}

/**
 * Represents the execution state of a queued command.
 */
enum class CommandExecutionState {
    QUEUED,
    EXECUTING,
    COMPLETED,
    FAILED_RETRYABLE,
    FAILED_FATAL
}

/**
 * Wrap metadata around a command item stored locally.
 */
data class QueuedCommandEnvelope(
    val id: String,
    val command: PlatformCommand,
    val enqueuedAt: Long,
    val retryCount: Int,
    val state: CommandExecutionState,
    val lastError: String?
)

/**
 * Interface for the Platform Command Queue.
 * Commands execute locally immediately (to provide responsive UI updates) and sync asynchronously.
 */
interface CommandQueue {
    val queuedCommands: StateFlow<List<QueuedCommandEnvelope>>
    val isProcessing: StateFlow<Boolean>

    /**
     * Enqueues a command to mutate local state immediately and schedule background cloud synchronization.
     */
    suspend fun enqueue(command: PlatformCommand): Result<Unit>

    /**
     * Attempts to dispatch the oldest pending commands from the queue to the cloud.
     */
    suspend fun processNextCommand(): Result<Unit>

    /**
     * Drops a fatally failing command or marks it for manual review to prevent queue blocking.
     */
    suspend fun discardCommand(commandId: String)
}
