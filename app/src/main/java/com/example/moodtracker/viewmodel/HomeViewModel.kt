package com.example.moodtracker.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.moodtracker.model.Mood
import com.example.moodtracker.model.MoodEntry
import com.example.moodtracker.util.ConfigManager
import com.example.moodtracker.util.DataManager
import com.example.moodtracker.worker.MoodCheckWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.Date

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
    val startHour: Int // 0-23, for sorting or reference
)

data class TodaysMoodsUiState(
    val isLoading: Boolean = true,
    val moods: List<DisplayMoodEntry> = emptyList(),
    val message: String? = null // e.g., "No moods recorded in the last 24 hours."
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

    // For time formatting, considering user's preference
    private val userTimeFormat: String by lazy { configManager.loadConfig().timeFormat }
    private val displayHourFormat: SimpleDateFormat by lazy {
        when (userTimeFormat) {
            ConfigManager.TimeFormat.H24 -> SimpleDateFormat("HH:00", Locale.getDefault())
            else -> SimpleDateFormat("h a", Locale.getDefault()) // Default to 12h with AM/PM
        }
    }
    private val moodIdHourFormat = SimpleDateFormat("yyyyMMddHH", Locale.US)


    init {
        viewModelScope.launch {
            allConfigMoods = configManager.loadMoods() // Load once
            loadData()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _trackingStatusUiState.update { it.copy(isLoading = true) }
            _todaysMoodsUiState.update { it.copy(isLoading = true) }

            fetchTrackingStatus()
            fetchTodaysMoods()

            _trackingStatusUiState.update { it.copy(isLoading = false) }
            _todaysMoodsUiState.update { it.copy(isLoading = false) }
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

        val rawEntries = dataManager.getMoodEntriesSince(sinceTimestamp).sortedBy { it.timestamp } // Oldest first for processing

        if (rawEntries.isEmpty()) {
            _todaysMoodsUiState.update {
                it.copy(moods = emptyList(), message = "No moods recorded in the last 24 hours.", isLoading = false)
            }
            return
        }

        val displayEntries = processMoodEntriesForDisplay(rawEntries)
        _todaysMoodsUiState.update {
            it.copy(moods = displayEntries, message = null, isLoading = false)
        }
    }

    private fun processMoodEntriesForDisplay(entries: List<MoodEntry>): List<DisplayMoodEntry> {
        if (entries.isEmpty()) return emptyList()

        val groupedDisplayEntries = mutableListOf<DisplayMoodEntry>()
        var currentGroupStartTime = entries.first().timestamp
        var currentGroupMoodName = entries.first().moodName
        var currentGroupStartHourId = entries.first().id


        for (i in 1 until entries.size) {
            val currentEntry = entries[i]
            val prevEntry = entries[i-1]

            // Check if mood changed OR if there's a time gap greater than 1 hour
            // (assuming mood IDs like yyyyMMddHH are consecutive for consecutive hours)
            val prevHourCal = Calendar.getInstance().apply { time = moodIdHourFormat.parse(prevEntry.id) ?: Date(prevEntry.timestamp) }
            val currentHourCal = Calendar.getInstance().apply { time = moodIdHourFormat.parse(currentEntry.id) ?: Date(currentEntry.timestamp) }
            prevHourCal.add(Calendar.HOUR_OF_DAY, 1)


            if (currentEntry.moodName != currentGroupMoodName || !isSameHour(prevHourCal, currentHourCal)) {
                // End previous group
                val mood = allConfigMoods.find { it.name == currentGroupMoodName }
                val moodColor = mood?.getColor() ?: Color.Gray.toArgb() // Default color
                val startTimeCal = Calendar.getInstance().apply { timeInMillis = currentGroupStartTime }
                val endTimeCal = Calendar.getInstance().apply { timeInMillis = prevEntry.timestamp } // End time is the prev entry's hour

                groupedDisplayEntries.add(
                    DisplayMoodEntry(
                        id = currentGroupStartHourId + "-" + prevEntry.id, // Composite ID
                        timeRange = "${formatTimeForDisplay(startTimeCal)} - ${formatTimeForDisplay(endTimeCal, true)}",
                        moodName = currentGroupMoodName,
                        moodColor = moodColor,
                        startHour = startTimeCal.get(Calendar.HOUR_OF_DAY)
                    )
                )
                // Start new group
                currentGroupStartTime = currentEntry.timestamp
                currentGroupMoodName = currentEntry.moodName
                currentGroupStartHourId = currentEntry.id
            }
        }

        // Add the last group
        val lastEntry = entries.last()
        val mood = allConfigMoods.find { it.name == currentGroupMoodName }
        val moodColor = mood?.getColor() ?: Color.Gray.toArgb()
        val startTimeCalLast = Calendar.getInstance().apply { timeInMillis = currentGroupStartTime }
        // For the very last entry, its end time is effectively the end of its hour.
        // Or if it's the current hour, it might be "Now" or "?"
        val endTimeCalLast = Calendar.getInstance().apply { timeInMillis = lastEntry.timestamp }


        groupedDisplayEntries.add(
            DisplayMoodEntry(
                id = currentGroupStartHourId + "-" + lastEntry.id,
                timeRange = "${formatTimeForDisplay(startTimeCalLast)} - ${formatTimeForDisplay(endTimeCalLast, true)}", // Or "Now"
                moodName = currentGroupMoodName,
                moodColor = moodColor,
                startHour = startTimeCalLast.get(Calendar.HOUR_OF_DAY)
            )
        )
        return groupedDisplayEntries.sortedByDescending { it.startHour } // Show newest groups first
    }
    private fun isSameHour(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR) &&
                cal1.get(Calendar.HOUR_OF_DAY) == cal2.get(Calendar.HOUR_OF_DAY)
    }


    private fun formatTimeForDisplay(calendar: Calendar, isEndTime: Boolean = false): String {
        // If it's an end time, we effectively want to show the end of that hour.
        // So, "9 AM" start time means the 9:00-9:59 block. If this is an end time,
        // it means the range ended *at the start* of the next hour, or *during* this hour.
        // The mockup has "9 AM - 12 PM", where 12 PM is likely the start of the next mood.
        // So, if a mood lasts for 9, 10, 11, the range is 9 AM - 12 PM (exclusive of 12 PM for this mood).
        // Let's adjust the display format to be simpler: just the start hour.
        // The grouping logic will handle the range text.
        val tempCal = Calendar.getInstance()
        tempCal.timeInMillis = calendar.timeInMillis
        if(isEndTime) { // If it's an end time for a block, show the start of the *next* hour
            // tempCal.add(Calendar.HOUR_OF_DAY, 1) // This might be too complex for simple display
        }
        return displayHourFormat.format(tempCal.time)
    }


    private fun formatNextCheckTime(nextCheckTimestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMillis = nextCheckTimestamp - now
        if (diffMillis <= 0) return "Next check: Processing..."

        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis) % 60

        return when {
            hours > 0 -> "Next check in $hours hr ${minutes} min"
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
}