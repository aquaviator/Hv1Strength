package com.example.core.ai

import kotlinx.coroutines.flow.Flow

/**
 * Message Role for Chat History within the AI Coach context.
 */
enum class ChatRole {
    USER,
    MODEL,
    SYSTEM
}

/**
 * Single historical chat message in the Human Coach module.
 */
data class CoachChatMessage(
    val messageId: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long
)

/**
 * Sealed class representing structured outcomes from the AI Gateway.
 */
sealed class StructuredCoachResponse {
    data class TextResponse(val text: String) : StructuredCoachResponse()
    
    data class WorkoutProgramProposal(
        val title: String,
        val description: String,
        val proposedExercises: List<ProposedExercise>,
        val coachingRationale: String
    ) : StructuredCoachResponse() {
        data class ProposedExercise(
            val exerciseId: String,
            val exerciseName: String,
            val setsCount: Int,
            val repsRange: String,
            val estimatedWeightKg: Float?
        )
    }

    data class ProgressionSuggestion(
        val originalExerciseId: String,
        val currentPerformance: String,
        val recommendedWeightAdjustment: Float,
        val recommendedRepsAdjustment: Int,
        val rationale: String
    ) : StructuredCoachResponse()

    data class ErrorResponse(val errorMessage: String, val throwable: Throwable?) : StructuredCoachResponse()
}

/**
 * AI Coach integration gateway.
 * Insulates front-end code from raw API endpoints and ensures replacement models can be integrated without rebuilding the presentation layer.
 */
interface AiGateway {
    
    /**
     * Sends a chat prompt with standard text response.
     */
    fun chatWithCoach(
        humanUserId: String,
        history: List<CoachChatMessage>,
        newPrompt: String
    ): Flow<StructuredCoachResponse>

    /**
     * Generates a fully structured workout program recommendation based on the user's historical volume.
     */
    suspend fun generateProgram(
        humanUserId: String,
        historicalContextJson: String,
        userGoals: List<String>
    ): StructuredCoachResponse

    /**
     * Requests progression suggestions for the user's logged lifts.
     */
    suspend fun analyzeProgression(
        humanUserId: String,
        exerciseId: String,
        performanceHistoryJson: String
    ): StructuredCoachResponse
}
