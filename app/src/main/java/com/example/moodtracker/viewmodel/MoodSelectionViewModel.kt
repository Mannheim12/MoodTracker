package com.example.moodtracker.viewmodel

import android.app.Application
import android.content.Context
import android.os.CountDownTimer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
// import com.example.moodtracker.model.Mood // Not needed for placeholder
// import com.example.moodtracker.model.Constants // Not needed for placeholder
import com.example.moodtracker.util.ConfigManager // Still needed for time formatting
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
    // val allDisplayableMoods: List<Mood> = emptyList(), // Removed for placeholder
    val promptText: String = "How are you feeling right now?",
    val timeText: String = "",
    val isLoading: Boolean = true, // Will be set to false quickly
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
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

                _uiState.update {
                    it.copy(
                        // allDisplayableMoods = emptyList(), // Not needed
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

    // Removed loadAndPrepareMoodsAsync() as it's not used for placeholder

    fun onMoodSelected(moodName: String, onMoodRecordedCallback: () -> Unit) { // Renamed callback
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
                // Ensure prompt text reflects back press action if desired, or keep it simple
                // _uiState.update { it.copy(promptText = "Recording 'Asleep' due to back press.") }
                onMoodSelected("Asleep", onMoodRecordedForBackPress)
            }
        } else {
            onMoodRecordedForBackPress() // Mood already selected, just proceed with closing
        }
    }

    override fun onCleared() {
        super.onCleared()
        moodSelectionTimer?.cancel()
    }
}