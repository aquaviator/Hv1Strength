package com.example.core.identity

import kotlinx.coroutines.flow.StateFlow

/**
 * Supported Authentication Providers for the Human Platform.
 */
enum class AuthProviderType {
    GOOGLE,
    APPLE,
    EMAIL,
    GUEST,
    ENTERPRISE
}

/**
 * Unified Profile details for a Human Platform account.
 */
data class HumanProfile(
    val id: String, // Prefix: human_xxxxxxxxx
    val name: String?,
    val email: String?,
    val avatarUrl: String?,
    val heightCm: Float?,
    val isMetric: Boolean,
    val goals: List<String>,
    val experienceLevel: String, // e.g., "Beginner", "Intermediate", "Advanced"
    val preferences: Map<String, String>
)

/**
 * Represeents a specific authentication provider linked to a Human ID.
 */
data class LinkedProvider(
    val providerId: String, // ID inside the provider system (e.g., Firebase UID, Apple ID)
    val providerType: AuthProviderType,
    val linkedAt: Long
)

/**
 * State of the Platform User Identity.
 */
sealed interface HumanIdentityState {
    object Unauthenticated : HumanIdentityState
    data class Authenticated(
        val profile: HumanProfile,
        val activeToken: String,
        val linkedProviders: List<LinkedProvider>
    ) : HumanIdentityState
    object OfflineMode : HumanIdentityState
}

/**
 * Core contract for managing Platform Identity, provider linking, and session context.
 */
interface HumanIdentityManager {
    val currentIdentity: StateFlow<HumanIdentityState>

    /**
     * Resolves the current primary Human User ID, returning null if unauthenticated.
     */
    fun getHumanUserId(): String?

    /**
     * Link an authentication provider (e.g. Google, Apple) to the active Human profile.
     */
    suspend fun linkProvider(providerId: String, token: String, type: AuthProviderType): Result<Unit>

    /**
     * Unlinks an authentication provider from the active Human profile.
     */
    suspend fun unlinkProvider(type: AuthProviderType): Result<Unit>

    /**
     * Synchronizes and updates the local user profile metrics and preferences.
     */
    suspend fun updateProfile(updatedProfile: HumanProfile): Result<Unit>
}
