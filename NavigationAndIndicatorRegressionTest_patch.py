import re

with open("app/src/test/java/com/example/NavigationAndIndicatorRegressionTest.kt", "r") as f:
    content = f.read()

content = content.replace(
    'assertEquals("welcome", navController.currentDestination?.route)',
    'assertEquals("Expected welcome, but was ${navController.currentDestination?.route}", "welcome", navController.currentDestination?.route)'
)

with open("app/src/test/java/com/example/NavigationAndIndicatorRegressionTest.kt", "w") as f:
    f.write(content)
