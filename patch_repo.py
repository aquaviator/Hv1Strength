import re

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    content = f.read()

# Replace the DEBUG block with the actual logic
old_block = """        // 3. Fallback evaluation mode (DEBUG / Developer build preview only)
        // In production, Google Play owns the 1-month introductory trial offer authority.
        return if (com.example.BuildConfig.DEBUG) {
            checkTrialState(profileCreatedAt, now)
        } else {
            if (cached != null && (!cached.isValidAt(now) || cached.status == "EXPIRED")) {
                AppAccessState.Expired
            } else if (subState is SubscriptionState.Loading) {
                AppAccessState.Initializing
            } else {
                AppAccessState.Unentitled
            }
        }"""

new_block = """        if (cached != null && (!cached.isValidAt(now) || cached.status == "EXPIRED")) {
            return AppAccessState.Expired
        } else if (subState is SubscriptionState.Loading) {
            return AppAccessState.Initializing
        } else {
            return AppAccessState.Unentitled
        }"""

content = content.replace(old_block, new_block)

# Remove checkTrialState function
check_trial_regex = r"    private fun checkTrialState\(.*?    }\n\n"
content = re.sub(check_trial_regex, "", content, flags=re.DOTALL)

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.write(content)
