package com.example.ui.screens

import com.example.billing.AppAccessState

internal data class MembershipPresentation(
    val primaryTitle: String,
    val primaryStatus: String,
    val description: String,
    val trialStartedAtMillis: Long? = null,
    val trialEndsAtMillis: Long? = null,
    val showAnnualProduct: Boolean = true,
    val annualHeading: String? = null,
    val allowPurchase: Boolean = true
)

internal fun remainingTrialDays(trialEndsAtMillis: Long, nowMillis: Long): Int {
    val remainingMillis = (trialEndsAtMillis - nowMillis).coerceAtLeast(0L)
    if (remainingMillis == 0L) return 0
    val millisPerDay = 24L * 60L * 60L * 1000L
    return ((remainingMillis + millisPerDay - 1L) / millisPerDay).toInt()
}

internal fun membershipPresentation(
    state: AppAccessState,
    nowMillis: Long = System.currentTimeMillis()
): MembershipPresentation = when (state) {
    is AppAccessState.TrialActive -> if (state.trialStartedAtMillis != null) {
        MembershipPresentation(
            primaryTitle = "Human V1 Trial",
            primaryStatus = "${remainingTrialDays(state.trialEndDateMillis, nowMillis)} days remaining",
            description = "Full access to Human Strength during your introductory Human V1 trial.",
            trialStartedAtMillis = state.trialStartedAtMillis,
            trialEndsAtMillis = state.trialEndDateMillis,
            annualHeading = "After your trial"
        )
    } else {
        MembershipPresentation(
            primaryTitle = "Human Strength Annual",
            primaryStatus = "Active",
            description = "Your verified Google Play access is active.",
            showAnnualProduct = false,
            allowPurchase = false
        )
    }
    is AppAccessState.Subscribed -> MembershipPresentation(
        primaryTitle = "Human Strength Annual",
        primaryStatus = "Active",
        description = "Your annual membership is active.",
        showAnnualProduct = false,
        allowPurchase = false
    )
    is AppAccessState.SubscriptionActiveUntilExpiry -> MembershipPresentation(
        primaryTitle = "Human Strength Annual",
        primaryStatus = "Active",
        description = "Your annual membership remains active until its current expiry.",
        showAnnualProduct = false,
        allowPurchase = false
    )
    AppAccessState.GracePeriod -> MembershipPresentation(
        primaryTitle = "Human Strength Annual",
        primaryStatus = "Payment grace period",
        description = "Google Play is resolving your annual membership payment.",
        showAnnualProduct = false,
        allowPurchase = false
    )
    AppAccessState.PaymentPending -> MembershipPresentation(
        primaryTitle = "Human Strength Annual",
        primaryStatus = "Payment pending",
        description = "Google Play is processing your annual membership.",
        showAnnualProduct = false,
        allowPurchase = false
    )
    is AppAccessState.Expired -> if (
        state.trialStartedAtMillis != null &&
        state.trialEndDateMillis != null
    ) {
        MembershipPresentation(
            primaryTitle = "Human V1 Trial",
            primaryStatus = "Ended ${formatMembershipDate(state.trialEndDateMillis)}",
            description = "Your Human V1 account trial has ended.",
            trialStartedAtMillis = state.trialStartedAtMillis,
            trialEndsAtMillis = state.trialEndDateMillis,
            annualHeading = "Continue with"
        )
    } else {
        MembershipPresentation(
            primaryTitle = "Human Strength Annual",
            primaryStatus = "Inactive",
            description = "No active annual membership was verified.",
            showAnnualProduct = false
        )
    }
    AppAccessState.VerificationUnavailable -> MembershipPresentation(
        primaryTitle = "Access verification unavailable",
        primaryStatus = "Try again when connected",
        description = "We could not verify your current membership status. No trial dates have been estimated.",
        showAnnualProduct = false,
        allowPurchase = false
    )
    AppAccessState.Initializing -> MembershipPresentation(
        primaryTitle = "Checking membership",
        primaryStatus = "Please wait",
        description = "Confirming your Human V1 account access.",
        showAnnualProduct = false,
        allowPurchase = false
    )
    AppAccessState.Unentitled -> MembershipPresentation(
        primaryTitle = "Human Strength Annual",
        primaryStatus = "Subscription required",
        description = "Continue with an annual membership through Google Play.",
        showAnnualProduct = false
    )
    is AppAccessState.Error -> MembershipPresentation(
        primaryTitle = "Membership status unavailable",
        primaryStatus = "Try again",
        description = state.message,
        showAnnualProduct = false,
        allowPurchase = false
    )
}

internal fun formatMembershipDate(timestampMillis: Long): String =
    java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
        .format(java.util.Date(timestampMillis))

internal fun annualPriceCopy(formattedPrice: String?): String {
    val price = formattedPrice
        ?.removeSuffix("/year")
        ?.removeSuffix(" per year")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "£24"
    return "$price/year via Google Play"
}
