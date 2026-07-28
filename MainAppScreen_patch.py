import re

with open("app/src/test/java/com/example/Sprint5WorkoutGroupingAndExecutionTest.kt", "r") as f:
    content = f.read()

content = content.replace(
    'println("queue: ${queue.size}, state: $activeState")\n        assertEquals(1, queue.size)',
    'waitUntil { activeWorkoutViewModel.executionQueue.value.isNotEmpty() }\n        val updatedQueue = activeWorkoutViewModel.executionQueue.value\n        assertEquals(1, updatedQueue.size)'
)
content = content.replace(
    'assertEquals(WorkoutGroupType.SINGLE, queue[0].groupType)',
    'assertEquals(WorkoutGroupType.SINGLE, updatedQueue[0].groupType)'
)
content = content.replace(
    'assertFalse(queue[0].set.isCompleted)',
    'assertFalse(updatedQueue[0].set.isCompleted)'
)

with open("app/src/test/java/com/example/Sprint5WorkoutGroupingAndExecutionTest.kt", "w") as f:
    f.write(content)
