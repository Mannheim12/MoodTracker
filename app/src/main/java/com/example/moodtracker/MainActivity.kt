package com.example.moodtracker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Main entry point for the application - enhanced with debugging info
 */
class MainActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var dataManager: DataManager
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var checkNowButton: Button
    private lateinit var refreshButton: Button
    private lateinit var debugInfoText: TextView

    // Handler for periodic UI updates
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateDebugInfo()
            handler.postDelayed(this, 10000) // Update every 10 seconds
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private val REQUIRED_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.VIBRATE
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.VIBRATE
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_debug)

        configManager = ConfigManager()
        dataManager = DataManager()

        // Initialize UI elements
        statusText = findViewById(R.id.status_text)
        startButton = findViewById(R.id.start_button)
        checkNowButton = findViewById(R.id.check_now_button)
        refreshButton = findViewById(R.id.refresh_button)
        debugInfoText = findViewById(R.id.debug_info_text)

        // Set up button click listeners
        startButton.setOnClickListener {
            if (checkAndRequestPermissions()) {
                startMoodTracking()
            }
        }

        checkNowButton.setOnClickListener {
            if (checkAndRequestPermissions()) {
                triggerMoodCheckNow()
            }
        }

        refreshButton.setOnClickListener {
            updateDebugInfo()
        }

        // Check if permissions are granted on start
        if (checkPermissions()) {
            startButton.setText(R.string.restart_tracking)
            statusText.setText(R.string.service_running)

            // Start auto-updates of debug info
            handler.post(updateRunnable)
        } else {
            startButton.setText(R.string.start_tracking)
            statusText.setText(R.string.permissions_needed)
        }

        // Initial debug info update
        updateDebugInfo()
    }

    override fun onResume() {
        super.onResume()
        // Update debug info when returning to the app
        updateDebugInfo()

        // Start periodic updates if permissions are granted
        if (checkPermissions()) {
            handler.post(updateRunnable)
        }
    }

    override fun onPause() {
        super.onPause()
        // Stop periodic updates when app is not in foreground
        handler.removeCallbacks(updateRunnable)
    }

    private fun startMoodTracking() {
        // Schedule the mood tracking via WorkManager
        MoodCheckWorker.schedule(this, true)

        statusText.setText(R.string.service_running)
        startButton.setText(R.string.restart_tracking)

        // Start auto-updates of debug info
        handler.post(updateRunnable)

        // Update debug info immediately
        updateDebugInfo()
    }

    private fun triggerMoodCheckNow() {
        try {
            // Direct call - no coroutine needed since schedule() is not suspending
            MoodCheckWorker.schedule(applicationContext, immediate = true)

            statusText.setText(R.string.check_triggered)
            // Reset status text after 3 seconds
            handler.postDelayed({
                statusText.setText(R.string.service_running)
            }, 3000)
        } catch (e: Exception) {
            statusText.setText(R.string.trigger_error)
            e.printStackTrace()
        }
    }

    private fun updateDebugInfo() {
        CoroutineScope(Dispatchers.Main).launch {
            val info = StringBuilder()

            // File System Status
            info.append("FILE SYSTEM STATUS\n")
            info.append("=================\n")
            val fileInfo = withContext(Dispatchers.IO) {
                checkFileSystem()
            }
            info.append(fileInfo)
            info.append("\n\n")

            // Permissions Status
            info.append("PERMISSIONS STATUS\n")
            info.append("=================\n")
            info.append(checkPermissionsStatus())
            info.append("\n\n")

            // Worker Status
            info.append("WORKER STATUS\n")
            info.append("=============\n")
            val workerInfo = withContext(Dispatchers.Default) {
                checkWorkerStatus()
            }
            info.append(workerInfo)
            info.append("\n\n")

            // Configuration Info
            info.append("CONFIGURATION\n")
            info.append("=============\n")
            val configInfo = withContext(Dispatchers.IO) {
                checkConfigInfo()
            }
            info.append(configInfo)

            // Update the UI
            debugInfoText.text = info.toString()
        }
    }

    private fun checkFileSystem(): String {
        val sb = StringBuilder()

        // Config file
        val configFile = configManager.getConfigFile()
        sb.append("Config File: ")
        if (configFile.exists()) {
            sb.append("EXISTS\n")
            sb.append("Path: ${configFile.absolutePath}\n")
            val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(configFile.lastModified()))
            sb.append("Last Modified: $lastModified\n")
            sb.append("Size: ${configFile.length()} bytes\n")
        } else {
            sb.append("MISSING\n")
            sb.append("Expected Path: ${configFile.absolutePath}\n")
        }

        sb.append("\n")

        // Data file
        val dataFile = dataManager.getDataFile()
        sb.append("Data File: ")
        if (dataFile.exists()) {
            sb.append("EXISTS\n")
            sb.append("Path: ${dataFile.absolutePath}\n")
            val lastModified = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(dataFile.lastModified()))
            sb.append("Last Modified: $lastModified\n")
            sb.append("Size: ${dataFile.length()} bytes\n")

            // Count entries
            try {
                val lines = dataFile.readLines()
                sb.append("Entries: ${lines.size - 1} (excluding header)\n") // Subtract header row
            } catch (e: Exception) {
                sb.append("Error reading entries: ${e.message}\n")
            }
        } else {
            sb.append("MISSING\n")
            sb.append("Expected Path: ${dataFile.absolutePath}\n")
        }

        // Documents directory writable?
        val documentsDir = configFile.parentFile
        if (documentsDir != null) {
            sb.append("\nDocuments Directory: ${documentsDir.absolutePath}\n")
            sb.append("Writable: ${documentsDir.canWrite()}\n")
            sb.append("Free Space: ${documentsDir.freeSpace / (1024 * 1024)} MB\n")
        }

        return sb.toString()
    }

    private fun checkPermissionsStatus(): String {
        val sb = StringBuilder()

        for (permission in REQUIRED_PERMISSIONS) {
            val granted = ContextCompat.checkSelfPermission(
                this, permission) == PackageManager.PERMISSION_GRANTED
            val permissionName = permission.substringAfterLast(".")
            sb.append("$permissionName: ${if (granted) "GRANTED" else "DENIED"}\n")
        }

        return sb.toString()
    }

    private suspend fun checkWorkerStatus(): String {
        val sb = StringBuilder()

        // Check if worker is scheduled
        val workManager = WorkManager.getInstance(applicationContext)
        val workInfos = workManager.getWorkInfosByTag(Constants.WORKER_TAG).get()

        // Get next check time from SharedPreferences
        val prefs = getSharedPreferences("mood_tracker_prefs", Context.MODE_PRIVATE)
        val nextCheckTime = prefs.getLong("next_check_time", 0)

        // Display next check time prominently at the top
        if (nextCheckTime > 0) {
            val nextCheckDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(nextCheckTime))
            sb.append("NEXT MOOD CHECK: $nextCheckDate\n")

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

        return sb.toString()
    }

    private fun checkConfigInfo(): String {
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

    // Check if all permissions are granted
    private fun checkPermissions(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    // Request permissions if not granted
    private fun checkAndRequestPermissions(): Boolean {
        if (checkPermissions()) {
            return true
        }

        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
            return false
        }

        return true
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
                startMoodTracking()
            } else {
                // Permissions denied
                statusText.setText(R.string.permissions_required)
            }
        }
    }
}