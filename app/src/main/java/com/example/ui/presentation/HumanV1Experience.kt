package com.example.ui.presentation

import com.example.billing.AppAccessState
import com.example.data.AuthState

enum class ExperienceTone { NEUTRAL, POSITIVE, ATTENTION, BLOCKED }

data class ExperienceStatus(
    val title: String,
    val detail: String,
    val tone: ExperienceTone,
    val actionLabel: String? = null
)

fun authenticationPresentation(state: AuthState): ExperienceStatus = when (state) {
    AuthState.Initial -> ExperienceStatus("Preparing your profile", "Loading local training data.", ExperienceTone.NEUTRAL)
    AuthState.Loading -> ExperienceStatus("Verifying your account", "Resolving your secure Human V1 account.", ExperienceTone.NEUTRAL)
    is AuthState.LegacyUpgradeRequired -> ExperienceStatus("Local data update ready", "Your account is verified. Local ownership must be updated before cloud sync.", ExperienceTone.ATTENTION, "Review update")
    is AuthState.LegacyUpgradeRunning -> ExperienceStatus("Updating local data", state.stage, ExperienceTone.NEUTRAL)
    is AuthState.LegacyUpgradeHandoffRequired -> ExperienceStatus("Finish account setup", state.message, ExperienceTone.ATTENTION, "Try again")
    AuthState.Offline -> ExperienceStatus("Training offline", "Workouts are saved on this device. Cloud sync is unavailable.", ExperienceTone.ATTENTION, "Connect account")
    is AuthState.ProtectedLocal -> ExperienceStatus("Protected local profile", "This profile remains local and is not connected to the signed-in account.", ExperienceTone.ATTENTION, "Account settings")
    is AuthState.Authenticated -> ExperienceStatus("Human V1 account ready", "Your trusted account is verified and cloud features are available.", ExperienceTone.POSITIVE)
    is AuthState.Error -> if (state.message.contains("conflict", true) || state.message.contains("binding", true)) {
        ExperienceStatus("Account needs attention", "This account cannot safely use the current cloud identity. Local training remains available.", ExperienceTone.BLOCKED, "Review account")
    } else {
        ExperienceStatus("Account verification unavailable", "Local training remains available. Retry when your connection is stable.", ExperienceTone.ATTENTION, "Retry")
    }
}

fun membershipStatus(state: AppAccessState): ExperienceStatus = when (state) {
    AppAccessState.Initializing -> ExperienceStatus("Checking membership", "Verifying access without estimating trial dates.", ExperienceTone.NEUTRAL)
    is AppAccessState.TrialActive -> ExperienceStatus("Human V1 trial active", "${state.daysRemaining} days remaining.", ExperienceTone.POSITIVE)
    is AppAccessState.Subscribed -> ExperienceStatus("Human Strength Annual", "Subscription verified.", ExperienceTone.POSITIVE)
    is AppAccessState.SubscriptionActiveUntilExpiry -> ExperienceStatus("Human Strength Annual", "Active until the current subscription period ends.", ExperienceTone.POSITIVE)
    is AppAccessState.SupportAccessActive -> ExperienceStatus("Human Strength internal testing", "Temporary support access is active; introductory access remains expired.", ExperienceTone.POSITIVE)
    AppAccessState.GracePeriod -> ExperienceStatus("Membership needs attention", "Access continues while Google Play resolves payment.", ExperienceTone.ATTENTION, "Manage subscription")
    AppAccessState.PaymentPending -> ExperienceStatus("Verification pending", "A purchase is processing; subscribed access is not yet confirmed.", ExperienceTone.ATTENTION, "Restore purchases")
    is AppAccessState.Expired -> ExperienceStatus("Access expired", "Choose a Human Strength membership to continue cloud-supported access.", ExperienceTone.BLOCKED, "View membership")
    AppAccessState.Unentitled -> ExperienceStatus("Membership required", "Human Strength Annual is available through Google Play.", ExperienceTone.ATTENTION, "View membership")
    AppAccessState.VerificationUnavailable -> ExperienceStatus("Membership unavailable", "Reconnect to verify access. No trial has been manufactured locally.", ExperienceTone.ATTENTION, "Retry")
    is AppAccessState.Error -> ExperienceStatus("Membership needs attention", "Access could not be verified safely.", ExperienceTone.BLOCKED, "Retry")
}

fun syncPresentation(auth: AuthState, status: String, pending: Int, lastError: String?): ExperienceStatus {
    if (auth is AuthState.Offline) return ExperienceStatus("Saved on this phone", "This local profile is not connected to an account.", ExperienceTone.NEUTRAL)
    if (auth is AuthState.ProtectedLocal) return ExperienceStatus("Saved on this phone", "Synchronization is disabled because this profile belongs to a different account.", ExperienceTone.ATTENTION)
    if (auth is AuthState.Loading || auth is AuthState.Initial) return ExperienceStatus("Saved on this phone", "Preparing secure synchronization.", ExperienceTone.NEUTRAL)
    if (auth is AuthState.Error) return ExperienceStatus("Saved on this phone", "Synchronization is paused until the account is safe.", ExperienceTone.ATTENTION, "Review account")
    if (status == "ItemsNeedReview") return ExperienceStatus("Some items need review", "Conflicting items remain protected; other changes can continue synchronizing.", ExperienceTone.ATTENTION, "Review items")
    if (status == "StudioPlanNeedsAttention") return ExperienceStatus("Studio plan needs attention", "One Studio plan needs attention because a referenced workout is unavailable.", ExperienceTone.ATTENTION, "Review plan")
    if (status == "StudioPlanDependencyPending") return ExperienceStatus("Studio plan waiting", "A referenced Studio workout is still arriving. Other changes can continue synchronizing.", ExperienceTone.NEUTRAL)
    if (status == "WaitingForIdentity") return ExperienceStatus("Saved on this phone", "Preparing secure synchronization.", ExperienceTone.NEUTRAL)
    if (status == "WaitingForConnection") return ExperienceStatus("Saved on this phone", "We’ll synchronize automatically when you’re connected.", ExperienceTone.ATTENTION)
    if (status == "Synchronizing" || status.contains("syncing", true) || status.contains("upload", true) || status.contains("download", true)) return ExperienceStatus("Synchronizing", "Your changes are already saved on this phone.", ExperienceTone.NEUTRAL)
    if (status == "SavedRetrying" || !lastError.isNullOrBlank()) return ExperienceStatus("Saved on this phone", "Online synchronization is temporarily unavailable. We’ll try again automatically.", ExperienceTone.ATTENTION)
    if (pending > 0) return ExperienceStatus("Saved on this phone", "$pending local change${if (pending == 1) " is" else "s are"} waiting for automatic synchronization.", ExperienceTone.ATTENTION)
    return ExperienceStatus("Synced", "Your Human V1 data is up to date.", ExperienceTone.POSITIVE)
}

fun cataloguePresentation(version: String, governedCount: Int, customCount: Int, valid: Boolean, fallback: Boolean): ExperienceStatus = when {
    fallback -> ExperienceStatus("Core exercise library limited", "$governedCount core and $customCount custom exercises available while the library recovers.", ExperienceTone.ATTENTION)
    !valid -> ExperienceStatus("Exercise library unavailable", "Validation did not complete safely. Custom exercises remain preserved.", ExperienceTone.BLOCKED)
    else -> ExperienceStatus("Exercise library ready", "Version $version · $governedCount core · $customCount custom.", ExperienceTone.POSITIVE)
}

fun backgroundPresentation(permissionGranted: Boolean, workoutActive: Boolean): ExperienceStatus = when {
    !permissionGranted -> ExperienceStatus("Background visibility limited", "Workouts still save locally, but timers may not remain visible. Recovery is available when the app reopens.", ExperienceTone.ATTENTION, "Open notification settings")
    workoutActive -> ExperienceStatus("Workout protected in background", "The active service and notification are running.", ExperienceTone.POSITIVE)
    else -> ExperienceStatus("Background protection available", "It starts automatically with an active workout. Force-stop and reboot auto-resume are not supported.", ExperienceTone.POSITIVE)
}

fun recoveryOwnershipAllowed(ownerUserId: String, currentUserId: String): Boolean =
    ownerUserId == "offline" || ownerUserId == currentUserId

fun advancedDiagnosticsVisible(debugBuild: Boolean): Boolean = debugBuild

fun startupProgressMessage(auth: AuthState, access: AppAccessState): String = when {
    auth is AuthState.Initial -> "Loading your local training profile"
    auth is AuthState.Loading -> "Verifying your trusted Human V1 account"
    access is AppAccessState.Initializing -> "Checking membership and saved workouts"
    else -> "Preparing Human Strength"
}
