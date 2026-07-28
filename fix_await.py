with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    content = f.read()

content = content.replace(
    "import kotlinx.coroutines.launch",
    "import kotlinx.coroutines.launch\nimport kotlinx.coroutines.tasks.await"
)

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.write(content)
