# Active strength workout foreground execution

The non-exported `specialUse` foreground service supports a workout explicitly started by the user. Its Play declaration should describe user-initiated strength-session elapsed timing, rest timing, and the ongoing resume notification. The service reads the singleton Room backup and never creates sessions, changes sets, selects cloud identity, or performs synchronization.

Android force-stop is respected. Reboot does not automatically launch UI or foreground execution; the valid Room backup remains available through the normal in-app recovery flow. Notification denial does not block a workout, but the UI reports reduced background visibility and links to application settings.
