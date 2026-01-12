package com.example.moodtracker.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.moodtracker.R
import com.example.moodtracker.model.Constants
import com.example.moodtracker.ui.ComposeMoodSelectionActivity
import com.example.moodtracker.util.ConfigManager
import com.example.moodtracker.util.DataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

/**
 * Worker class that handles mood check scheduling and notifications
 * Each worker schedules the next worker with a random interval and shows a notification
 * that persists until the next check.
 */
class MoodCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private val dataManager = DataManager(applicationContext)
    private val configManager = ConfigManager(applicationContext)
    private val prefs: SharedPreferences = applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        // Constants
        const val PREF_NAME = "mood_tracker_prefs"
        const val PREF_WAS_TRACKING = "was_tracking"
        const val PREF_NEXT_CHECK_TIME = "next_check_time"
        const val PREF_LAST_CHECK_TIME = "last_check_time"
        const val PREF_HOURLY_ID = "hourly_id" // ID for the current hour in format YYYYMMDDHH
        const val UNIQUE_WORK_NAME = "mood_check_worker"

        /**
         * Initiates or immediately triggers mood tracking
         * @param context Application context
         * @param isImmediate If true, shows notification immediately
         */
        fun startTracking(context: Context, isImmediate: Boolean = false) {
            // Cancel any existing work first
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)

            // Calculate initial delay
            // TODO 'Check Now' button temporarily sets interval to 10 seconds
            val delayMillis = if (isImmediate) 10000L else calculateNextInterval(context)

            // Update tracking state
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            prefs.edit()
                .putBoolean(PREF_WAS_TRACKING, true)
                .putLong(PREF_NEXT_CHECK_TIME, now + delayMillis)
                .apply()

            // Schedule the work
            val workRequest = OneTimeWorkRequestBuilder<MoodCheckWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .addTag(Constants.WORKER_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        /**
         * Stops mood tracking completely
         */
        fun stopTracking(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)

            // Update SharedPreferences
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(PREF_WAS_TRACKING, false)
                .apply()

            // Cancel any existing notification
            cancelNotification(context)
        }

        /**
         * Calculates the next interval based on config settings and hourly constraints
         * Scheduling uses local timezone so notifications appear at reasonable local times
         * The next check will be a random time that satisfies:
         * 1. Within hour X+1 (in local time)
         * 2. Between 30-90 minutes from now (or configured interval)
         */
        fun calculateNextInterval(context: Context): Long {
            val configManager = ConfigManager(context)
            val config = configManager.loadConfig()

            val minIntervalMinutes = config.minIntervalMinutes
            val maxIntervalMinutes = config.maxIntervalMinutes

            val now = System.currentTimeMillis()

            // Use explicit local timezone for scheduling (notifications should appear at reasonable local times)
            val localTimeZone = TimeZone.getDefault()
            val calendar = Calendar.getInstance(localTimeZone).apply {
                timeInMillis = now
                add(Calendar.HOUR_OF_DAY, 1) // Move to next local hour
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val nextHourStart = calendar.timeInMillis

            // Set end of next hour with buffer
            calendar.apply {
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 44) // 15 second buffer to avoid edge cases
                set(Calendar.MILLISECOND, 999)
            }
            val nextHourEnd = calendar.timeInMillis

            // Calculate interval constraints from current time
            val minTime = now + TimeUnit.MINUTES.toMillis(minIntervalMinutes.toLong())
            val maxTime = now + TimeUnit.MINUTES.toMillis(maxIntervalMinutes.toLong())

            // Find the valid scheduling window (intersection of hour constraint and interval constraint)
            val validStart = maxOf(nextHourStart, minTime)
            val validEnd = minOf(nextHourEnd, maxTime)

            // Pick a random time in the valid range
            val randomOffset = if (validEnd > validStart) {
                ThreadLocalRandom.current().nextLong(validEnd - validStart + 1)
            } else {
                0L // Fallback: schedule immediately if no valid range
            }

            val nextCheckTime = validStart + randomOffset
            return nextCheckTime - now
        }

        /**
         * Check if mood tracking is currently active
         * @param context Application context
         * @return true if tracking is active, false otherwise
         */
        fun isTrackingActive(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(PREF_WAS_TRACKING, false)
        }

        fun cancelNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(Constants.NOTIFICATION_ID)
        }
    }

    /**
     * Main work execution method - processes current mood check and schedules next one
     */
    override suspend fun doWork(): Result {
        try {
            // Check if tracking is still active
            if (!prefs.getBoolean(PREF_WAS_TRACKING, false)) {
                // Tracking was disabled while worker was waiting
                return Result.success()
            }

            // Record current time
            val now = System.currentTimeMillis()

            // Generate hourly ID for this check (UTC-based for data consistency)
            val currentHourId = withContext(Dispatchers.IO) {
                dataManager.generateHourId(now)
            }

            // Handle previous notification if not already handled
            withContext(Dispatchers.IO) {
                // Check if the previous hour was in a sleep window and needs to be auto-recorded as "Asleep"
                val previousHourId = prefs.getString(PREF_HOURLY_ID, null)
                if (previousHourId != null) {
                    // Check if this hour was in a sleep window
                    if (configManager.isHourIdInSleepWindow(previousHourId)) {
                        // Check if it wasn't already filled in by the user
                        val existingEntry = dataManager.getEntryByHourId(previousHourId)
                        if (existingEntry == null) {
                            // Auto-record as "Asleep"
                            val previousTimestamp = configManager.convertUtcHourIdToTimestamp(previousHourId)
                            dataManager.addMoodEntry("Asleep", previousHourId, previousTimestamp ?: now)
                        }
                    }
                }

                cancelNotification(applicationContext)
            }

            // Store the current hour ID for this check
            prefs.edit().putString(PREF_HOURLY_ID, currentHourId).apply()

            // Update last check time
            prefs.edit().putLong(PREF_LAST_CHECK_TIME, now).apply()

            // Show the mood check notification
            showMoodCheckNotification(currentHourId)

            // Schedule next work
            val nextIntervalMillis = calculateNextInterval(applicationContext)
            val nextCheckTime = now + nextIntervalMillis
            prefs.edit().putLong(PREF_NEXT_CHECK_TIME, nextCheckTime).apply()

            val workRequest = OneTimeWorkRequestBuilder<MoodCheckWorker>()
                .setInitialDelay(nextIntervalMillis, TimeUnit.MILLISECONDS)
                .addTag(Constants.WORKER_TAG)
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    /**
     * Shows the notification that persists until next check
     * Uses ConfigManager for consistent time formatting
     */
    private fun showMoodCheckNotification(hourIdForSelection: String) {
        // Create intent for notification tap
        val intent = Intent(applicationContext, ComposeMoodSelectionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Create pending intent
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use ConfigManager for consistent hour formatting (already UTC-aware)
        val hourText = configManager.formatHourIdForDisplay(hourIdForSelection)

        // Build the notification
        val notification = NotificationCompat.Builder(applicationContext, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mood_tracker)
            .setContentTitle(applicationContext.getString(R.string.notification_title))
            .setContentText(applicationContext.getString(R.string.notification_text_with_hour, hourText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setOngoing(true)    // Makes it persistent until canceled
            .build()

        // Show the notification
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
    }
}