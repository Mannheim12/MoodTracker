package com.example.moodtracker

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * Application class for initialization
 */
class MoodTrackerApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        // Initialize any required components
        // WorkManager will be initialized automatically since we implement Configuration.Provider

        // Check if mood tracking was running before
        val prefs = getSharedPreferences("mood_tracker_prefs", Context.MODE_PRIVATE)
        val wasTracking = prefs.getBoolean("was_tracking", false)

        // If tracking was active, restart it
        if (wasTracking) {
            com.example.moodtracker.worker.MoodCheckWorker.schedule(this)
        }
    }

    /**
     * Provide WorkManager configuration
     * This is required since we disabled the default initializer in the manifest
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}