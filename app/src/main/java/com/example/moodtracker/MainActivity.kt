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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent

/**
 * Main entry point for the application - enhanced with debugging info
 */
class MainActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var dataManager: DataManager
    private lateinit var statusText: TextView
    private lateinit var debugInfoText: TextView
    private lateinit var batteryOptimizationText: TextView
    private lateinit var importConfigLauncher: ActivityResultLauncher<Intent>
    // 1. Permission buttons
    private lateinit var requestPermissionsButton: Button
    // 2. Control buttons
    private lateinit var toggleTrackingButton: Button
    private lateinit var stopTrackingButton: Button
    // 3. Config buttons
    private lateinit var viewConfigButton: Button
    private lateinit var exportConfigButton: Button
    private lateinit var importConfigButton: Button
    // 4. Database buttons
    private lateinit var viewDatabaseButton: Button
    private lateinit var exportDatabaseButton: Button // Future feature
    // 5. Debug buttons
    private lateinit var refreshButton: Button
    private lateinit var hideDebugButton: Button // Future feature

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
            // For Android 13+ (API 33+)
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.VIBRATE
            )
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // For Android 9 and below (API ≤ 28)
            arrayOf(
                Manifest.permission.VIBRATE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        } else {
            // For Android 10-12
            arrayOf(
                Manifest.permission.VIBRATE
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup result launcher
        importConfigLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val success = configManager.importConfig(uri)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                statusText.setText(R.string.config_imported)
                                updateDebugInfo()
                            } else {
                                statusText.setText(R.string.import_failed)
                            }
                        }
                    }
                }
            }
        }

        configManager = ConfigManager(this)
        dataManager = DataManager(this)

        // Initialize text views
        statusText = findViewById(R.id.status_text)
        batteryOptimizationText = findViewById(R.id.battery_optimization_text)
        debugInfoText = findViewById(R.id.debug_info_text)

        // Initialize all buttons (organized by logical sections)
        // 1. Permissions row
        requestPermissionsButton = findViewById(R.id.request_permissions_button)
        // 2. Tracking controls row
        toggleTrackingButton = findViewById(R.id.toggle_tracking_button)
        stopTrackingButton = findViewById(R.id.stop_tracking_button)
        // 3. Configuration row
        viewConfigButton = findViewById(R.id.view_config_button)
        exportConfigButton = findViewById(R.id.export_config_button)
        importConfigButton = findViewById(R.id.import_config_button)
        // 4. Database row
        viewDatabaseButton = findViewById(R.id.view_database_button)
        //exportDatabaseButton = findViewById(R.id.export_database_button) // Future feature
        // 5. Debug row
        refreshButton = findViewById(R.id.refresh_button) //show or refresh button
        //hideDebugButton = findViewById(R.id.hide_debug_button) // Future feature

        // Set up button click listeners (same order as initialization)
        // 1. Permissions row
        requestPermissionsButton.setOnClickListener { requestPermissions() }
        // 2. Tracking controls row
        toggleTrackingButton.setOnClickListener { toggleTracking() }
        stopTrackingButton.setOnClickListener { stopTracking() }
        // 3. Configuration row
        viewConfigButton.setOnClickListener { viewConfig() }
        exportConfigButton.setOnClickListener { exportConfig() }
        importConfigButton.setOnClickListener { importConfig() }
        // 4. Database row
        viewDatabaseButton.setOnClickListener { viewDatabase() }
        // 5. Debug row
        refreshButton.setOnClickListener { updateDebugInfo() }
    }

    /**
     * Runs when the app is resumed to ensure proper state
     */
    override fun onResume() {
        super.onResume()
        // Update UI when returning to the app
        MoodCheckWorker.checkTrackingConsistency(this)
        updateDebugInfo()
        // Update battery optimization status
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        batteryOptimizationText.visibility = if (isIgnoringBatteryOptimizations) View.GONE else View.VISIBLE
        // Update permission button state
        val allPermissionsGranted = checkPermissions()
        requestPermissionsButton.isEnabled = !allPermissionsGranted
        // Update tracking button state
        val isActive = MoodCheckWorker.isTrackingActive(this)
        toggleTrackingButton.setText(if (isActive) R.string.check_now else R.string.start_tracking)
        stopTrackingButton.isEnabled = isActive
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

        // Get current state
        val isActive = MoodCheckWorker.isTrackingActive(this)

        if (isActive) {
            // If active, just trigger an immediate check
            MoodCheckWorker.startTracking(this, isImmediate = true)

            // Update UI to give feedback
            statusText.setText(R.string.check_triggered)
            // Reset status text after 3 seconds
            handler.postDelayed({
                statusText.setText(R.string.service_running)
            }, 3000)
        } else {
            // If not active, start tracking
            MoodCheckWorker.startTracking(this, isImmediate = false)

            // Update UI
            statusText.setText(R.string.service_running)
        }

        // Update tracking button state
        toggleTrackingButton.setText(R.string.check_now)
        stopTrackingButton.isEnabled = true
    }

    /**
     * Stop all mood tracking by canceling all workers
     */
    private fun stopTracking() {
        MoodCheckWorker.stopTracking(this)

        // Update UI
        statusText.setText(R.string.tracking_stopped)

        // Update tracking button state
        toggleTrackingButton.setText(R.string.start_tracking)
        stopTrackingButton.isEnabled = false
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
            requestPermissionsButton.isEnabled = true
        } else {
            statusText.setText(R.string.permissions_granted)
            requestPermissionsButton.isEnabled = false
        }
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

            // Update permission button state
            val allPermissionsGranted = checkPermissions()
            requestPermissionsButton.isEnabled = !allPermissionsGranted
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
        val isTrackingEnabled = MoodCheckWorker.isTrackingActive(this)

        // Get next check time from SharedPreferences
        val prefs = getSharedPreferences(MoodCheckWorker.PREF_NAME, Context.MODE_PRIVATE)
        val nextCheckTime = prefs.getLong(MoodCheckWorker.PREF_NEXT_CHECK_TIME, 0)

        // Display next check time prominently at the top
        if (nextCheckTime > 0) {
            val nextCheckDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(nextCheckTime))
            sb.append("NEXT MOOD CHECK: $nextCheckDate\n")

            // Time until next check
            val timeDiffMillis = nextCheckTime - System.currentTimeMillis()
            val hoursUntil = TimeUnit.MILLISECONDS.toHours(timeDiffMillis)
            val minutesUntil = TimeUnit.MILLISECONDS.toMinutes(timeDiffMillis) % 60
            val secondsUntil = TimeUnit.MILLISECONDS.toSeconds(timeDiffMillis) % 60
            sb.append("Time until next check: $hoursUntil hr $minutesUntil min $secondsUntil sec\n\n")
        } else {
            sb.append("NEXT MOOD CHECK: Not scheduled\n\n")
        }

        // Improved work info retrieval
        try {
            val workInfosFuture = workManager.getWorkInfosByTag(Constants.WORKER_TAG)
            val workInfos = workInfosFuture.get(1000, TimeUnit.MILLISECONDS) // Short timeout

            if (workInfos.isNotEmpty() && workInfos.any { !it.state.isFinished }) {
                sb.append("Worker Status: SCHEDULED\n")
            } else {
                sb.append("Worker Status: NOT SCHEDULED\n")
            }
        } catch (e: Exception) {
            sb.append("Worker Status: ERROR CHECKING\n")
        }

        // Last check time
        val lastCheckTime = prefs.getLong(MoodCheckWorker.PREF_LAST_CHECK_TIME, 0)
        if (lastCheckTime > 0) {
            val lastCheckDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date(lastCheckTime))
            sb.append("\nLast Check: $lastCheckDate\n")

            // Time since last check
            val timeSinceMillis = System.currentTimeMillis() - lastCheckTime
            val hoursSince = TimeUnit.MILLISECONDS.toHours(timeSinceMillis)
            val minutesSince = TimeUnit.MILLISECONDS.toMinutes(timeSinceMillis) % 60
            val secondsSince = TimeUnit.MILLISECONDS.toSeconds(timeSinceMillis) % 60
            sb.append("Time since last check: $hoursSince hr $minutesSince min $secondsSince sec\n")
        } else {
            sb.append("\nLast Check: NEVER\n")
        }

        // Tracking status from SharedPreferences
        sb.append("\nTracking Enabled: ${if (isTrackingEnabled) "YES" else "NO"}\n")

        return sb.toString()
    }

    private fun debugConfigInfo(): String {
        val sb = StringBuilder()

        try {
            // Load config values
            val config = configManager.loadConfig()
            sb.append("Min Interval: ${config.minIntervalMinutes} minutes\n")
            sb.append("Max Interval: ${config.maxIntervalMinutes} minutes\n")

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

    private fun exportConfig() {
        // Export in background
        CoroutineScope(Dispatchers.IO).launch {
            val file = configManager.exportConfig()
            withContext(Dispatchers.Main) {
                if (file != null) {
                    statusText.text = getString(R.string.config_exported, file.path)
                } else {
                    statusText.text = getString(R.string.export_failed)
                }
            }
        }
    }

    private fun importConfig() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importConfigLauncher.launch(intent)
    }

    private fun viewConfig() {
        // Launch in a coroutine
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Use configManager to get the config
                val config = configManager.loadConfig()

                // Convert to a readable format using existing methods
                val configText = buildString {
                    append("Min Interval: ${config.minIntervalMinutes} minutes\n")
                    append("Max Interval: ${config.maxIntervalMinutes} minutes\n\n")

                    append("Moods:\n")
                    config.moods.forEach { mood ->
                        append("- ${mood.name}: Color ${mood.colorHex}")
                        if (mood.dimension1.isNotEmpty()) {
                            append(", ${mood.dimension1}")
                        }
                        if (mood.dimension2.isNotEmpty()) {
                            append(", ${mood.dimension2}")
                        }
                        if (mood.dimension3.isNotEmpty()) {
                            append(", ${mood.dimension3}")
                        }
                        if (mood.category.isNotEmpty()) {
                            append(", Category: ${mood.category}")
                        }
                        append("\n")
                    }
                }

                // Show in an AlertDialog with ScrollView - using same pattern as viewDatabase()
                val scrollView = ScrollView(this@MainActivity)
                val textView = TextView(this@MainActivity).apply {
                    text = configText
                    setPadding(20, 20, 20, 20)
                    textSize = 14f
                }

                scrollView.addView(textView)

                AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.config_file_content))
                    .setView(scrollView)
                    .setPositiveButton(R.string.close, null)
                    .show()

            } catch (e: Exception) {
                statusText.setText(R.string.error_loading_config)
                e.printStackTrace()
            }
        }
    }
}