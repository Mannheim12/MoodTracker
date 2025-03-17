package com.example.moodtracker.worker

import android.app.Notification
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
import java.util.Calendar
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * Worker class that handles mood check scheduling and notifications
 */
class MoodCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val dataManager = DataManager(context)
    private val configManager = ConfigManager(context)
    private val random = Random()
    private val prefs: SharedPreferences = applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Configuration values (will be loaded from config file)
    private var minIntervalMinutes: Int = Constants.MIN_INTERVAL_MINUTES
    private var maxIntervalMinutes: Int = Constants.MAX_INTERVAL_MINUTES
    private var retryWindowMinutes: Int = Constants.RETRY_WINDOW_MINUTES

    companion object {
        // Making constants public for use in MainActivity
        const val PREF_NAME = "mood_tracker_prefs"
        const val PREF_WAS_TRACKING = "was_tracking"
        const val PREF_IS_RETRY = "is_retry"
        const val PREF_NEXT_CHECK_TIME = "next_check_time"
        const val PREF_LAST_CHECK_TIME = "last_check_time"
        const val UNIQUE_WORK_NAME = "mood_check_worker"
        private const val INITIAL_DELAY_SECS = 10L // Short delay for first run after boot

        /**
         * Schedule the mood check worker with enhanced state tracking
         */
        fun schedule(context: Context, resetSchedule: Boolean = false, immediate: Boolean = false, isRetry: Boolean = false) {
            val workManager = WorkManager.getInstance(context)
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

            // Cancel any existing work
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)

            // Update preferences for manual checks vs retry checks
            if (immediate) {
                prefs.edit()
                    .putBoolean(PREF_IS_RETRY, false) // Manual checks are not retries
                    .apply()
            } else if (isRetry) {
                // Set retry status for retry checks
                prefs.edit()
                    .putBoolean(PREF_IS_RETRY, true)
                    .apply()
            }

            // Create data for worker
            val data = Data.Builder()
                .putBoolean("reset_schedule", resetSchedule)
                .putBoolean("is_immediate", immediate)
                .build()

            // Create work request
            val workRequest = OneTimeWorkRequestBuilder<MoodCheckWorker>()
                .setInputData(data)
                .addTag(Constants.WORKER_TAG)
                // Use zero delay for immediate triggers, otherwise short delay
                .setInitialDelay(if (immediate) 0L else INITIAL_DELAY_SECS, TimeUnit.SECONDS)
                .build()

            // Enqueue as unique work
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            // Update tracking state
            prefs.edit()
                .putBoolean(PREF_WAS_TRACKING, true)
                .putLong(PREF_NEXT_CHECK_TIME, System.currentTimeMillis() +
                        (if (immediate) 0 else INITIAL_DELAY_SECS * 1000))
                .apply()
        }
    }

    override suspend fun doWork(): Result {
        // Get input data
        val resetSchedule = inputData.getBoolean("reset_schedule", false)
        val isImmediate = inputData.getBoolean("is_immediate", false)

        // Check if this is a retry check from preferences
        val isRetry = prefs.getBoolean(PREF_IS_RETRY, false)

        // Load configuration values
        loadConfigValues()

        // Record this check time in shared preferences
        prefs.edit().putLong(PREF_LAST_CHECK_TIME, System.currentTimeMillis()).apply()

        try {
            // Instead of directly launching activity, show a notification
            showMoodCheckNotification()

            // Schedule the next check based on current status
            scheduleNextMoodCheck(resetSchedule, isRetry)

            return Result.success()
        } catch (e: Exception) {
            // If there was an error, try again later
            return Result.retry()
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
        // Note: Channel settings (vibration, lights, etc.) are defined in the app class
        // and don't need to be duplicated here
        val notification = NotificationCompat.Builder(applicationContext, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mood_tracker)
            .setContentTitle(applicationContext.getString(R.string.notification_title))
            .setContentText(applicationContext.getString(R.string.notification_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // For pre-O compatibility
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Marks as important
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Remove when tapped
            .build()

        // Get notification manager
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Show the notification
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
    }

    // Load configuration values from config file
    private suspend fun loadConfigValues() = withContext(Dispatchers.IO) {
        val config = configManager.loadConfig()
        minIntervalMinutes = config["min_interval_minutes"] ?: Constants.MIN_INTERVAL_MINUTES
        maxIntervalMinutes = config["max_interval_minutes"] ?: Constants.MAX_INTERVAL_MINUTES
        retryWindowMinutes = config["retry_window_minutes"] ?: Constants.RETRY_WINDOW_MINUTES
    }

    // Schedule the next mood check
    private fun scheduleNextMoodCheck(resetSchedule: Boolean = false, isRetry: Boolean = false) {
        // Get current time
        val now = System.currentTimeMillis()

        // Calculate next check time (Will be updated with a proper scheduling algorithm later)
        val nextCheckTime = now + (1 * 60 * 1000) // 1 minute in milliseconds

        // Store state in shared preferences
        // Note: in the real logic, would set is_retry based on success/failure,
        // but for now just keep it as false
        prefs.edit()
            .putLong(PREF_NEXT_CHECK_TIME, nextCheckTime)
            .putLong(PREF_LAST_CHECK_TIME, now)
            .putBoolean(PREF_IS_RETRY, false) // Assume success for now
            .putBoolean(PREF_WAS_TRACKING, true)
            .apply()

        // Create work request with 1-minute delay
        val workRequest = OneTimeWorkRequestBuilder<MoodCheckWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES)
            .addTag(Constants.WORKER_TAG)
            .build()

        // Enqueue as unique work
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}