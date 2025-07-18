package com.example.moodtracker.util

import android.content.Context
import android.net.Uri
import com.example.moodtracker.model.Constants
import com.example.moodtracker.model.Mood
import java.io.File
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Handles reading and writing configuration using .json file
 */
class ConfigManager(private val context: Context) {
    // Data class for configuration
    data class Config(
        // Mood & Tracking
        val minIntervalMinutes: Int = Constants.MIN_INTERVAL_MINUTES,
        val maxIntervalMinutes: Int = Constants.MAX_INTERVAL_MINUTES,
        val moods: List<Mood> = Constants.DEFAULT_MOODS,
        // Auto-sleep hours (placeholders, actual implementation will be more complex)
        val autoSleepStartHour: Int? = null, // 24-hour format
        val autoSleepEndHour: Int? = null,   // 24-hour format

        // Data Management
        val autoExportFrequency: String = AutoExportFrequency.OFF, // Default to off

        // Appearance
        val timeFormat: String = TimeFormat.SYSTEM_DEFAULT, // Could be "12h", "24h", or "system"
        val appTheme: String = AppTheme.SYSTEM, // "light", "dark", "system"

        // Advanced
        val debugModeEnabled: Boolean = false
    )

    // Define constants for new setting options for type safety and clarity
    object TimeFormat {
        const val H12 = "12h"
        const val H24 = "24h"
        const val SYSTEM_DEFAULT = "system" // Or you might resolve this to 12h/24h at load time
    }

    object AppTheme {
        const val LIGHT = "light"
        const val DARK = "dark"
        const val SYSTEM = "system"
    }

    object AutoExportFrequency {
        const val OFF = "off"
        const val DAILY = "daily" // Example, can add more specific ones
        const val WEEKLY_SUNDAY = "weekly_sunday"
        const val WEEKLY_MONDAY = "weekly_monday"
        const val MONTHLY_FIRST = "monthly_first"
    }


    // Load config from JSON file, create default if not exists
    fun loadConfig(): Config {
        try {
            val file = File(context.filesDir, Constants.CONFIG_FILE_NAME)

            // Create default config file if not exists
            if (!file.exists()) {
                saveDefaultConfig() // This will save a Config() with new defaults
                return Config()
            }

            // Read config from file
            val json = file.readText()
            return parseConfig(json)
        } catch (e: Exception) {
            e.printStackTrace()
            // If parsing fails (e.g., old config file without new fields),
            // returning a default Config might be a good recovery strategy.
            // Alternatively, you could try to merge or handle migration.
            // For simplicity now, return default.
            return Config()
        }
    }

    // Load just the moods - for Mood Selection Activity
    fun loadMoods(): List<Mood> {
        return loadConfig().moods
    }

    // Save config to file
    fun saveConfig(config: Config) { // Made public to be called from ViewModel
        try {
            val file = File(context.filesDir, Constants.CONFIG_FILE_NAME)
            val json = convertConfigToJson(config)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Create default config
    private fun saveDefaultConfig() {
        saveConfig(Config())
    }

    /**
     * Export config to the given URI
     * @param uri The URI to export to
     * @return true if export was successful, false otherwise
     */
    fun exportToUri(uri: Uri): Boolean {
        try {
            // Get the current config
            val config = loadConfig()
            val json = convertConfigToJson(config)

            // Write to the URI
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    // Import config from file URI
    fun importConfig(uri: Uri): Boolean {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val json = input.bufferedReader().readText()
                val config = parseConfig(json)
                saveConfig(config)
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Consider more robust error handling or feedback for malformed JSON
        }
        return false
    }

    // Parse JSON using Moshi
    private fun parseConfig(json: String): Config {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(Config::class.java)
        // If fromJson returns null (e.g. malformed json), return a default config
        return adapter.fromJson(json) ?: Config()
    }

    // Convert to JSON using Moshi
    private fun convertConfigToJson(config: Config): String {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(Config::class.java)
        return adapter.toJson(config)
    }

    /**
     * Reset config to default values
     * @return true if reset was successful, false otherwise
     */
    fun resetToDefaultConfig(): Boolean {
        return try {
            // Create a new default config
            val defaultConfig = Config()
            // Save it to file
            saveConfig(defaultConfig)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Formats an hour ID (like "2023111522") into a displayable hour string
     * respecting the user's 12/24h preference.
     * Assumes the input hourId, if not empty, is in the valid "yyyyMMddHH" format.
     * Uses the device's default locale for formatting.
     *
     * @param hourId The hour ID string (e.g., "2023111522").
     * @return The formatted hour string (e.g., "10 PM" or "22:00"), or "N/A" if input is invalid.
     */
    fun formatHourIdForDisplay(hourId: String): String {
        if (hourId.length != 10) { // Basic validation for yyyyMMddHH
            return "N/A"
        }

        try {
            val hourOfDay = hourId.takeLast(2).toInt() // Extract hour (00-23)

            // Determine the correct format based on user settings
            val userTimeFormat = loadConfig().timeFormat // Accesses its own config
            val displayPattern = when (userTimeFormat) {
                TimeFormat.H24 -> "HH:00" // 24-hour format (e.g., 14:00)
                else -> "h a" // 12-hour format (e.g., 2 PM) for H12 or SYSTEM_DEFAULT
            }
            val sdfHour = SimpleDateFormat(displayPattern, Locale.getDefault())

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return sdfHour.format(calendar.time)

        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return "N/A"
        } catch (e: Exception) {
            e.printStackTrace()
            return "N/A"
        }
    }
}