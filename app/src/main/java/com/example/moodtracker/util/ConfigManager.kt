package com.example.moodtracker.util

import android.content.Context
import android.net.Uri
import com.example.moodtracker.model.Constants
import com.example.moodtracker.model.Mood
import java.io.File
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Handles reading and writing configuration using .json file
 */
class ConfigManager(private val context: Context) {
    // Data class for configuration
    data class Config(
        val minIntervalMinutes: Int = Constants.MIN_INTERVAL_MINUTES,
        val maxIntervalMinutes: Int = Constants.MAX_INTERVAL_MINUTES,
        val moods: List<Mood> = Constants.DEFAULT_MOODS
    )

    // Load config from JSON file, create default if not exists
    fun loadConfig(): Config {
        try {
            val file = File(context.filesDir, Constants.CONFIG_FILE_NAME)

            // Create default config file if not exists
            if (!file.exists()) {
                saveDefaultConfig()
                return Config()
            }

            // Read config from file
            val json = file.readText()
            return parseConfig(json)
        } catch (e: Exception) {
            e.printStackTrace()
            return Config() // Return defaults on error
        }
    }

    // Load just the moods - for MoodSelectionActivity
    fun loadMoods(): List<Mood> {
        return loadConfig().moods
    }

    // Save config to file
    private fun saveConfig(config: Config) {
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
        }
        return false
    }

    // Parse JSON using Moshi
    private fun parseConfig(json: String): Config {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(Config::class.java)
        return adapter.fromJson(json) ?: Config()
    }

    // Convert to JSON using Moshi
    private fun convertConfigToJson(config: Config): String {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(Config::class.java)
        return adapter.toJson(config)
    }
}