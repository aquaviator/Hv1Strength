package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.AuthState
import com.example.data.StrengthDatabase
import com.example.data.StrengthRepository
import com.example.ui.screens.*
import com.example.ui.theme.HumanV1Theme
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StrengthViewModel
import com.example.ui.viewmodel.StrengthViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    android.util.Log.i("MainActivity", "onCreate started")

    // Check for previous crash logs
    val crashLogFile = java.io.File(filesDir, "crash_log.txt")
    var crashLogContent: String? = null
    if (crashLogFile.exists()) {
        crashLogContent = crashLogFile.readText()
        crashLogFile.delete()
    }

    // Install a local UI-based crash handler for this activity's UI thread
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        val stackTrace = android.util.Log.getStackTraceString(throwable)
        java.io.File(filesDir, "crash_log.txt").writeText("FATAL EXCEPTION in ${thread.name}:\n$stackTrace")
        defaultHandler?.uncaughtException(thread, throwable)
    }

    enableEdgeToEdge()

    if (crashLogContent != null) {
        setContent {
            MyApplicationTheme {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            // We don't have vertical scroll imported here easily, but just standard modifier
                    ) {
                        androidx.compose.material3.Text(
                            "App Crashed Previously!",
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                        androidx.compose.material3.Text(
                            crashLogContent!!,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                        androidx.compose.material3.Button(onClick = { startApp() }) {
                            androidx.compose.material3.Text("Retry Normal Startup")
                        }
                    }
                }
            }
        }
    } else {
        startApp()
    }
  }

  private fun startApp() {
    try {
        android.util.Log.i("MainActivity", "Initializing database...")
        val database = StrengthDatabase.getDatabase(applicationContext, lifecycleScope)
        
        android.util.Log.i("MainActivity", "Database initialized. Initializing repository...")
        val repository = StrengthRepository(database.strengthDao(), applicationContext)
        
        android.util.Log.i("MainActivity", "Repository initialized. Scheduling background sync...")
        com.example.core.sync.SyncScheduler.schedulePeriodic(applicationContext)
        android.util.Log.i("MainActivity", "Background sync scheduled successfully.")

        setContent {
          val viewModel: StrengthViewModel = viewModel(
            factory = StrengthViewModelFactory(repository, applicationContext)
          )
          val themeMode by viewModel.theme.collectAsState()
          val isDark = when (themeMode.lowercase()) {
              "light" -> false
              "dark" -> true
              else -> androidx.compose.foundation.isSystemInDarkTheme()
          }
          HumanV1Theme(darkTheme = isDark) {
            MainAppScreen(viewModel)
          }
        }
    } catch (e: Throwable) {
        val stackTrace = android.util.Log.getStackTraceString(e)
        android.util.Log.e("MainActivity", "FATAL: Error during MainActivity onCreate initialization", e)
        
        setContent {
            MyApplicationTheme {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.errorContainer
                ) {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        androidx.compose.material3.Text(
                            "Initialization Error",
                            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                        androidx.compose.material3.Text(
                            stackTrace,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
  }
}

@Composable
fun MainAppScreen(
    viewModel: StrengthViewModel,
    navController: androidx.navigation.NavHostController = rememberNavController()
) {
    val authState by viewModel.authState.collectAsState()
    val vibrationOn by viewModel.vibrationOn.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    CompositionLocalProvider(
        com.example.core.util.LocalVibrationEnabled provides vibrationOn
    ) {
        val navigationItems = listOf(
        NavigationItem("workout", "Workout", Icons.Default.FitnessCenter),
        NavigationItem("history", "History", Icons.Default.History),
        NavigationItem("exercises", "Exercises", Icons.Default.List),
        NavigationItem("progress", "Progress", Icons.Default.TrendingUp),
        NavigationItem("settings", "Settings", Icons.Default.Settings)
    )

    val currentTab = remember(currentRoute) {
        if (currentRoute == null) null
        else when {
            currentRoute == "workout" || currentRoute == "active_workout" || currentRoute.startsWith("workout_summary") -> "workout"
            currentRoute == "history" -> "history"
            currentRoute == "exercises" -> "exercises"
            currentRoute == "progress" -> "progress"
            currentRoute == "settings" || currentRoute == "profile" || currentRoute == "sync_debug" -> "settings"
            else -> null
        }
    }

    val navigateToTab = remember(navController, currentRoute, currentTab) {
        { targetRoute: String ->
            val hasWorkoutInBackStack = navController.currentBackStack.value.any { it.destination.route == "workout" }
            if (currentTab == targetRoute) {
                // Tap current tab while on child route -> return to that tab's root
                if (currentRoute != targetRoute) {
                    if (targetRoute == "workout" && hasWorkoutInBackStack) {
                        navController.popBackStack("workout", inclusive = false)
                    } else {
                        try {
                            navController.popBackStack(targetRoute, inclusive = false)
                        } catch (e: Exception) {
                            navController.navigate(targetRoute) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                }
            } else {
                // Tap another tab -> restore state
                if (targetRoute == "workout" && hasWorkoutInBackStack) {
                    navController.popBackStack("workout", inclusive = false)
                } else {
                    navController.navigate(targetRoute) {
                        if (hasWorkoutInBackStack) {
                            popUpTo("workout") {
                                // Only save state if we are not leaving a transient post-workout summary screen
                                saveState = !currentRoute.orEmpty().startsWith("workout_summary")
                            }
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(viewModel.snackbarHostState) },
        bottomBar = {
            // Hide bottom bar for active workout or welcome screen
            if (currentRoute != "active_workout" && currentRoute != "welcome" && authState !is AuthState.Initial) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp
                    ) {
                        navigationItems.forEach { item ->
                            val isSelected = currentTab == item.route
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, style = MaterialTheme.typography.labelMedium, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                selected = isSelected,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                ),
                                onClick = {
                                    navigateToTab(item.route)
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (authState is AuthState.Initial) "welcome" else "workout",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("welcome") {
                WelcomeScreen(
                    viewModel = viewModel,
                    onNavigateToHome = {
                        navController.navigate("workout") {
                            popUpTo("welcome") { inclusive = true }
                        }
                    }
                )
            }
            composable("profile") {
                ProfileScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onSignOutComplete = {
                        navController.navigate("welcome") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable("workout") {
                WorkoutScreen(
                    viewModel = viewModel,
                    onNavigateToActiveWorkout = {
                        navController.navigate("active_workout")
                    },
                    onNavigateToProfile = {
                        navController.navigate("profile") { launchSingleTop = true }
                    }
                )
            }
            composable("history") {
                HistoryScreen(
                    viewModel = viewModel,
                    onNavigateToProfile = {
                        navController.navigate("profile") { launchSingleTop = true }
                    }
                )
            }
            composable("exercises") {
                ExerciseScreen(
                    viewModel = viewModel,
                    onNavigateToProfile = {
                        navController.navigate("profile") { launchSingleTop = true }
                    }
                )
            }
            composable("progress") {
                ProgressScreen(
                    viewModel = viewModel,
                    onNavigateToProfile = {
                        navController.navigate("profile") { launchSingleTop = true }
                    }
                )
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToProfile = {
                        navController.navigate("profile") { launchSingleTop = true }
                    },
                    onNavigateToSyncDebug = {
                        navController.navigate("sync_debug")
                    }
                )
            }
            composable("sync_debug") {
                SyncDebugScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable("active_workout") {
                ActiveWorkoutScreen(
                    viewModel = viewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onNavigateToSummary = { completedId ->
                        navController.navigate("workout_summary/$completedId") {
                            // Pop active_workout off the back stack to avoid going back to it
                            popUpTo("workout") { inclusive = false }
                        }
                    }
                )
            }
            composable("workout_summary/{completedWorkoutId}") { backStackEntry ->
                val workoutIdStr = backStackEntry.arguments?.getString("completedWorkoutId")
                val workoutId = workoutIdStr?.toIntOrNull() ?: -1
                WorkoutSummaryScreen(
                    viewModel = viewModel,
                    completedWorkoutId = workoutId,
                    onNavigateToHistory = {
                        navigateToTab("history")
                    },
                    onNavigateToWorkouts = {
                        navigateToTab("workout")
                    }
                )
            }
        }
    }
}
}

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
