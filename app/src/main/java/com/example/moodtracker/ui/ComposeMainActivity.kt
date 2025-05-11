package com.example.moodtracker.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
            val context = LocalContext.current

            // Define permissions to request based on Android version
            val permissionsToRequest = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    emptyArray<String>() // No specific runtime permissions needed on launch for older APIs
                    // VIBRATE is generally handled, storage is for specific features.
                }
            }

            val multiplePermissionsLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { }

            LaunchedEffect(Unit) {
                if (permissionsToRequest.isNotEmpty()) {
                    val ungrantedPermissions = permissionsToRequest.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }.toTypedArray()

                    if (ungrantedPermissions.isNotEmpty()) {
                        multiplePermissionsLauncher.launch(ungrantedPermissions)
                    }
                }
            }

            val navController = rememberNavController()
            Scaffold { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(navController = navController)
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
                        popEnterTransition = {
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
                        SettingsScreen(navController = navController)
                    }
                    // AnalyticsScreen can be added here if needed
                    // composable(Screen.Analytics.route) { AnalyticsScreen() }
                }
            }
        }
    }
}

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    // data object Analytics : Screen("analytics") // Example for future screen
}