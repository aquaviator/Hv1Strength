import re

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for i, line in enumerate(lines):
    if "val thirtyDaysInMillis" in line and not skip:
        # Find where to start skipping. Actually let's just find the exact lines
        skip = True
    
    if skip and "override fun refreshAccessState" in line:
        skip = False
        
    if not skip:
        new_lines.append(line)

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.writelines(new_lines)
