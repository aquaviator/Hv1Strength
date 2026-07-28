import re

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    content = f.read()

content = content.replace("@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)\n@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)", "@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)")

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.write(content)
