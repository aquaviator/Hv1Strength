import re

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    content = f.read()

old_logic = """        // 3. Check Human V1 Account Trial
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
        }"""

new_logic = """        // 3. Check Human V1 Account Trial
        if (userProfile != null) {
            if (userProfile.trialStartedAt != null && userProfile.trialEndsAt != null) {
                // Trial has been initialized
                if (now < userProfile.trialEndsAt!!) {
                    val daysRemaining = maxOf(1, ((userProfile.trialEndsAt!! - now) / (24L * 60L * 60L * 1000L)).toInt())
                    return AppAccessState.TrialActive(daysRemaining, userProfile.trialEndsAt!!)
                }
            } else {
                // New eligible account - initialize trial once
                val trialPolicy = platformConfigRepository.getTrialPolicy()
                if (trialPolicy.trialEnabled) {
                    var cloudTrialStartedAt: Long? = null
                    var cloudTrialEndsAt: Long? = null
                    
                    if (com.example.HumanStrengthApplication.isFirebaseConfigured) {
                        try {
                            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            val doc = kotlinx.coroutines.tasks.await(db.collection("users").document(userProfile.humanUserId).collection("profile").document("main").get())
                            if (doc.exists()) {
                                cloudTrialStartedAt = doc.getLong("trialStartedAt")
                                cloudTrialEndsAt = doc.getLong("trialEndsAt")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not fetch cloud profile to verify trial status", e)
                        }
                    }

                    if (cloudTrialStartedAt != null && cloudTrialEndsAt != null) {
                        val updatedProfile = userProfile.copy(
                            trialStartedAt = cloudTrialStartedAt,
                            trialEndsAt = cloudTrialEndsAt
                        )
                        repository.insertUserProfile(updatedProfile)
                        return AppAccessState.Initializing
                    } else {
                        val trialEndsAt = now + (trialPolicy.trialDurationDays * 24L * 60L * 60L * 1000L)
                        val updatedProfile = userProfile.copy(
                            trialStartedAt = now,
                            trialEndsAt = trialEndsAt,
                            updatedAt = now
                        )
                        repository.insertUserProfile(updatedProfile)
                        return AppAccessState.Initializing
                    }
                }
            }
        }"""

content = content.replace(old_logic, new_logic)

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.write(content)

