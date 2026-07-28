with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    lines = f.readlines()

new_lines = []
in_check_trial = False

for line in lines:
    if "private fun checkTrialState" in line:
        in_check_trial = True
    
    if in_check_trial and "override fun refreshAccessState" in line:
        in_check_trial = False
        # Add the missing closing brace before refreshAccessState
        new_lines.append("    }\n\n")

    new_lines.append(line)

with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.writelines(new_lines)

