with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    content = f.read()

content = content.replace(
    'val doc = kotlinx.coroutines.tasks.await(db.collection("users").document(userProfile.humanUserId).collection("profile").document("main").get())',
    'val doc = db.collection("users").document(userProfile.humanUserId).collection("profile").document("main").get().await()'
)

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.write(content)
