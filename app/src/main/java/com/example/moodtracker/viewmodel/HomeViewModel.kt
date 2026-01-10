package com.example.moodtracker.viewmodel

import android.app.Application
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.example.moodtracker.model.Constants
import com.example.moodtracker.model.Mood
import com.example.moodtracker.model.MoodEntry
import com.example.moodtracker.ui.ComposeMoodSelectionActivity
import com.example.moodtracker.util.ConfigManager
import com.example.moodtracker.util.DataManager
import com.example.moodtracker.worker.MoodCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.Date

data class TrackingStatusUiState(
    val isLoading: Boolean = true,
    val isActive: Boolean = false,
    val nextCheckMessage: String = "Loading..."
)

data class TimelineItem(
    val id: String, // hour ID or composite ID for grouped entries
    val timeRange: String,
    val itemType: TimelineItemType,
    val moodName: String? = null, // null for missed entries
    val moodColor: Int? = null, // null for missed entries
    val startTimestamp: Long // For proper chronological sorting
)

enum class TimelineItemType {
    MOOD_ENTRY,      // A recorded mood entry (can be grouped)
    MISSED_ENTRY     // A missing hour that needs to be filled in
}

// Keep for backward compatibility with debug display
data class DisplayMoodEntry(
    val id: String, // Can be composite if entries are grouped
    val timeRange: String,
    val moodName: String,
    val moodColor: Int, // Parsed color Int
    val startHour: Int, // 0-23, for backward compatibility
    val startTimestamp: Long // For proper chronological sorting
)

data class TimelineUiState(
    val isLoading: Boolean = true,
    val timelineItems: List<TimelineItem> = emptyList(),
    val message: String? = null // e.g., "No moods recorded in the last 48 hours."
)

// New UI State for Debug Information
data class DebugInfoUiState(
    val isDebugModeEnabled: Boolean = false,
    // Worker Status
    val nextCheckTime: String = "",
    val timeUntilNextCheck: String = "",
    val workerStatus: String = "",
    val lastCheckTime: String = "",
    val timeSinceLastCheck: String = "",
    val trackingEnabled: Boolean = false,
    // Permissions Status
    val permissionsStatus: Map<String, Boolean> = emptyMap(),
    val batteryOptimizationExempt: Boolean = false,
    // Database Status
    val databaseEntryCount: Int = 0,
    val latestEntry: String = "",
    // Configuration
    val minInterval: Int = 0,
    val maxInterval: Int = 0,
    val timeFormat: String = "",
    val appTheme: String = "",
    val autoExportFrequency: String = "",
    val autoSleepStartHour: String = "",
    val autoSleepEndHour: String = "",
    val moods: List<MoodDebugInfo> = emptyList(),
    // Debugging
    val databaseEntriesForDialog: List<MoodEntry>? = null
)

data class MoodDebugInfo(
    val name: String,
    val colorHex: String,
    val properties: String // Compact representation of dimensions/category
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val dataManager = DataManager(application)
    private val configManager = ConfigManager(application)
    private val prefs: SharedPreferences = application.getSharedPreferences(
        MoodCheckWorker.PREF_NAME, Context.MODE_PRIVATE
    )
    private var allConfigMoods: List<Mood> = emptyList() // Cache loaded moods

    private val _trackingStatusUiState = MutableStateFlow(TrackingStatusUiState())
    val trackingStatusUiState: StateFlow<TrackingStatusUiState> = _trackingStatusUiState.asStateFlow()

    private val _timelineUiState = MutableStateFlow(TimelineUiState())
    val timelineUiState: StateFlow<TimelineUiState> = _timelineUiState.asStateFlow()

    private val _debugInfoUiState = MutableStateFlow(DebugInfoUiState())
    val debugInfoUiState: StateFlow<DebugInfoUiState> = _debugInfoUiState.asStateFlow()

    init {
        viewModelScope.launch {
            allConfigMoods = withContext(Dispatchers.IO) { configManager.loadMoods() }
            loadData()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _trackingStatusUiState.update { it.copy(isLoading = true) }
            _timelineUiState.update { it.copy(isLoading = true) }

            // Load and process config information
            val currentConfig = configManager.loadConfig()

            fetchTrackingStatus()
            fetchTimeline()
            fetchDebugInfo(currentConfig) // Pass the already loaded config

            _trackingStatusUiState.update { it.copy(isLoading = false) }
            _timelineUiState.update { it.copy(isLoading = false) }
        }
    }

    private fun fetchTrackingStatus() {
        val isActive = MoodCheckWorker.isTrackingActive(getApplication())
        val nextCheckTimestamp = prefs.getLong(MoodCheckWorker.PREF_NEXT_CHECK_TIME, 0L)
        val message = if (isActive) {
            if (nextCheckTimestamp > System.currentTimeMillis()) {
                formatNextCheckTime(nextCheckTimestamp)
            } else {
                "Next check: Processing..." // Or "Scheduled"
            }
        } else {
            "Tracking is off."
        }
        _trackingStatusUiState.update {
            it.copy(isActive = isActive, nextCheckMessage = message, isLoading = false)
        }
    }

    private suspend fun fetchTimeline() {
        val config = configManager.loadConfig()
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR_OF_DAY, -config.timelineHours)
        val sinceTimestamp = calendar.timeInMillis

        // Fetch mood entries
        val rawEntries = dataManager.getMoodEntriesSince(sinceTimestamp)

        // Fetch missed entry hour IDs
        val missedHourIds = dataManager.getMissedEntryHourIds()

        // Create a map of hour IDs to mood entries for quick lookup
        val entriesByHourId = rawEntries.groupBy { it.id }

        // Generate all expected hour IDs in the timeline range
        val expectedHourIds = mutableListOf<String>()
        val tempCalendar = Calendar.getInstance()
        tempCalendar.timeInMillis = sinceTimestamp
        val now = System.currentTimeMillis()

        while (tempCalendar.timeInMillis < now) {
            expectedHourIds.add(dataManager.generateHourId(tempCalendar.timeInMillis))
            tempCalendar.add(Calendar.HOUR_OF_DAY, 1)
        }

        // Build timeline items - combine moods and missed entries
        val timelineItems = mutableListOf<TimelineItem>()
        var i = 0

        while (i < expectedHourIds.size) {
            val hourId = expectedHourIds[i]
            val entries = entriesByHourId[hourId]

            if (!entries.isNullOrEmpty()) {
                // We have a mood entry - group consecutive same moods
                val firstEntry = entries.first()
                val moodName = firstEntry.moodName
                var endIndex = i

                // Look ahead to group consecutive hours with same mood
                while (endIndex + 1 < expectedHourIds.size) {
                    val nextHourId = expectedHourIds[endIndex + 1]
                    val nextEntries = entriesByHourId[nextHourId]
                    if (!nextEntries.isNullOrEmpty() && nextEntries.first().moodName == moodName) {
                        endIndex++
                    } else {
                        break
                    }
                }

                val mood = allConfigMoods.find { it.name == moodName }
                val moodColor = mood?.getColor() ?: Color.Gray.toArgb()

                // Format time range
                val timeRange = if (i == endIndex) {
                    configManager.formatHourIdForDisplay(hourId)
                } else {
                    val startDisplay = configManager.formatHourIdForDisplay(hourId)
                    val endDisplay = configManager.formatHourIdForDisplay(expectedHourIds[endIndex])
                    "$startDisplay - $endDisplay"
                }

                val timestamp = configManager.convertUtcHourIdToTimestamp(hourId) ?: firstEntry.timestamp

                timelineItems.add(
                    TimelineItem(
                        id = hourId,
                        timeRange = timeRange,
                        itemType = TimelineItemType.MOOD_ENTRY,
                        moodName = moodName,
                        moodColor = moodColor,
                        startTimestamp = timestamp
                    )
                )

                i = endIndex + 1
            } else if (hourId in missedHourIds) {
                // This is a missed entry
                val timeRange = configManager.formatHourIdForDisplay(hourId)
                val timestamp = configManager.convertUtcHourIdToTimestamp(hourId) ?: System.currentTimeMillis()

                timelineItems.add(
                    TimelineItem(
                        id = hourId,
                        timeRange = timeRange,
                        itemType = TimelineItemType.MISSED_ENTRY,
                        startTimestamp = timestamp
                    )
                )
                i++
            } else {
                // Hour outside tracking or before first entry
                i++
            }
        }

        // Sort timeline by timestamp descending (most recent first)
        val sortedItems = timelineItems.sortedByDescending { it.startTimestamp }

        if (sortedItems.isEmpty()) {
            _timelineUiState.update {
                it.copy(timelineItems = emptyList(), message = "No moods recorded in the last ${config.timelineHours} hours.", isLoading = false)
            }
        } else {
            _timelineUiState.update {
                it.copy(timelineItems = sortedItems, message = null, isLoading = false)
            }
        }
    }

    private fun fetchDebugInfo(config: ConfigManager.Config) {
        val debugEnabled = config.debugModeEnabled
        if (!debugEnabled) {
            _debugInfoUiState.update { it.copy(isDebugModeEnabled = false) }
            return
        }

        viewModelScope.launch {
            // Worker Status
            val nextCheckTimestamp = prefs.getLong(MoodCheckWorker.PREF_NEXT_CHECK_TIME, 0L)
            val lastCheckTimestamp = prefs.getLong(MoodCheckWorker.PREF_LAST_CHECK_TIME, 0L)
            val trackingEnabled = MoodCheckWorker.isTrackingActive(getApplication())

            val nextCheckTime = if (nextCheckTimestamp > 0) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(nextCheckTimestamp))
            } else "Not scheduled"

            val timeUntilNext = if (nextCheckTimestamp > System.currentTimeMillis()) {
                val diff = nextCheckTimestamp - System.currentTimeMillis()
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60
                "$hours hr $minutes min $seconds sec"
            } else "N/A"

            val lastCheckTime = if (lastCheckTimestamp > 0) {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastCheckTimestamp))
            } else "Never"

            val timeSinceLast = if (lastCheckTimestamp > 0) {
                val diff = System.currentTimeMillis() - lastCheckTimestamp
                val hours = TimeUnit.MILLISECONDS.toHours(diff)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(diff) % 60
                "$hours hr $minutes min $seconds sec"
            } else "N/A"

            // Worker Status
            val workerStatus = try {
                val workManager = WorkManager.getInstance(getApplication())
                val workInfosFuture = workManager.getWorkInfosByTag(Constants.WORKER_TAG)
                val workInfos = workInfosFuture.get(1000, TimeUnit.MILLISECONDS)
                if (workInfos.isNotEmpty() && workInfos.any { !it.state.isFinished }) {
                    "SCHEDULED"
                } else {
                    "NOT SCHEDULED"
                }
            } catch (e: Exception) {
                "ERROR CHECKING"
            }

            // Permissions Status
            val permissions = mutableMapOf<String, Boolean>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions["POST_NOTIFICATIONS"] = ContextCompat.checkSelfPermission(
                    getApplication(), Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }
            permissions["VIBRATE"] = ContextCompat.checkSelfPermission(
                getApplication(), Manifest.permission.VIBRATE
            ) == PackageManager.PERMISSION_GRANTED

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                permissions["WRITE_EXTERNAL_STORAGE"] = ContextCompat.checkSelfPermission(
                    getApplication(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
                permissions["READ_EXTERNAL_STORAGE"] = ContextCompat.checkSelfPermission(
                    getApplication(), Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }

            // Battery Optimization
            val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as PowerManager
            val batteryOptExempt = powerManager.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)

            // Database Status
            val entryCount = withContext(Dispatchers.IO) { dataManager.getEntryCount() }
            val latestEntryInfo = withContext(Dispatchers.IO) {
                dataManager.getMostRecentEntry()?.let { entry ->
                    val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp))
                    "${entry.id} at $date (${entry.moodName})"
                } ?: "No entries yet"
            }

            // Configuration Analysis
            val moods = allConfigMoods
            val moodDebugInfoList = moods.map { mood ->
                val properties = buildString {
                    append("V=${mood.valence}, ")
                    append("A=${mood.arousal}, ")
                    append("D=${mood.dominance}")
                }
                MoodDebugInfo(
                    name = mood.name,
                    colorHex = mood.colorHex,
                    properties = properties
                )
            }

            // Format configuration values
            val timeFormatDisplay = when(config.timeFormat) {
                ConfigManager.TimeFormat.H12 -> "12-hour"
                ConfigManager.TimeFormat.H24 -> "24-hour"
                ConfigManager.TimeFormat.SYSTEM_DEFAULT -> "System Default"
                else -> config.timeFormat
            }

            val appThemeDisplay = when(config.appTheme) {
                ConfigManager.AppTheme.LIGHT -> "Light"
                ConfigManager.AppTheme.DARK -> "Dark"
                ConfigManager.AppTheme.SYSTEM -> "System"
                else -> config.appTheme
            }

            val autoExportDisplay = when(config.autoExportFrequency) {
                ConfigManager.AutoExportFrequency.OFF -> "Off"
                ConfigManager.AutoExportFrequency.DAILY -> "Daily"
                ConfigManager.AutoExportFrequency.WEEKLY_SUNDAY -> "Weekly (Sunday)"
                ConfigManager.AutoExportFrequency.WEEKLY_MONDAY -> "Weekly (Monday)"
                ConfigManager.AutoExportFrequency.MONTHLY_FIRST -> "Monthly (1st)"
                else -> config.autoExportFrequency
            }

            val autoSleepStart = config.autoSleepStartHour?.let { "$it:00" } ?: "Not Set"
            val autoSleepEnd = config.autoSleepEndHour?.let { "$it:00" } ?: "Not Set"

            _debugInfoUiState.update {
                it.copy(
                    isDebugModeEnabled = true,
                    nextCheckTime = nextCheckTime,
                    timeUntilNextCheck = timeUntilNext,
                    workerStatus = workerStatus,
                    lastCheckTime = lastCheckTime,
                    timeSinceLastCheck = timeSinceLast,
                    trackingEnabled = trackingEnabled,
                    permissionsStatus = permissions,
                    batteryOptimizationExempt = batteryOptExempt,
                    databaseEntryCount = entryCount,
                    latestEntry = latestEntryInfo,
                    minInterval = config.minIntervalMinutes,
                    maxInterval = config.maxIntervalMinutes,
                    timeFormat = timeFormatDisplay,
                    appTheme = appThemeDisplay,
                    autoExportFrequency = autoExportDisplay,
                    autoSleepStartHour = autoSleepStart,
                    autoSleepEndHour = autoSleepEnd,
                    moods = moodDebugInfoList
                )
            }
        }
    }

    private fun formatNextCheckTime(nextCheckTimestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMillis = nextCheckTimestamp - now
        if (diffMillis <= 0) return "Next check: Processing..."

        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60

        return when {
            hours > 0 -> "Next check in $hours hr $minutes min"
            minutes > 0 -> "Next check in $minutes min"
            else -> "Next check: Soon"
        }
    }

    fun onCheckNowClicked() {
        MoodCheckWorker.startTracking(getApplication(), isImmediate = true)
        // Refresh status quickly
        viewModelScope.launch { fetchTrackingStatus() }
    }

    fun onToggleTrackingClicked() {
        val isActive = MoodCheckWorker.isTrackingActive(getApplication())
        if (isActive) {
            MoodCheckWorker.stopTracking(getApplication())
        } else {
            MoodCheckWorker.startTracking(getApplication(), isImmediate = false)
        }
        // Refresh status quickly
        viewModelScope.launch { fetchTrackingStatus() }
    }

    fun onTimelineItemClicked(hourId: String) {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, ComposeMoodSelectionActivity::class.java).apply {
            putExtra(ComposeMoodSelectionActivity.EXTRA_HOUR_ID, hourId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun onViewDatabaseClicked() {
        viewModelScope.launch {
            val entries = dataManager.getAllEntries()
            _debugInfoUiState.update { it.copy(databaseEntriesForDialog = entries) }
        }
    }

    fun onDismissDatabaseDialog() {
        _debugInfoUiState.update { it.copy(databaseEntriesForDialog = null) }
    }

    fun onPopulateDatabaseClicked() {
        viewModelScope.launch {
            dataManager.populateWithSampleData()
            loadData() // Reload all data to reflect changes
        }
    }
}