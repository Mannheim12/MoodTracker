package com.example.moodtracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.moodtracker.theme.MoodTrackerTheme
import com.example.moodtracker.ui.screens.HomeScreen
import com.example.moodtracker.ui.screens.SettingsScreen

class ComposeMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MoodTrackerApp()
        }
    }
}

@Composable
fun MoodTrackerApp() {
    MoodTrackerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            // The Scaffold no longer has a topBar defined here
            Scaffold { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.padding(innerPadding) // Apply padding from the main Scaffold
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(navController = navController) // Pass NavController
                    }
                    composable(
                        route = Screen.Settings.route,
                        enterTransition = {
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Up,
                                animationSpec = tween(300)
                            )
                        },
                        exitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Down,
                                animationSpec = tween(300)
                            )
                        },
                        popEnterTransition = { // Kept for consistency, though might not be visible with Up/Down
                            slideIntoContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Up,
                                animationSpec = tween(300)
                            )
                        },
                        popExitTransition = {
                            slideOutOfContainer(
                                towards = AnimatedContentTransitionScope.SlideDirection.Down,
                                animationSpec = tween(300)
                            )
                        }
                    ) {
                        SettingsScreen(navController = navController) // Pass NavController
                    }
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    // Consider adding other screens here if needed, e.g., AnalyticsScreen
    // data object Analytics : Screen("analytics")
}