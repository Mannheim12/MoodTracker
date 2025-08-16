package com.example.moodtracker.viewmodel

import android.app.Application
import android.content.Context
import android.os.CountDownTimer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.moodtracker.model.Constants
import com.example.moodtracker.model.Mood
import com.example.moodtracker.util.ConfigManager
import com.example.moodtracker.util.DataManager
import com.example.moodtracker.worker.MoodCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MoodSelectionUiState(
    val moods: List<Mood> = emptyList(),
    val promptText: String = "How are you feeling right now?",
    val timeText: String = "",
    val isLoading: Boolean = true,
    val moodRecorded: Boolean = false,
    val error: String? = null
)

class MoodSelectionViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val configManager = ConfigManager(application)
    private val dataManager = DataManager(application)

    private val _uiState = MutableStateFlow(MoodSelectionUiState())
    val uiState: StateFlow<MoodSelectionUiState> = _uiState.asStateFlow()

    private var moodSelectionTimer: CountDownTimer? = null
    private val timeoutDuration = 60000L // 1 minute

    private var currentHourId: String = ""

    init {
        loadMoodsAndTimes()
    }

    private fun loadMoodsAndTimes() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Fetch hour ID and time text first
                currentHourId = withContext(Dispatchers.IO) {
                    val context = getApplication<Application>().applicationContext
                    val idFromPref = context
                        .getSharedPreferences(MoodCheckWorker.PREF_NAME, Context.MODE_PRIVATE)
                        .getString(MoodCheckWorker.PREF_HOURLY_ID, null)?.takeIf { it.isNotBlank() }
                    idFromPref ?: dataManager.generateHourId()
                }

                val timeTextResult = withContext(Dispatchers.IO) {
                    val formattedHourText = configManager.formatHourIdForDisplay(currentHourId)
                    val userTimeFormat = configManager.loadConfig().timeFormat
                    val currentTimePattern = when (userTimeFormat) {
                        ConfigManager.TimeFormat.H24 -> "HH:mm"
                        else -> "h:mm a"
                    }
                    val sdfCurrent = SimpleDateFormat(currentTimePattern, Locale.getDefault())
                    val currentTimeString = sdfCurrent.format(Date())
                    "For $formattedHourText (Current: $currentTimeString)"
                }

                // Load moods from hardcoded list instead of config
                // Take first 10 moods (the grid moods), excluding Asleep and N/A
                val loadedMoods = Constants.DEFAULT_MOODS.take(10)

                _uiState.update {
                    it.copy(
                        moods = loadedMoods,
                        timeText = timeTextResult,
                        isLoading = false,
                        error = null
                    )
                }
                startMoodSelectionTimeout()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Initialization failed: ${e.message}"
                    )
                }
            }
        }
    }

    fun onMoodSelected(moodName: String, onMoodRecordedCallback: () -> Unit) {
        if (_uiState.value.moodRecorded) return

        viewModelScope.launch {
            _uiState.update { it.copy(moodRecorded = true) }
            moodSelectionTimer?.cancel()

            val context = getApplication<Application>().applicationContext
            MoodCheckWorker.cancelNotification(context)

            try {
                withContext(Dispatchers.IO) {
                    if (currentHourId.isEmpty()) {
                        currentHourId = dataManager.generateHourId()
                    }
                    dataManager.addMoodEntry(moodName, currentHourId, System.currentTimeMillis())
                }
                onMoodRecordedCallback()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save mood: ${e.message}", moodRecorded = false) }
            }
        }
    }

    private fun startMoodSelectionTimeout() {
        moodSelectionTimer?.cancel()
        moodSelectionTimer = object : CountDownTimer(timeoutDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Optional: Update UI with countdown
            }

            override fun onFinish() {
                if (!_uiState.value.moodRecorded) {
                    viewModelScope.launch {
                        if (!_uiState.value.moodRecorded) { // Double check
                            _uiState.update { it.copy(promptText = "Timeout. Recording 'Asleep'.") }
                            onMoodSelected("Asleep") {
                                // Screen's LaunchedEffect handles closure via uiState.moodRecorded
                            }
                        }
                    }
                }
            }
        }.start()
    }

    fun handleBackPress(onMoodRecordedForBackPress: () -> Unit) {
        if (!_uiState.value.moodRecorded) {
            viewModelScope.launch {
                onMoodSelected("Asleep", onMoodRecordedForBackPress)
            }
        } else {
            onMoodRecordedForBackPress()
        }
    }

    override fun onCleared() {
        super.onCleared()
        moodSelectionTimer?.cancel()
    }
}