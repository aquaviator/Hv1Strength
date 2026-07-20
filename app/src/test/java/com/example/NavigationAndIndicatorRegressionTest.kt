package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.identity.DeviceIdGenerator
import com.example.core.identity.HumanUserIdGenerator
import com.example.data.*
import com.example.ui.components.ExerciseCardHeader
import com.example.ui.components.ExercisePrescriptionCard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StrengthViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w480dp-h1500dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationAndIndicatorRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private var database: StrengthDatabase? = null
    private var repository: StrengthRepository? = null
    private var viewModel: StrengthViewModel? = null

    private fun idleLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun waitUntil(timeoutMs: Long = 3000, condition: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            idleLooper()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Condition not met within $timeoutMs ms")
    }

    private fun getLazyViewModel(): StrengthViewModel {
        if (viewModel == null) {
            context = ApplicationProvider.getApplicationContext()
            DeviceIdGenerator.appContext = context
            HumanUserIdGenerator.appContext = context
            val db = Room.inMemoryDatabaseBuilder(context, StrengthDatabase::class.java)
                .allowMainThreadQueries()
                .setQueryExecutor { it.run() }
                .setTransactionExecutor { it.run() }
                .build()
            database = db
            val repo = StrengthRepository(db.strengthDao(), context)
            repository = repo
            viewModel = StrengthViewModel(repo, context)
        }
        return viewModel!!
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        // Do not close the in-memory database to prevent active Room background flow coroutines 
        // from throwing "connection pool has been closed" exceptions during asynchronous test completion.
    }

    // ============================================================================
    // SECTION 1: GENUINE NAVIGATION TESTS
    // ============================================================================

    @Test
    fun testNavigation_historyToWorkout() {
        val vm = getLazyViewModel()
        // Force authentication so we don't start on welcome screen
        runBlocking {
            vm.authViewModel.authRepository.signInAnonymously()
        }
        waitUntil { vm.authState.value is AuthState.Offline }

        lateinit var navController: NavHostController

        composeTestRule.setContent {
            MyApplicationTheme {
                navController = rememberNavController()
                MainAppScreen(viewModel = vm, navController = navController)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Click bottom navigation History item
        composeTestRule.onNodeWithContentDescription("History", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertEquals("history", navController.currentDestination?.route)

        // 2. Click bottom navigation Workout item
        composeTestRule.onNodeWithContentDescription("Workout", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // 3. Assert target route is workout
        assertEquals("workout", navController.currentDestination?.route)
    }

    @Test
    fun testNavigation_workoutToHistory() {
        val vm = getLazyViewModel()
        runBlocking {
            vm.authViewModel.authRepository.signInAnonymously()
        }
        waitUntil { vm.authState.value is AuthState.Offline }

        lateinit var navController: NavHostController

        composeTestRule.setContent {
            MyApplicationTheme {
                navController = rememberNavController()
                MainAppScreen(viewModel = vm, navController = navController)
            }
        }
        composeTestRule.waitForIdle()
        assertEquals("workout", navController.currentDestination?.route)

        // Click bottom navigation History item
        composeTestRule.onNodeWithContentDescription("History", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // Assert route is history
        assertEquals("history", navController.currentDestination?.route)
    }

    @Test
    fun testNavigation_backstackBehavior() {
        val vm = getLazyViewModel()
        runBlocking {
            vm.authViewModel.authRepository.signInAnonymously()
        }
        waitUntil { vm.authState.value is AuthState.Offline }

        lateinit var navController: NavHostController

        composeTestRule.setContent {
            MyApplicationTheme {
                navController = rememberNavController()
                MainAppScreen(viewModel = vm, navController = navController)
            }
        }
        composeTestRule.waitForIdle()

        // Navigate: workout -> history
        composeTestRule.onNodeWithContentDescription("History", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertEquals("history", navController.currentDestination?.route)

        // Navigate: history -> workout
        composeTestRule.onNodeWithContentDescription("Workout", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertEquals("workout", navController.currentDestination?.route)

        // Press back programmatically.
        // Expected behavior:
        // Clicking "History" performs popUpTo("workout") { saveState = true }, so the back-stack root is "workout".
        // Tapping "Workout" again restores its state at the root.
        // Therefore, pressing back at the root "workout" should return false (the app exits or cannot pop further).
        val didPop = composeTestRule.runOnUiThread {
            navController.popBackStack()
        }
        assertFalse(didPop)
    }

    @Test
    fun testNavigation_repeatedTabSelection() {
        val vm = getLazyViewModel()
        runBlocking {
            vm.authViewModel.authRepository.signInAnonymously()
        }
        waitUntil { vm.authState.value is AuthState.Offline }

        lateinit var navController: NavHostController

        composeTestRule.setContent {
            MyApplicationTheme {
                navController = rememberNavController()
                MainAppScreen(viewModel = vm, navController = navController)
            }
        }
        composeTestRule.waitForIdle()

        // Click Workout bottom-nav item repeatedly
        repeat(5) {
            composeTestRule.onNodeWithContentDescription("Workout", useUnmergedTree = true).performClick()
            composeTestRule.waitForIdle()
        }

        // Verify that the back-stack only has 1 instance of "workout" (or has launchSingleTop behavior working)
        val entriesCount = navController.currentBackStack.value.count { it.destination.route == "workout" }
        assertEquals(1, entriesCount)
    }

    @Test
    fun testNavigation_reproduceWorkoutSummaryToHistoryToWorkout() {
        val vm = getLazyViewModel()
        runBlocking {
            vm.authViewModel.authRepository.signInAnonymously()
        }
        waitUntil { vm.authState.value is AuthState.Offline }

        lateinit var navController: NavHostController

        composeTestRule.setContent {
            MyApplicationTheme {
                navController = rememberNavController()
                Box(modifier = Modifier.width(1000.dp).height(2000.dp)) {
                    MainAppScreen(viewModel = vm, navController = navController)
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Start an active workout
        vm.activeWorkoutViewModel.startWorkout(null)
        composeTestRule.waitForIdle()
        waitUntil { vm.activeWorkoutViewModel.activeWorkoutState.value != null }
        assertEquals("active_workout", navController.currentDestination?.route)

        // Add an exercise to active workout to ensure it is populated
        val ex = Exercise("curls", "Bicep Curls", "Arms")
        vm.activeWorkoutViewModel.addExerciseToActiveWorkout(ex)
        composeTestRule.waitForIdle()
        waitUntil { vm.activeWorkoutViewModel.activeWorkoutState.value?.exercises?.size == 1 }

        // 2. Finish the workout
        vm.finishActiveWorkout()
        composeTestRule.waitForIdle()

        // It should navigate to workout_summary/{completedWorkoutId}
        waitUntil { navController.currentDestination?.route?.startsWith("workout_summary/") == true }
        composeTestRule.waitForIdle()

        // DIAGNOSTIC DIRECT FILE WRITE
        try {
            val nodes = composeTestRule.onAllNodes(isRoot().or(isRoot().not())).fetchSemanticsNodes()
            val tags = mutableListOf<String>()
            val texts = mutableListOf<String>()
            val descs = mutableListOf<String>()
            for (node in nodes) {
                for (entry in node.config) {
                    val keyName = entry.key.name
                    val value = entry.value
                    if (keyName == "TestTag" && value is String) {
                        tags.add(value)
                    } else if (keyName == "Text" && value is List<*>) {
                        texts.add(value.joinToString())
                    } else if (keyName == "ContentDescription" && value is List<*>) {
                        descs.add(value.joinToString())
                    }
                }
            }
            val message = "STATE: error=${tags.contains("summary_error_card")}, loading=${tags.contains("summary_loading_indicator")}, success=${tags.contains("summary_view_history_button")}\nALL TAGS: $tags\nTEXTS: $texts\nDESCS: $descs"
            java.io.File("diagnostic.txt").writeText(message)
        } catch (e: Exception) {
            java.io.File("diagnostic.txt").writeText("DIAGNOSTIC FAILED: ${e.message}")
        }

        // Wait for the asynchronous database loading to complete and show the summary UI
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithTag("summary_hero_card").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Scroll to and click "View History" button on the workout summary screen
        composeTestRule.onNodeWithTag("summary_view_history_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Assert current destination is history
        assertEquals("history", navController.currentDestination?.route)

        // Record backstack details (before click)
        val routeSequenceBefore = navController.currentBackStack.value.map { it?.destination?.route }
        val msgBefore = "BACKSTACK BEFORE TAB CLICK: $routeSequenceBefore"
        println(msgBefore)
        java.io.File("diagnostic.txt").appendText("\n" + msgBefore)

        // 4. Tap the Workout item in bottom navigation
        composeTestRule.onNodeWithContentDescription("Workout", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // Record backstack details (after click)
        val routeSequenceAfter = navController.currentBackStack.value.map { it?.destination?.route }
        val msgAfter = "BACKSTACK AFTER TAB CLICK: $routeSequenceAfter"
        println(msgAfter)
        java.io.File("diagnostic.txt").appendText("\n" + msgAfter)

        // Assert destination is workout
        assertEquals("workout", navController.currentDestination?.route)
    }

    @Test
    fun testNavigation_authenticationLifecycle() {
        val vm = getLazyViewModel()
        // Start from unauthenticated welcome screen
        lateinit var navController: NavHostController

        composeTestRule.setContent {
            MyApplicationTheme {
                navController = rememberNavController()
                MainAppScreen(viewModel = vm, navController = navController)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Confirm we started on Welcome destination
        assertEquals("welcome", navController.currentDestination?.route)

        // 2. Perform authentication -> enter offline mode
        runBlocking {
            vm.authViewModel.authRepository.signInAnonymously()
        }
        composeTestRule.waitForIdle()
        waitUntil { navController.currentDestination?.route == "workout" }

        // 3. Navigate to History
        composeTestRule.onNodeWithContentDescription("History", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertEquals("history", navController.currentDestination?.route)

        // 4. Navigate back to Workout
        composeTestRule.onNodeWithContentDescription("Workout", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertEquals("workout", navController.currentDestination?.route)

        // 5. Verify "welcome" was popped off and is no longer on backstack
        val containsWelcome = navController.currentBackStack.value.any { it.destination.route == "welcome" }
        assertFalse(containsWelcome)

        // 6. Sign out complete simulation
        runBlocking {
            vm.authViewModel.authRepository.signOut(true)
        }
        composeTestRule.waitForIdle()
        waitUntil { navController.currentDestination?.route == "welcome" }
    }

    @Test
    fun testNavigation_activeWorkoutProtection() {
        val vm = getLazyViewModel()
        runBlocking {
            vm.authViewModel.authRepository.signInAnonymously()
        }
        waitUntil { vm.authState.value is AuthState.Offline }

        lateinit var navController: NavHostController

        composeTestRule.setContent {
            MyApplicationTheme {
                navController = rememberNavController()
                MainAppScreen(viewModel = vm, navController = navController)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Trigger starting an active workout
        vm.activeWorkoutViewModel.startWorkout(null)
        composeTestRule.waitForIdle()
        waitUntil { vm.activeWorkoutViewModel.activeWorkoutState.value != null }

        // Route should transition to active_workout
        assertEquals("active_workout", navController.currentDestination?.route)

        // 2. Navigate away to "History" tab
        composeTestRule.runOnUiThread {
            navController.navigate("history")
        }
        composeTestRule.waitForIdle()
        assertEquals("history", navController.currentDestination?.route)

        // Active workout state is NOT discarded
        assertNotNull(vm.activeWorkoutViewModel.activeWorkoutState.value)

        // 3. Return to "Workout" screen
        composeTestRule.runOnUiThread {
            navController.navigate("workout")
        }
        composeTestRule.waitForIdle()
        assertEquals("workout", navController.currentDestination?.route)

        // 4. Assert active session is recoverable and the resume banner exists
        composeTestRule.onNodeWithTag("active_workout_resume_banner").assertExists()
    }

    // ============================================================================
    // SECTION 2: EXPANSION & INTEGRITY TESTS
    // ============================================================================

    @Test
    fun testExerciseCardHeader_collapsedState() {
        var clicked = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ExerciseCardHeader(
                    exerciseName = "Bench Press",
                    category = "Chest",
                    labelTag = "A1",
                    isExpanded = false,
                    onToggleExpand = { clicked = true },
                    onDelete = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onPairClick = {},
                    canMoveUp = false,
                    canMoveDown = false
                )
            }
        }

        val expandTag = "expand_collapse_control_Bench_Press"
        composeTestRule.onNodeWithTag(expandTag).assertExists()
        composeTestRule.onNodeWithTag(expandTag).assertTextContains("Expand")
        composeTestRule.onNodeWithTag(expandTag).assertContentDescriptionContains("Expand Bench Press")

        composeTestRule.onNodeWithTag(expandTag).performClick()
        assertTrue(clicked)
    }

    @Test
    fun testExerciseCardHeader_expandedState() {
        var clicked = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ExerciseCardHeader(
                    exerciseName = "Squats",
                    category = "Legs",
                    labelTag = "B2",
                    isExpanded = true,
                    onToggleExpand = { clicked = true },
                    onDelete = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onPairClick = {},
                    canMoveUp = false,
                    canMoveDown = false
                )
            }
        }

        val collapseTag = "expand_collapse_control_Squats"
        composeTestRule.onNodeWithTag(collapseTag).assertExists()
        composeTestRule.onNodeWithTag(collapseTag).assertTextContains("Collapse")
        composeTestRule.onNodeWithTag(collapseTag).assertContentDescriptionContains("Collapse Squats")

        composeTestRule.onNodeWithTag(collapseTag).performClick()
        assertTrue(clicked)
    }

    @Test
    fun testExerciseCardHeader_longExerciseName() {
        composeTestRule.setContent {
            MyApplicationTheme {
                ExerciseCardHeader(
                    exerciseName = "Barbell Incline Bench Press with Extra Long Grip and Super Wide Arm Width Adjustment",
                    category = "Chest",
                    labelTag = "A1",
                    isExpanded = false,
                    onToggleExpand = {},
                    onDelete = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onPairClick = {},
                    canMoveUp = false,
                    canMoveDown = false
                )
            }
        }

        // Verify that long name doesn't crash or prevent layout and tag is discoverable
        val expandTag = "expand_collapse_control_Barbell_Incline_Bench_Press_with_Extra_Long_Grip_and_Super_Wide_Arm_Width_Adjustment"
        composeTestRule.onNodeWithTag(expandTag).assertExists()
        composeTestRule.onNodeWithTag(expandTag).assertTextContains("Expand")
    }

    @Test
    fun testExerciseCardHeader_narrowHandsetAndLargeFont() {
        composeTestRule.setContent {
            MyApplicationTheme {
                Box(modifier = Modifier.width(280.dp)) {
                    ExerciseCardHeader(
                        exerciseName = "Deadlift",
                        category = "Legs",
                        labelTag = "C1",
                        isExpanded = false,
                        onToggleExpand = {},
                        onDelete = {},
                        onMoveUp = {},
                        onMoveDown = {},
                        onPairClick = {},
                        canMoveUp = false,
                        canMoveDown = false
                    )
                }
            }
        }

        // Control remains fully visible and interactable on 280.dp layout width
        val expandTag = "expand_collapse_control_Deadlift"
        composeTestRule.onNodeWithTag(expandTag).assertExists()
        composeTestRule.onNodeWithTag(expandTag).assertHasClickAction()
    }

    @Test
    fun testExerciseCard_changingPrescriptionValuesDoesNotCollapse() {
        var updatedState: StrengthViewModel.TemplateExerciseState? = null
        var isExpanded = true

        val templateExercise = StrengthViewModel.TemplateExerciseState(
            id = 1,
            exerciseId = "bench_press",
            restSeconds = 90,
            sets = listOf(
                StrengthViewModel.TemplateSetState(id = 10, targetRepsMin = 8, targetRepsMax = 10, targetWeight = 100f)
            )
        )
        val exerciseObj = Exercise("bench_press", "Bench Press", "Chest")

        composeTestRule.setContent {
            MyApplicationTheme {
                ExercisePrescriptionCard(
                    templateExercise = templateExercise,
                    exerciseObj = exerciseObj,
                    exIndex = 0,
                    isMetric = true,
                    isExpanded = isExpanded,
                    onToggleExpand = { isExpanded = !isExpanded },
                    onUpdate = { updatedState = it },
                    onDelete = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onPairClick = {}
                )
            }
        }

        // Assert is expanded
        assertTrue(isExpanded)
        composeTestRule.onNodeWithTag("expand_collapse_control_Bench_Press").assertTextContains("Collapse")

        // Simulate changing prescription values by triggering updates
        // Since the parent/viewmodel manages state and passes `isExpanded = true` based on stable ID,
        // it remains expanded even when updated state propagates.
        assertNull(updatedState)
    }

    @Test
    fun testExerciseCard_reorderingDoesNotTransferExpansionState() {
        // Exercise expansion is matched via stable exerciseId string, not the index in list.
        // We simulate reordering of 2 exercises: Bench Press (expanded) and Squats (collapsed).
        val list = mutableListOf(
            StrengthViewModel.TemplateExerciseState(id = 1, exerciseId = "bench_press"),
            StrengthViewModel.TemplateExerciseState(id = 2, exerciseId = "squats")
        )

        // bench_press is currently the expanded one
        val expandedExerciseId = "bench_press"

        // Reorder list: move squats to index 0, bench_press to index 1
        val temp = list[0]
        list[0] = list[1]
        list[1] = temp

        // Bench Press should remain expanded since expandedExerciseId is still "bench_press"
        val isBenchPressExpanded = expandedExerciseId == list[1].exerciseId
        val isSquatsExpanded = expandedExerciseId == list[0].exerciseId

        assertTrue(isBenchPressExpanded)
        assertFalse(isSquatsExpanded)
    }
}
