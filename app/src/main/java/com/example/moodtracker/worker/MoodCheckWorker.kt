package com.example.moodtracker.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.moodtracker.R
import com.example.moodtracker.model.Constants
import com.example.moodtracker.ui.MoodSelectionActivity
import com.example.moodtracker.util.ConfigManager
import com.example.moodtracker.util.DataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * Worker class that handles mood check scheduling and notifications
 */
class MoodCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val configManager = ConfigManager(context)
    private val dataManager = DataManager(context)
    private val random = Random()
    private val prefs: SharedPreferences = applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        // Constants
        const val PREF_NAME = "mood_tracker_prefs"
        const val PREF_WAS_TRACKING = "was_tracking"
        const val PREF_NEXT_CHECK_TIME = "next_check_time"
        const val PREF_LAST_CHECK_TIME = "last_check_time"
        const val PREF_LAST_NOTIFICATION_ID = "last_notification_id"
        const val PREF_HOURLY_ID = "hourly_id" // ID for the current hour in format YYYYMMDDHH
        const val UNIQUE_WORK_NAME = "mood_check_worker"
        private const val INITIAL_DELAY_SECS = 10L // Short delay for first run after boot

        /**
         * Schedule the mood check worker
         * This is a placeholder that will be expanded with full scheduling logic
         */
        fun scheduleCheck(
            context: Context,
            isImmediate: Boolean = false,
            isBoot: Boolean = false
        ) {
            val workManager = WorkManager.getInstance(context)
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

            // For boot recovery, check if tracking was active
            if (isBoot && !prefs.getBoolean(PREF_WAS_TRACKING, false)) {
                return
            }

            // Cancel any existing work
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)

            // Simple delay logic (will be replaced with full scheduling algorithm)
            val delayMillis = when {
                isImmediate -> 0L
                isBoot -> {
                    val nextCheckFromPrefs = prefs.getLong(PREF_NEXT_CHECK_TIME, 0)
                    val now = System.currentTimeMillis()

                    if (nextCheckFromPrefs > now + 60000) {
                        nextCheckFromPrefs - now
                    } else {
                        60000L
                    }
                }
                else -> {
                    // 1 minute for testing - TO BE REPLACED with full scheduling algorithm
                    60000L
                }
            }

            // Update SharedPreferences
            val now = System.currentTimeMillis()
            prefs.edit()
                .putBoolean(PREF_WAS_TRACKING, true)
                .putLong(PREF_NEXT_CHECK_TIME, now + delayMillis)
                .apply()

            // Create work request
            val workRequest = OneTimeWorkRequestBuilder<MoodCheckWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(Constants.WORKER_TAG)
                .build()

            // Enqueue as unique work
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        try {
            // Handle missed previous check (if any)
            checkAndRecordMissedMood()

            // Current hour ID for database entry
            val currentHourId = dataManager.generateHourId()
            prefs.edit().putString(PREF_HOURLY_ID, currentHourId).apply()

            // Record current check time
            val now = System.currentTimeMillis()
            prefs.edit()
                .putLong(PREF_LAST_CHECK_TIME, now)
                .apply()

            // Show notification
            showMoodCheckNotification()

            // Schedule next check
            // TO DO: This will be replaced with full scheduling algorithm
            scheduleCheck(applicationContext)

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    /**
     * Check if previous notification was missed and record "Asleep" if needed
     */
    private suspend fun checkAndRecordMissedMood() = withContext(Dispatchers.IO) {
        val lastCheckTime = prefs.getLong(PREF_LAST_CHECK_TIME, 0)
        val lastHourId = prefs.getString(PREF_HOURLY_ID, "")

        // If we have a previous check recorded
        if (lastCheckTime > 0 && !lastHourId.isNullOrEmpty()) {
            // Check if we already have a mood entry for that hour
            val hasEntry = dataManager.hasEntryForHour(lastHourId)

            // If no entry exists, record "Asleep"
            if (!hasEntry) {
                dataManager.addMoodEntry("Asleep", lastHourId, lastCheckTime)
            }
        }
    }

    private fun showMoodCheckNotification() {
        // Create intent for notification tap
        val intent = Intent(applicationContext, MoodSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Create pending intent
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val notification = NotificationCompat.Builder(applicationContext, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mood_tracker)
            .setContentTitle(applicationContext.getString(R.string.notification_title))
            .setContentText(applicationContext.getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // Show the notification
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)

        // Save notification ID
        prefs.edit().putInt(PREF_LAST_NOTIFICATION_ID, Constants.NOTIFICATION_ID).apply()
    }
}