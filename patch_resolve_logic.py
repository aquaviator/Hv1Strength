import re

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    content = f.read()

old_logic = """        if (cached != null && (!cached.isValidAt(now) || cached.status == "EXPIRED")) {
            return AppAccessState.Expired
        } else if (subState is SubscriptionState.Loading) {
            return AppAccessState.Initializing
        } else {
            return AppAccessState.Unentitled
        }"""

new_logic = """        if (subState is SubscriptionState.Loading) {
            return AppAccessState.Initializing
        }

        // 3. Check Human V1 Account Trial
        if (userProfile != null) {
            if (userProfile.trialStartedAt != null && userProfile.trialEndsAt != null) {
                // Trial has been initialized
                if (now < userProfile.trialEndsAt) {
                    val daysRemaining = maxOf(1, ((userProfile.trialEndsAt - now) / (24L * 60L * 60L * 1000L)).toInt())
                    return AppAccessState.TrialActive(daysRemaining, userProfile.trialEndsAt)
                }
            } else {
                // New eligible account - initialize trial once
                val trialPolicy = platformConfigRepository.getTrialPolicy()
                if (trialPolicy.trialEnabled) {
                    val trialEndsAt = now + (trialPolicy.trialDurationDays * 24L * 60L * 60L * 1000L)
                    val updatedProfile = userProfile.copy(
                        trialStartedAt = now,
                        trialEndsAt = trialEndsAt,
                        updatedAt = now
                    )
                    // Save synchronously or asynchronously
                    repository.insertUserProfile(updatedProfile)
                    // Return initializing while we wait for the flow to emit the new profile
                    return AppAccessState.Initializing
                }
            }
        }

        // 4. Known Human V1 trial expired or Play Subscription expired
        val hasExpiredPlay = cached != null && (!cached.isValidAt(now) || cached.status == "EXPIRED")
        val hasExpiredTrial = userProfile?.trialEndsAt != null && now >= userProfile.trialEndsAt

        if (hasExpiredPlay || hasExpiredTrial) {
            return AppAccessState.Expired
        }

        return AppAccessState.Unentitled"""

content = content.replace(old_logic, new_logic)

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.write(content)

