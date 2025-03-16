package com.example.moodtracker

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.media.RingtoneManager
import androidx.work.Configuration
import com.example.moodtracker.model.Constants

/**
 * Application class for initialization
 */
class MoodTrackerApp : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()

        // Create notification channel at app initialization
        createNotificationChannel()
    }

    /**
     * Create notification channel for Mood Check notifications
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.notification_text)
            enableLights(true)
            lightColor = Color.BLUE
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 250, 500)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
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