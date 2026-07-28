import re

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    content = f.read()

old_logic = """                        val trialEndsAt = now + (trialPolicy.trialDurationDays * 24L * 60L * 60L * 1000L)
                        val updatedProfile = userProfile.copy(
                            trialStartedAt = now,
                            trialEndsAt = trialEndsAt,
                            updatedAt = now
                        )
                        repository.insertUserProfile(updatedProfile)
                        return AppAccessState.Initializing"""

new_logic = """                        val isExistingAccount = (now - userProfile.createdAt) > (24L * 60L * 60L * 1000L)
                        val startedAt = if (isExistingAccount) userProfile.createdAt else now
                        val trialEndsAt = startedAt + (trialPolicy.trialDurationDays * 24L * 60L * 60L * 1000L)
                        
                        val updatedProfile = userProfile.copy(
                            trialStartedAt = startedAt,
                            trialEndsAt = trialEndsAt,
                            updatedAt = now
                        )
                        repository.insertUserProfile(updatedProfile)
                        return AppAccessState.Initializing"""

content = content.replace(old_logic, new_logic)

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.write(content)

