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
    AppAccessState.GracePeriod -> ExperienceStatus("Membership needs attention", "Access continues while Google Play resolves payment.", ExperienceTone.ATTENTION, "Manage subscription")
    AppAccessState.PaymentPending -> ExperienceStatus("Verification pending", "A purchase is processing; subscribed access is not yet confirmed.", ExperienceTone.ATTENTION, "Restore purchases")
    is AppAccessState.Expired -> ExperienceStatus("Access expired", "Choose a Human Strength membership to continue cloud-supported access.", ExperienceTone.BLOCKED, "View membership")
    AppAccessState.Unentitled -> ExperienceStatus("Membership required", "Human Strength Annual is available through Google Play.", ExperienceTone.ATTENTION, "View membership")
    AppAccessState.VerificationUnavailable -> ExperienceStatus("Membership unavailable", "Reconnect to verify access. No trial has been manufactured locally.", ExperienceTone.ATTENTION, "Retry")
    is AppAccessState.Error -> ExperienceStatus("Membership needs attention", "Access could not be verified safely.", ExperienceTone.BLOCKED, "Retry")
}

fun syncPresentation(auth: AuthState, status: String, pending: Int, lastError: String?): ExperienceStatus {
    if (auth is AuthState.Offline) return ExperienceStatus("Saved locally", "Connect an account to enable cloud sync.", ExperienceTone.NEUTRAL)
    if (auth is AuthState.Loading || auth is AuthState.Initial) return ExperienceStatus("Waiting for account verification", "Cloud changes remain safely paused.", ExperienceTone.NEUTRAL)
    if (auth is AuthState.Error) return ExperienceStatus("Waiting for account verification", "Cloud sync is blocked until the account is safe.", ExperienceTone.ATTENTION, "Review account")
    if (!lastError.isNullOrBlank() || status.contains("fail", true) || status.contains("error", true)) return ExperienceStatus("Sync needs attention", "Your data remains saved locally.", ExperienceTone.ATTENTION, "Retry sync")
    if (status.contains("sync", true) && !status.equals("synced", true)) return ExperienceStatus("Syncing", "Securely updating your cloud copy.", ExperienceTone.NEUTRAL)
    if (pending > 0) return ExperienceStatus("Waiting for connection", "$pending local change${if (pending == 1) "" else "s"} waiting.", ExperienceTone.ATTENTION, "Retry sync")
    return ExperienceStatus("Synced", "Cloud copy is up to date.", ExperienceTone.POSITIVE)
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
