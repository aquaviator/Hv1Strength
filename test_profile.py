import re
with open("app/src/main/java/com/example/data/Entities.kt", "r") as f:
    content = f.read()
print("UserProfile" in content)
