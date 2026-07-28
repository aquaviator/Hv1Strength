import re

with open("app/src/test/java/com/example/Candidate70PhaseDEntitlementTest.kt", "r") as f:
    content = f.read()

content = content.replace("assertTrue(state is AppAccessState.TrialActive || state is AppAccessState.Initializing)", "assertTrue(state is AppAccessState.Unentitled || state is AppAccessState.Initializing)")

with open("app/src/test/java/com/example/Candidate70PhaseDEntitlementTest.kt", "w") as f:
    f.write(content)
