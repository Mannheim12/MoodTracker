package com.example.moodtracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Button
import android.widget.TextView
import android.widget.ScrollView
import android.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.moodtracker.model.Constants
import com.example.moodtracker.util.ConfigManager
import com.example.moodtracker.util.DataManager
import com.example.moodtracker.worker.MoodCheckWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.view.View

/**
 * Main entry point for the application - enhanced with debugging info
 */
class MainActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var dataManager: DataManager
    private lateinit var statusText: TextView
    private lateinit var toggleTrackingButton: Button
    private lateinit var stopTrackingButton: Button
    private lateinit var requestPermissionsButton: Button
    private lateinit var refreshButton: Button
    private lateinit var viewDatabaseButton: Button
    private lateinit var debugInfoText: TextView
    private lateinit var batteryOptimizationText: TextView

    // Handler for periodic UI updates
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDebugInfo()
            updateButtonStates()
            handler.postDelayed(this, 10000) // Update every 10 seconds
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+ (API 33+), include notification permission
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.VIBRATE
                // Commenting out storage permissions until interface issues are resolved
                // Manifest.permission.READ_EXTERNAL_STORAGE,
                // Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        } else {
            // For older versions
            arrayOf(
                Manifest.permission.VIBRATE
                // Commenting out storage permissions until interface issues are resolved
                // Manifest.permission.READ_EXTERNAL_STORAGE,
                // Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configManager = ConfigManager(this)
        dataManager = DataManager(this)

        // Initialize UI elements
        statusText = findViewById(R.id.status_text)
        batteryOptimizationText = findViewById(R.id.battery_optimization_text)
        toggleTrackingButton = findViewById(R.id.toggle_tracking_button)
        stopTrackingButton = findViewById(R.id.stop_tracking_button)
        requestPermissionsButton = findViewById(R.id.request_permissions_button)
        refreshButton = findViewById(R.id.refresh_button)
        debugInfoText = findViewById(R.id.debug_info_text)

        // Set up button click listeners
        toggleTrackingButton.setOnClickListener {
            toggleTracking()
        }

        stopTrackingButton.setOnClickListener {
            stopTracking()
        }

        requestPermissionsButton.setOnClickListener {
            requestPermissions()
        }

        viewDatabaseButton = findViewById(R.id.view_database_button)
        viewDatabaseButton.setOnClickListener {
            viewDatabase()
        }

        refreshButton.setOnClickListener {
            updateDebugInfo()
            updateButtonStates()
        }

        // Initial UI update
        updateDebugInfo()
        updateButtonStates()
        updateBatteryOptimizationStatus()
    }

    /**
     * Runs when the app is resumed to ensure proper state
     */
    override fun onResume() {
        super.onResume()
        // Update UI when returning to the app
        MoodCheckWorker.checkTrackingConsistency(this)
        updateDebugInfo()
        updateButtonStates()
        updateBatteryOptimizationStatus()

        // Start periodic updates
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic updates when app is not in foreground
        handler.removeCallbacks(updateRunnable)
    }

    /**
     * Toggles between starting tracking and triggering an immediate check
     * based on current tracking state
     */
    private fun toggleTracking() {
        if (!checkPermissions()) {
            statusText.setText(R.string.permissions_needed)
            return
        }

        // Check tracking consistency first
        MoodCheckWorker.checkTrackingConsistency(this)

        // Get current state
        val isActive = getSharedPreferences(MoodCheckWorker.PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(MoodCheckWorker.PREF_WAS_TRACKING, false)

        if (isActive) {
            // If active, just trigger an immediate check
            triggerMoodCheckNow()
        } else {
            // If not active, start tracking
            startMoodTracking()
        }

        // Update button states after action
        updateButtonStates()
    }

    /**
     * Start mood tracking by scheduling a worker
     */
    private fun startMoodTracking() {
        // Start tracking with standard delay
        MoodCheckWorker.startTracking(this, isImmediate = false)

        // Update UI
        statusText.setText(R.string.service_running)
        updateButtonStates()
    }

    /**
     * Trigger a mood check immediately
     */
    private fun triggerMoodCheckNow() {
        try {
            MoodCheckWorker.startTracking(this, isImmediate = true)

            // Update UI to give feedback
            statusText.setText(R.string.check_triggered)
            // Reset status text after 3 seconds
            handler.postDelayed({
                statusText.setText(R.string.service_running)
            }, 3000)
        } catch (e: Exception) {
            statusText.setText(R.string.trigger_error)
            e.printStackTrace()
        }

        updateButtonStates()
    }

    /**
     * Stop all mood tracking by canceling all workers
     */
    private fun stopTracking() {
        MoodCheckWorker.stopTracking(this)

        // Update UI
        statusText.setText(R.string.tracking_stopped)
        updateButtonStates()
    }

    /**
     * Request all required permissions that haven't been granted yet
     */
    private fun requestPermissions() {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            statusText.setText(R.string.permissions_requesting)
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        } else {
            statusText.setText(R.string.permissions_granted)
        }

        updateButtonStates()
    }

    /**
     * Check if any permissions are missing
     */
    private fun checkPermissions(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    /**
     * Update the text and enabled state of all buttons based on current app state
     */
    private fun updateButtonStates() {
        // Update permission button state
        val allPermissionsGranted = checkPermissions()
        requestPermissionsButton.isEnabled = !allPermissionsGranted

        // Update tracking button state
        val isActive = getSharedPreferences(MoodCheckWorker.PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(MoodCheckWorker.PREF_WAS_TRACKING, false)
        toggleTrackingButton.setText(if (isActive) R.string.check_now else R.string.start_tracking)
        stopTrackingButton.isEnabled = isActive
    }

    /**
     * Update the battery optimization state on app startup or resume
     */
    private fun updateBatteryOptimizationStatus() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        batteryOptimizationText.visibility = if (isIgnoringBatteryOptimizations) View.GONE else View.VISIBLE
    }

    // Handle permission request result
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted
                statusText.setText(R.string.permissions_granted)
            } else {
                // Some permissions denied
                statusText.setText(R.string.permissions_required)
            }

            updateButtonStates()
        }
    }

    private fun updateDebugInfo() {
        CoroutineScope(Dispatchers.Main).launch {
            val info = StringBuilder()

            // Worker Status
            info.append("WORKER STATUS\n")
            info.append("=============\n")
            val workerInfo = withContext(Dispatchers.Default) {
                debugWorkerStatus()
            }
            info.append(workerInfo)
            info.append("\n\n")

            // Permissions Status
            info.append("PERMISSIONS STATUS\n")
            info.append("==================\n")
            info.append(debugPermissionsStatus())
            info.append("\n\n")

            // File System Status
            info.append("FILE SYSTEM STATUS\n")
            info.append("==================\n")
            val fileInfo = withContext(Dispatchers.IO) {
                debugFileSystem()
            }
            info.append(fileInfo)
            info.append("\n\n")

            // Configuration Info
            info.append("CONFIGURATION\n")
            info.append("=============\n")
            val configInfo = withContext(Dispatchers.IO) {
                debugConfigInfo()
            }
            info.append(configInfo)

            // Update the UI
            debugInfoText.text = info.toString()
        }
    }

    private suspend fun debugFileSystem(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()

        // Database stats
        sb.append("Database: ")
        try {
            val entryCount = dataManager.getEntryCount()
            sb.append("$entryCount entries\n")

            // Most recent entry
            val recentEntry = dataManager.getMostRecentEntry()
            if (recentEntry != null) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val formattedDate = dateFormat.format(Date(recentEntry.timestamp))
                sb.append("Latest entry: ${recentEntry.id} at $formattedDate (${recentEntry.moodName})\n")
            } else {
                sb.append("No entries yet\n")
            }

        } catch (e: Exception) {
            sb.append("Error reading database: ${e.message}\n")
        }

        sb.append("\n")

        // CSV Export info
        sb.append("CSV Export Path: ")
        val exportPath = dataManager.getExportPath()
        sb.append("$exportPath\n")

        return@withContext sb.toString()
    }

    private fun debugPermissionsStatus(): String {
        val sb = StringBuilder()

        for (permission in REQUIRED_PERMISSIONS) {
            val granted = ContextCompat.checkSelfPermission(
                this, permission) == PackageManager.PERMISSION_GRANTED
            val permissionName = permission.substringAfterLast(".")
            sb.append("$permissionName: ${if (granted) "GRANTED" else "DENIED"}\n")
        }

        // Check battery optimization status
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        sb.append("Battery Optimization Exempt: ${if (isIgnoringBatteryOptimizations) "YES" else "NO"}\n")

        return sb.toString()
    }

    private fun debugWorkerStatus(): String {
        val sb = StringBuilder()

        // Check if worker is scheduled
        val workManager = WorkManager.getInstance(applicationContext)

        // Get work info without blocking
        val workInfos = try {
            // This isn't truly non-blocking but safer than direct .get()
            val workInfosFuture = workManager.getWorkInfosByTag(Constants.WORKER_TAG)
            if (workInfosFuture.isDone) {
                workInfosFuture.get()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }

        // Get next check time from SharedPreferences
        val prefs = getSharedPreferences("mood_tracker_prefs", Context.MODE_PRIVATE)
        val nextCheckTime = prefs.getLong("next_check_time", 0)
        val isRetry = prefs.getBoolean("is_retry", false)

        // Display next check time prominently at the top
        if (nextCheckTime > 0) {
            val nextCheckDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(nextCheckTime))
            sb.append("NEXT MOOD CHECK: $nextCheckDate\n")
            sb.append("Check Type: ${if (isRetry) "RETRY" else "REGULAR"}\n")

            // Time until next check
            val minutesUntil = TimeUnit.MILLISECONDS.toMinutes(nextCheckTime - System.currentTimeMillis())
            val secondsUntil = TimeUnit.MILLISECONDS.toSeconds(nextCheckTime - System.currentTimeMillis()) % 60
            sb.append("Time until next check: $minutesUntil min $secondsUntil sec\n\n")
        } else {
            sb.append("NEXT MOOD CHECK: Not scheduled\n\n")
        }

        // Rest of the worker status information
        if (workInfos.isNotEmpty()) {
            sb.append("Worker Status: SCHEDULED\n")

            // Check each work info
            for (workInfo in workInfos) {
                sb.append("ID: ${workInfo.id}\n")
                sb.append("State: ${workInfo.state}\n")
            }
        } else {
            sb.append("Worker Status: NOT SCHEDULED\n")
        }

        // Last check time
        val lastCheckTime = prefs.getLong("last_check_time", 0)
        if (lastCheckTime > 0) {
            val lastCheckDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(lastCheckTime))
            sb.append("\nLast Check: $lastCheckDate\n")

            // Time since last check
            val minutesSince = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - lastCheckTime)
            sb.append("Time since last check: $minutesSince minutes\n")
        } else {
            sb.append("\nLast Check: NEVER\n")
        }

        // Tracking status from SharedPreferences
        val wasTracking = prefs.getBoolean("was_tracking", false)
        sb.append("\nTracking Enabled: ${if (wasTracking) "YES" else "NO"}\n")

        return sb.toString()
    }

    private fun debugConfigInfo(): String {
        val sb = StringBuilder()

        try {
            // Load config values
            val config = configManager.loadConfig()
            sb.append("Min Interval: ${config["min_interval_minutes"] ?: "N/A"} minutes\n")
            sb.append("Max Interval: ${config["max_interval_minutes"] ?: "N/A"} minutes\n")
            sb.append("Retry Window: ${config["retry_window_minutes"] ?: "N/A"} minutes\n")

            // Load moods
            val moods = configManager.loadMoods()
            sb.append("\nMoods Loaded: ${moods.size}\n")

            // Count moods by dimension
            val positiveMoods = moods.count { it.dimension1 == "Positive" }
            val neutralMoods = moods.count { it.dimension1 == "Neutral" }
            val negativeMoods = moods.count { it.dimension1 == "Negative" }
            val otherMoods = moods.count { it.category == "Other" }

            sb.append("Positive Moods: $positiveMoods\n")
            sb.append("Neutral Moods: $neutralMoods\n")
            sb.append("Negative Moods: $negativeMoods\n")
            sb.append("Other Moods: $otherMoods\n")

        } catch (e: Exception) {
            sb.append("Error loading configuration: ${e.message}\n")
            e.printStackTrace()
        }

        return sb.toString()
    }

    private fun viewDatabase() {
        // Launch in a coroutine
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Get database entries
                val entries = withContext(Dispatchers.IO) {
                    dataManager.getAllEntries()
                }

                if (entries.isEmpty()) {
                    // Show message if database is empty
                    statusText.text = getString(R.string.database_is_empty)
                    return@launch
                }

                // Create a simple display text for the entries
                val entriesText = buildString {
                    // Format each entry
                    for (entry in entries) {
                        val date = Date(entry.timestamp)
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                        append("ID: ${entry.id} | ${dateFormat.format(date)} | ${entry.moodName}\n")
                    }
                }

                // Show in an AlertDialog with ScrollView
                val scrollView = ScrollView(this@MainActivity)
                val textView = TextView(this@MainActivity).apply {
                    text = entriesText
                    setPadding(20, 20, 20, 20)
                    textSize = 14f
                }

                scrollView.addView(textView)

                AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.database_entries, entries.size))
                    .setView(scrollView)
                    .setPositiveButton(R.string.close, null)
                    .show()

            } catch (e: Exception) {
                statusText.text = getString(R.string.error_loading_database, e.message)
                e.printStackTrace()
            }
        }
    }
}