import re

with open("app/src/main/java/com/example/data/Entities.kt", "r") as f:
    content = f.read()

new_fields = """    val sex: String? = null,
    val trainingExperience: String? = null,
    val trialStartedAt: Long? = null,
    val trialEndsAt: Long? = null,
    // Sync metadata fields"""

content = content.replace('    val sex: String? = null,\n    val trainingExperience: String? = null, // "Beginner", "Intermediate", "Advanced"\n\n    // Sync metadata fields', new_fields)

with open("app/src/main/java/com/example/data/Entities.kt", "w") as f:
    f.write(content)
