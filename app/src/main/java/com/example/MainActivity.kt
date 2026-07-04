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
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StrengthViewModel
import com.example.ui.viewmodel.StrengthViewModelFactory

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Initialize database and repository reactively
    val database = StrengthDatabase.getDatabase(applicationContext, lifecycleScope)
    val repository = StrengthRepository(database.strengthDao(), applicationContext)

    // Schedule background synchronization
    com.example.core.sync.SyncScheduler.schedulePeriodic(applicationContext)

    setContent {
      MyApplicationTheme {
        val viewModel: StrengthViewModel = viewModel(
          factory = StrengthViewModelFactory(repository, applicationContext)
        )
        MainAppScreen(viewModel)
      }
    }
  }
}

@Composable
fun MainAppScreen(viewModel: StrengthViewModel) {
    val authState by viewModel.authState.collectAsState()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navigationItems = listOf(
        NavigationItem("workout", "Workout", Icons.Default.FitnessCenter),
        NavigationItem("history", "History", Icons.Default.History),
        NavigationItem("exercises", "Exercises", Icons.Default.List),
        NavigationItem("progress", "Progress", Icons.Default.TrendingUp),
        NavigationItem("settings", "Settings", Icons.Default.Settings)
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(viewModel.snackbarHostState) },
        bottomBar = {
            // Hide bottom bar for active workout, welcome screen, or profile screen
            if (currentRoute != "active_workout" && currentRoute != "welcome" && currentRoute != "profile" && currentRoute != "sync_debug" && authState !is AuthState.Initial) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        thickness = 1.dp
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = 0.dp
                    ) {
                        navigationItems.forEach { item ->
                            val isSelected = currentRoute == item.route
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                selected = isSelected,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                                    selectedTextColor = MaterialTheme.colorScheme.onSecondary,
                                    indicatorColor = MaterialTheme.colorScheme.secondary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.startDestinationId) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
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
                    }
                )
            }
            composable("history") {
                HistoryScreen(viewModel = viewModel)
            }
            composable("exercises") {
                ExerciseScreen(viewModel = viewModel)
            }
            composable("progress") {
                ProgressScreen(viewModel = viewModel)
            }
            composable("settings") {
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToProfile = {
                        navController.navigate("profile")
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
                    }
                )
            }
        }
    }
}

data class NavigationItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
