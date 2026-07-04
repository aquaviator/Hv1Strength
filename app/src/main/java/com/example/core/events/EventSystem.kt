package com.example.core.events

import kotlinx.coroutines.flow.SharedFlow

/**
 * Universal event types published across the Human Platform application.
 */
sealed class HumanPlatformEvent {
    abstract val eventId: String
    abstract val timestamp: Long
    abstract val humanUserId: String

    data class WorkoutStarted(
        override val eventId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val sessionId: Int,
        val templateId: Int?
    ) : HumanPlatformEvent()

    data class WorkoutFinished(
        override val eventId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val sessionId: Int,
        val totalVolume: Double,
        val durationSeconds: Long
    ) : HumanPlatformEvent()

    data class SetCompleted(
        override val eventId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val exerciseId: String,
        val weight: Float,
        val reps: Int
    ) : HumanPlatformEvent()

    data class BodyWeightLogged(
        override val eventId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val weightKg: Float
    ) : HumanPlatformEvent()

    data class WorkoutTemplateCreated(
        override val eventId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val templateId: Int,
        val templateName: String
    ) : HumanPlatformEvent()

    data class SubscriptionPurchased(
        override val eventId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val productSku: String,
        val purchaseToken: String,
        val expiryDate: Long
    ) : HumanPlatformEvent()

    data class ProfileUpdated(
        override val eventId: String,
        override val timestamp: Long,
        override val humanUserId: String,
        val changedFields: List<String>
    ) : HumanPlatformEvent()
}

/**
 * Subscriber interface for handling events reactively.
 */
interface HumanEventSubscriber {
    fun onEvent(event: HumanPlatformEvent)
}

/**
 * Core event dispatcher for routing real-time domain events to Analytics, Notifications, achievements, and Coach modules.
 */
interface HumanEventBus {
    val events: SharedFlow<HumanPlatformEvent>

    /**
     * Publishes an event to the global stream.
     */
    suspend fun publish(event: HumanPlatformEvent)

    /**
     * Dynamically registers a runtime subscriber.
     */
    fun registerSubscriber(subscriber: HumanEventSubscriber)

    /**
     * Unregisters a runtime subscriber.
     */
    fun unregisterSubscriber(subscriber: HumanEventSubscriber)
}
