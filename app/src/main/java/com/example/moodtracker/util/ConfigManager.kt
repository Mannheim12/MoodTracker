package com.example.moodtracker.util

import android.os.Environment
import com.example.moodtracker.model.Constants
import com.example.moodtracker.model.Mood
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

/**
 * Handles reading and writing the configuration file
 */
class ConfigManager {

    // Cached moods list to avoid frequent file reads
    private var cachedMoods: List<Mood>? = null

    // Cached configuration settings
    private var cachedConfig: Map<String, Int>? = null

    // Get the configuration file
    fun getConfigFile(): File {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }
        return File(documentsDir, Constants.CONFIG_FILE_NAME)
    }

    // Load moods from config file
    fun loadMoods(): List<Mood> {
        // Return cached moods if available
        cachedMoods?.let { return it }

        val configFile = getConfigFile()

        // Safety check: Only read from our specific config file
        if (!isValidConfigFile(configFile)) {
            cachedMoods = Constants.DEFAULT_MOODS
            return Constants.DEFAULT_MOODS
        }

        val properties = Properties()

        // If config file doesn't exist or can't be read, create it with defaults and return defaults
        if (!configFile.exists()) {
            writeDefaultConfig()
            cachedMoods = Constants.DEFAULT_MOODS
            return Constants.DEFAULT_MOODS
        }

        try {
            // Verify file again before reading
            if (!isValidConfigFile(configFile)) {
                cachedMoods = Constants.DEFAULT_MOODS
                return Constants.DEFAULT_MOODS
            }

            FileInputStream(configFile).use { properties.load(it) }

            // Parse moods section
            val moodCount = properties.getProperty("mood.count", "15").toIntOrNull() ?: 15
            val moods = mutableListOf<Mood>()

            for (i in 0 until moodCount) {
                val name = properties.getProperty("mood.$i.name") ?: continue
                val color = properties.getProperty("mood.$i.color", "#808080")
                val dimension1 = properties.getProperty("mood.$i.dimension1", "")
                val dimension2 = properties.getProperty("mood.$i.dimension2", "")
                val dimension3 = properties.getProperty("mood.$i.dimension3", "")
                val category = properties.getProperty("mood.$i.category", "")

                moods.add(Mood(name, color, dimension1, dimension2, dimension3, category))
            }

            // If no moods were loaded, use defaults
            if (moods.isEmpty()) {
                cachedMoods = Constants.DEFAULT_MOODS
                return Constants.DEFAULT_MOODS
            }

            cachedMoods = moods
            return moods

        } catch (e: Exception) {
            // In case of any error, return defaults
            cachedMoods = Constants.DEFAULT_MOODS
            return Constants.DEFAULT_MOODS
        }
    }

    /**
     * Load configuration settings from the config file
     * @return Map of configuration settings with default values if not found
     */
    fun loadConfig(): Map<String, Int> {
        // Return cached config if available
        cachedConfig?.let { return it }

        val configFile = getConfigFile()
        val config = mutableMapOf<String, Int>()

        // Set default values
        config["min_interval_minutes"] = Constants.MIN_INTERVAL_MINUTES
        config["max_interval_minutes"] = Constants.MAX_INTERVAL_MINUTES
        config["retry_window_minutes"] = Constants.RETRY_WINDOW_MINUTES

        // If config file doesn't exist, create it with defaults and return defaults
        if (!configFile.exists() || !isValidConfigFile(configFile)) {
            writeDefaultConfig()
            cachedConfig = config
            return config
        }

        try {
            val properties = Properties()
            FileInputStream(configFile).use { properties.load(it) }

            // Load configuration values
            config["min_interval_minutes"] = properties.getProperty("min_interval_minutes")?.toIntOrNull()
                ?: Constants.MIN_INTERVAL_MINUTES
            config["max_interval_minutes"] = properties.getProperty("max_interval_minutes")?.toIntOrNull()
                ?: Constants.MAX_INTERVAL_MINUTES
            config["retry_window_minutes"] = properties.getProperty("retry_window_minutes")?.toIntOrNull()
                ?: Constants.RETRY_WINDOW_MINUTES

            // Validate values
            if (config["min_interval_minutes"]!! < 5) {
                config["min_interval_minutes"] = 5
            }
            if (config["max_interval_minutes"]!! > 120) {
                config["max_interval_minutes"] = 120
            }
            if (config["min_interval_minutes"]!! >= config["max_interval_minutes"]!!) {
                config["min_interval_minutes"] = Constants.MIN_INTERVAL_MINUTES
                config["max_interval_minutes"] = Constants.MAX_INTERVAL_MINUTES
            }
            if (config["retry_window_minutes"]!! < 1 || config["retry_window_minutes"]!! > 15) {
                config["retry_window_minutes"] = Constants.RETRY_WINDOW_MINUTES
            }

            cachedConfig = config
            return config

        } catch (e: Exception) {
            // In case of any error, return defaults
            cachedConfig = config
            return config
        }
    }

    // Write default config if none exists
    private fun writeDefaultConfig() {
        val configFile = getConfigFile()

        // Safety check: Only write to our specific config file
        if (!isValidConfigFile(configFile)) {
            return
        }

        val properties = Properties()

        // Save mood count
        properties.setProperty("mood.count", Constants.DEFAULT_MOODS.size.toString())

        // Save each mood
        Constants.DEFAULT_MOODS.forEachIndexed { index, mood ->
            properties.setProperty("mood.$index.name", mood.name)
            properties.setProperty("mood.$index.color", mood.colorHex)
            if (mood.dimension1.isNotEmpty()) properties.setProperty("mood.$index.dimension1", mood.dimension1)
            if (mood.dimension2.isNotEmpty()) properties.setProperty("mood.$index.dimension2", mood.dimension2)
            if (mood.dimension3.isNotEmpty()) properties.setProperty("mood.$index.dimension3", mood.dimension3)
            if (mood.category.isNotEmpty()) properties.setProperty("mood.$index.category", mood.category)
        }

        // Save scheduling parameters
        properties.setProperty("min_interval_minutes", Constants.MIN_INTERVAL_MINUTES.toString())
        properties.setProperty("max_interval_minutes", Constants.MAX_INTERVAL_MINUTES.toString())
        properties.setProperty("retry_window_minutes", Constants.RETRY_WINDOW_MINUTES.toString())

        // Write to file
        try {
            // Final safety check before writing
            if (!isValidConfigFile(configFile)) {
                return
            }

            FileOutputStream(configFile).use { properties.store(it, "Mood Tracker Configuration") }
        } catch (e: Exception) {
            // Handle error (could log it in a real app)
        }
    }

    // Safety check to ensure we only read from and write to our specific config file
    private fun isValidConfigFile(file: File): Boolean {
        // Check file name
        if (file.name != Constants.CONFIG_FILE_NAME) {
            return false
        }

        // Check that it's in the Documents directory
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!file.absolutePath.startsWith(documentsDir.absolutePath)) {
            return false
        }

        return true
    }

    /**
     * Clear the cached config to force a reload from disk
     */
    fun clearCache() {
        cachedMoods = null
        cachedConfig = null
    }
}