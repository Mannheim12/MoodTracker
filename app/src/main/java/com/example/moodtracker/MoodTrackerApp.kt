package com.example.moodtracker

import android.app.Application
import androidx.work.Configuration

/**
 * Application class for initialization
 */
class MoodTrackerApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        // Any future initializations can go here
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