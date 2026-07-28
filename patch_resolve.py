import re

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    content = f.read()

old_fun = """    private suspend fun resolveAccessState(
        subState: SubscriptionState,
        cached: VerifiedEntitlement?
    ): AppAccessState {"""

new_fun = """    private suspend fun resolveAccessState(
        subState: SubscriptionState,
        userProfile: com.example.data.UserProfile?,
        cached: VerifiedEntitlement?
    ): AppAccessState {"""

content = content.replace(old_fun, new_fun)

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.write(content)

