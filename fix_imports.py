with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "r") as f:
    lines = f.readlines()

new_lines = []
imports = []
code = []

in_imports = True
for line in lines:
    if line.startswith("package "):
        new_lines.append(line)
    elif line.startswith("import "):
        imports.append(line)
    elif line.strip() == "":
        code.append(line)
    else:
        in_imports = False
        code.append(line)

final_lines = new_lines + imports + code
with open("app/src/main/java/com/example/billing/PlayEntitlementRepository.kt", "w") as f:
    f.writelines(final_lines)
