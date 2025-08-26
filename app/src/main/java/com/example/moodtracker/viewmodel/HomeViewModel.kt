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
import java.util.TimeZone

data class TrackingStatusUiState(
    val isLoading: Boolean = true,
    val isActive: Boolean = false,
    val nextCheckMessage: String = "Loading..."
)

data class DisplayMoodEntry(
    val id: String, // Can be composite if entries are grouped
    val timeRange: String,
    val moodName: String,
    val moodColor: Int, // Parsed color Int
    val startHour: Int, // 0-23, for backward compatibility
    val startTimestamp: Long // For proper chronological sorting
)

data class TodaysMoodsUiState(
    val isLoading: Boolean = true,
    val moods: List<DisplayMoodEntry> = emptyList(),
    val message: String? = null // e.g., "No moods recorded in the last 24 hours."
)

data class DisplayMissedEntry(
    val hourId: String,
    val displayText: String
)

data class MissedEntriesUiState(
    val isLoading: Boolean = true,
    val missedEntries: List<DisplayMissedEntry> = emptyList()
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

    private val _todaysMoodsUiState = MutableStateFlow(TodaysMoodsUiState())
    val todaysMoodsUiState: StateFlow<TodaysMoodsUiState> = _todaysMoodsUiState.asStateFlow()

    private val _missedEntriesUiState = MutableStateFlow(MissedEntriesUiState())
    val missedEntriesUiState: StateFlow<MissedEntriesUiState> = _missedEntriesUiState.asStateFlow()

    private val _debugInfoUiState = MutableStateFlow(DebugInfoUiState())
    val debugInfoUiState: StateFlow<DebugInfoUiState> = _debugInfoUiState.asStateFlow()

    // For time formatting, considering user's preference
    private val userTimeFormat: String by lazy { configManager.loadConfig().timeFormat }
    private val moodIdHourFormat = SimpleDateFormat("yyyyMMddHH", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }


    init {
        viewModelScope.launch {
            allConfigMoods = withContext(Dispatchers.IO) { configManager.loadMoods() }
            loadData()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _trackingStatusUiState.update { it.copy(isLoading = true) }
            _todaysMoodsUiState.update { it.copy(isLoading = true) }
            _missedEntriesUiState.update { it.copy(isLoading = true) }

            // Load and process config information
            val currentConfig = configManager.loadConfig()

            fetchTrackingStatus()
            fetchTodaysMoods()
            fetchMissedEntries()
            fetchDebugInfo(currentConfig) // Pass the already loaded config

            _trackingStatusUiState.update { it.copy(isLoading = false) }
            _todaysMoodsUiState.update { it.copy(isLoading = false) }
            _missedEntriesUiState.update { it.copy(isLoading = false) }
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

    private suspend fun fetchTodaysMoods() {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR_OF_DAY, -24)
        val sinceTimestamp = calendar.timeInMillis

        val rawEntries = dataManager.getMoodEntriesSince(sinceTimestamp)

        if (rawEntries.isEmpty()) {
            _todaysMoodsUiState.update {
                it.copy(moods = emptyList(), message = "No moods recorded in the last 24 hours.", isLoading = false) //TODO make this message change based on the config
            }
            return
        }

        val displayEntries = processMoodEntriesForDisplay(rawEntries)
        _todaysMoodsUiState.update {
            it.copy(moods = displayEntries, message = null, isLoading = false)
        }
    }

    private suspend fun fetchMissedEntries() {
        val missedIds = dataManager.getMissedEntryHourIds()
        val displayEntries = missedIds.map { hourId ->
            DisplayMissedEntry(
                hourId = hourId,
                displayText = formatMissedEntryIdForDisplay(hourId)
            )
        }
        _missedEntriesUiState.update {
            it.copy(missedEntries = displayEntries, isLoading = false)
        }
    }

    /**
     * Formats a missed entry hour ID into a user-friendly string like "Today at 5 PM".
     * This is presentation logic, so it lives in the ViewModel.
     */
    private fun formatMissedEntryIdForDisplay(hourId: String): String {
        if (hourId.length != 10) return "Invalid time"

        return try {
            val date = moodIdHourFormat.parse(hourId) ?: return "Invalid date"

            val dayString = when {
                android.text.format.DateUtils.isToday(date.time) -> "Today at"
                // Check if the date was yesterday
                android.text.format.DateUtils.isToday(date.time + TimeUnit.DAYS.toMillis(1)) -> "Yesterday at"
                else -> SimpleDateFormat("MMM d 'at'", Locale.getDefault()).format(date)
            }

            // Adhere to DRY by reusing the ConfigManager's hour formatting logic
            val timeString = configManager.formatHourIdForDisplay(hourId)

            "$dayString $timeString"
        } catch (e: Exception) {
            "Invalid format"
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

    private fun processMoodEntriesForDisplay(entries: List<MoodEntry>): List<DisplayMoodEntry> {
        if (entries.isEmpty()) return emptyList()

        val displayEntries = mutableListOf<DisplayMoodEntry>()
        var groupStartEntry = entries.first()

        for (i in 1 until entries.size) {
            val currentEntry = entries[i]
            val prevEntry = entries[i - 1]

            // A group ends if the mood name changes
            if (currentEntry.moodName != groupStartEntry.moodName) {
                displayEntries.add(createDisplayEntry(groupStartEntry, prevEntry))
                groupStartEntry = currentEntry
            }
        }

        // Add the final group
        displayEntries.add(createDisplayEntry(groupStartEntry, entries.last()))

        return displayEntries.sortedByDescending { it.id }
    }

    private fun createDisplayEntry(startEntry: MoodEntry, endEntry: MoodEntry): DisplayMoodEntry {
        val mood = allConfigMoods.find { it.name == startEntry.moodName }
        val moodColor = mood?.getColor() ?: Color.Gray.toArgb()

        val displayTimeZone = try {
            TimeZone.getTimeZone(startEntry.timeZoneId)
        } catch (e: Exception) {
            TimeZone.getDefault() // Fallback to device default if ID is invalid
        }

        val displayHourFormat = when (userTimeFormat) {
            ConfigManager.TimeFormat.H24 -> SimpleDateFormat("HH:00", Locale.getDefault())
            else -> SimpleDateFormat("h a", Locale.getDefault())
        }.apply {
            timeZone = displayTimeZone
        }

        val startTimeCal = Calendar.getInstance(displayTimeZone).apply { timeInMillis = startEntry.timestamp }

        val timeRange = if (startEntry.id == endEntry.id) {
            // Single hour entry
            displayHourFormat.format(startTimeCal.time)
        } else {
            // Range of hours
            val endTimeCal = Calendar.getInstance(displayTimeZone).apply { timeInMillis = endEntry.timestamp }
            "${displayHourFormat.format(startTimeCal.time)} - ${displayHourFormat.format(endTimeCal.time)}"
        }

        return DisplayMoodEntry(
            id = startEntry.id,
            timeRange = timeRange,
            moodName = startEntry.moodName,
            moodColor = moodColor,
            startHour = startTimeCal.get(Calendar.HOUR_OF_DAY),
            startTimestamp = startEntry.timestamp
        )
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

    fun onMissedEntryClicked(hourId: String) {
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