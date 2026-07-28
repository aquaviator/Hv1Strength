import re

with open("app/src/main/java/com/example/ui/screens/SubscriptionAccessScreen.kt", "r") as f:
    content = f.read()

replacement = """            val titleText = if (appAccessState is com.example.billing.AppAccessState.Expired) {
                "YOUR TRIAL HAS ENDED"
            } else {
                "UNLOCK HUMAN STRENGTH"
            }

            val bodyText = if (appAccessState is com.example.billing.AppAccessState.Expired) {
                "Your training history, logged workouts, and custom routines are safe. Continue training with Human Strength for £24/year."
            } else {
                "Access all training modules, preserve local & cloud sync data, and build your custom routines with Human Strength."
            }

            Text(
                text = titleText,"""

content = re.sub(r'            Text\(\s*text = "YOUR TRIAL HAS ENDED",', replacement, content)

with open("app/src/main/java/com/example/ui/screens/SubscriptionAccessScreen.kt", "w") as f:
    f.write(content)

