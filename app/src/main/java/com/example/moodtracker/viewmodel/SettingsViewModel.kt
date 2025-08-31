package com.example.moodtracker.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.moodtracker.util.ConfigManager
import com.example.moodtracker.util.DataManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Data class to hold all settings UI state
data class SettingsUiState(
    val minIntervalMinutes: Int = ConfigManager.Config().minIntervalMinutes,
    val maxIntervalMinutes: Int = ConfigManager.Config().maxIntervalMinutes,
    // autoSleepStartHour: Int? = null, // Will add later
    // autoSleepEndHour: Int? = null,   // Will add later
    val autoExportFrequency: String = ConfigManager.Config().autoExportFrequency,
    val timeFormat: String = ConfigManager.Config().timeFormat,
    val appTheme: String = ConfigManager.Config().appTheme,
    val debugModeEnabled: Boolean = ConfigManager.Config().debugModeEnabled,
    val snackbarMessage: String? = null // For showing transient messages
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val configManager = ConfigManager(application)
    private val dataManager = DataManager(application) // For Reset Database

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        val currentConfig = configManager.loadConfig()
        _uiState.update {
            it.copy(
                minIntervalMinutes = currentConfig.minIntervalMinutes,
                maxIntervalMinutes = currentConfig.maxIntervalMinutes,
                autoExportFrequency = currentConfig.autoExportFrequency,
                timeFormat = currentConfig.timeFormat,
                appTheme = currentConfig.appTheme,
                debugModeEnabled = currentConfig.debugModeEnabled
            )
        }
    }

    private fun saveAndUpdateState(newConfig: ConfigManager.Config) {
        configManager.saveConfig(newConfig)
        _uiState.update {
            it.copy(
                minIntervalMinutes = newConfig.minIntervalMinutes,
                maxIntervalMinutes = newConfig.maxIntervalMinutes,
                autoExportFrequency = newConfig.autoExportFrequency,
                timeFormat = newConfig.timeFormat,
                appTheme = newConfig.appTheme,
                debugModeEnabled = newConfig.debugModeEnabled
            )
        }
    }

    fun updateMinInterval(minutes: Int) {
        val currentConfig = configManager.loadConfig()
        // Add validation if needed, e.g., minutes in range [5, 55]
        saveAndUpdateState(currentConfig.copy(minIntervalMinutes = minutes))
    }

    fun updateMaxInterval(minutes: Int) {
        val currentConfig = configManager.loadConfig()
        // Add validation if needed, e.g., minutes in range [65, 115]
        saveAndUpdateState(currentConfig.copy(maxIntervalMinutes = minutes))
    }

    fun updateAutoExportFrequency(frequency: String) {
        val currentConfig = configManager.loadConfig()
        saveAndUpdateState(currentConfig.copy(autoExportFrequency = frequency))
    }

    fun updateTimeFormat(format: String) {
        val currentConfig = configManager.loadConfig()
        saveAndUpdateState(currentConfig.copy(timeFormat = format))
    }

    fun updateAppTheme(theme: String) {
        val currentConfig = configManager.loadConfig()
        saveAndUpdateState(currentConfig.copy(appTheme = theme))
        // For theme change requiring restart:
        _uiState.update { it.copy(snackbarMessage = "Theme will update on app restart.") }
    }

    fun updateDebugMode(enabled: Boolean) {
        val currentConfig = configManager.loadConfig()
        saveAndUpdateState(currentConfig.copy(debugModeEnabled = enabled))
    }

    fun exportConfig(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = configManager.exportToUri(uri)
            val message = if (success) "Config exported successfully." else "Config export failed."
            onResult(success, message)
            // Optionally show snackbar via _uiState if onResult isn't sufficient
        }
    }

    fun importConfig(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = configManager.importConfig(uri)
            if (success) {
                loadSettings() // Reload settings after successful import
            }
            val message = if (success) "Config imported successfully. Reloaded settings." else "Config import failed."
            onResult(success, message)
        }
    }

    fun resetConfigToDefault(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = configManager.resetToDefaultConfig()
            if (success) {
                loadSettings() // Reload settings after successful reset
            }
            val message = if (success) "Config reset to default. Reloaded settings." else "Config reset failed."
            onResult(success, message)
        }
    }

    fun exportData(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = dataManager.exportToUri(uri)
            val message = if (success) "Data exported successfully." else "Data export failed."
            onResult(success, message)
        }
    }

    fun importData(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            // Note: Data import doesn't directly affect SettingsUiState,
            // but good to provide feedback.
            val success = dataManager.importFromCSV(uri)
            val message = if (success) "Data imported successfully." else "Data import failed."
            onResult(success, message)
        }
    }

    fun resetDatabase(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            dataManager.resetDatabase() // Assuming this always succeeds or handles its own errors
            // For simplicity, let's assume success unless an exception is thrown,
            // which viewModelScope would catch if not handled in resetDatabase.
            val success = true // Or add error handling in DataManager.resetDatabase
            val message = "Database reset successfully."
            onResult(success, message)
        }
    }

    fun snackbarMessageShown() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    suspend fun generateDataExportFilename(): String {
        val mostRecentEntry = dataManager.getMostRecentEntry()
        return if (mostRecentEntry != null) {
            val dateString = configManager.formatTimestampForFilename(mostRecentEntry.timestamp)
            "mood_tracker_data_$dateString.csv"
        } else {
            "mood_tracker_data.csv"
        }
    }
}