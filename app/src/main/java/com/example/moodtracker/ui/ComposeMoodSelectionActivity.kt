package com.example.moodtracker.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.moodtracker.theme.MoodTrackerTheme
import com.example.moodtracker.ui.screens.MoodSelectionScreen
import com.example.moodtracker.util.ConfigManager

class ComposeMoodSelectionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContent {
            // Read config to get theme preference
            val config = ConfigManager(this).loadConfig()
            val darkTheme = when (config.appTheme) {
                ConfigManager.AppTheme.LIGHT -> false
                ConfigManager.AppTheme.DARK -> true
                else -> isSystemInDarkTheme()  // System default
            }

            // Apply theme
            MoodTrackerTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MoodSelectionScreen(
                        onCloseScreen = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    /**
     * This method is called when the activity is re-launched while at the top of the
     * activity stack instead of a new instance of the activity being started.
     * The Intent parameter from the framework is non-nullable here.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}