package com.example

import com.example.billing.AppAccessState
import com.example.ui.screens.annualPriceCopy
import com.example.ui.screens.membershipPresentation
import com.example.ui.screens.remainingTrialDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MembershipPresentationTest {
    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_800_000_000_000L

    @Test
    fun quickStartDoesNotContainDemoTrialBadge() {
        val candidates = listOf(
            File("src/main/java/com/example/ui/screens/WorkoutScreen.kt"),
            File("app/src/main/java/com/example/ui/screens/WorkoutScreen.kt")
        )
        val source = candidates.first { it.exists() }.readText()

        assertFalse(source.contains("Day 12 of 30"))
    }

    @Test
    fun exactThirtyDayWindowDisplaysThirtyDaysNotThirtyOne() {
        assertEquals(30, remainingTrialDays(now + 30L * day, now))
        val presentation = membershipPresentation(
            AppAccessState.TrialActive(
                daysRemaining = 999,
                trialEndDateMillis = now + 30L * day,
                trialStartedAtMillis = now
            ),
            now
        )

        assertEquals("30 days remaining", presentation.primaryStatus)
    }

    @Test
    fun remainingDaysComeFromExpiryRatherThanPolicyOrLegacyField() {
        val presentation = membershipPresentation(
            AppAccessState.TrialActive(
                daysRemaining = 30,
                trialEndDateMillis = now + 7L * day + 1L,
                trialStartedAtMillis = now - day
            ),
            now
        )

        assertEquals("8 days remaining", presentation.primaryStatus)
    }

    @Test
    fun activeTrialUsesHumanV1IdentityAndAuthoritativeDates() {
        val startedAt = now - day
        val endsAt = now + 10L * day
        val presentation = membershipPresentation(
            AppAccessState.TrialActive(10, endsAt, startedAt),
            now
        )

        assertEquals("Human V1 Trial", presentation.primaryTitle)
        assertEquals(startedAt, presentation.trialStartedAtMillis)
        assertEquals(endsAt, presentation.trialEndsAtMillis)
        assertFalse(presentation.description.contains("Google Play free trial", ignoreCase = true))
        assertEquals("After your trial", presentation.annualHeading)
    }

    @Test
    fun expiredTrialShowsEndedStateAndAnnualProduct() {
        val endedAt = now - day
        val presentation = membershipPresentation(AppAccessState.Expired(endedAt, now - 31L * day), now)

        assertEquals("Human V1 Trial", presentation.primaryTitle)
        assertTrue(presentation.primaryStatus.startsWith("Ended "))
        assertTrue(presentation.showAnnualProduct)
        assertEquals("Continue with", presentation.annualHeading)
    }

    @Test
    fun paidMembershipIsPrimary() {
        val presentation = membershipPresentation(AppAccessState.Subscribed(now + 365L * day), now)

        assertEquals("Human Strength Annual", presentation.primaryTitle)
        assertEquals("Active", presentation.primaryStatus)
        assertFalse(presentation.showAnnualProduct)
        assertFalse(presentation.allowPurchase)
    }

    @Test
    fun verificationUnavailableInventsNoTrialDatesOrCountdown() {
        val presentation = membershipPresentation(AppAccessState.VerificationUnavailable, now)

        assertEquals("Access verification unavailable", presentation.primaryTitle)
        assertNull(presentation.trialStartedAtMillis)
        assertNull(presentation.trialEndsAtMillis)
        assertFalse(presentation.primaryStatus.contains("days"))
    }

    @Test
    fun annualPriceNeverAdvertisesGooglePlayTrialOrDuplicatesPeriod() {
        val copy = annualPriceCopy("£24.00/year")

        assertEquals("£24.00/year via Google Play", copy)
        assertFalse(copy.contains("free trial", ignoreCase = true))
    }
}
