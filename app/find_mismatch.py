# find_mismatch.py
def find_unbalanced_braces(filepath):
    with open(filepath, 'r') as f:
        lines = f.readlines()

    stack = []
    for i, line in enumerate(lines):
        line_num = i + 1
        for char in line:
            if char == '{':
                stack.append(line_num)
            elif char == '}':
                if not stack:
                    print(f"Extra closing brace at line {line_num}: {line.strip()}")
                else:
                    stack.pop()
    
    if stack:
        print(f"Total unclosed opening braces: {len(stack)}")
        print("First 20 unclosed opening braces (line numbers):")
        for line_num in stack[:20]:
            print(f"  Line {line_num}: {lines[line_num - 1].strip()}")
    else:
        print("Braces are perfectly balanced!")

find_unbalanced_braces("app/src/main/java/com/example/ui/screens/WorkoutScreen.kt")
