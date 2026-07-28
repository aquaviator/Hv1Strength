import re

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    content = f.read()

content = content.replace("resolveAccessState(subState, userProfile?.createdAt, cached)", "resolveAccessState(subState, cached)")
content = content.replace("private suspend fun resolveAccessState(\n        subState: SubscriptionState,\n        profileCreatedAt: Long?,\n        cached: VerifiedEntitlement?\n    )", "private suspend fun resolveAccessState(\n        subState: SubscriptionState,\n        cached: VerifiedEntitlement?\n    )")

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.write(content)
