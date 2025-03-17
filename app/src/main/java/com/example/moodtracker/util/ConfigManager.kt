package com.example.moodtracker.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import com.example.moodtracker.model.Constants
import com.example.moodtracker.model.Mood
import java.io.File

/**
 * Handles reading and writing configuration using SharedPreferences
 */
class ConfigManager(context: Context) {

    private val sharedPreferences = context.getSharedPreferences(
        "mood_tracker_preferences",
        Context.MODE_PRIVATE
    )

    // Cached moods list to avoid frequent reading
    private var cachedMoods: List<Mood>? = null

    // Cached configuration settings
    private var cachedConfig: Map<String, Int>? = null

    /**
     * Load moods from SharedPreferences or return defaults
     */
    fun loadMoods(): List<Mood> {
        // Return cached moods if available
        cachedMoods?.let { return it }

        // Check if we have stored any moods
        val moodCount = sharedPreferences.getInt("mood.count", 0)

        // If no moods stored, use defaults
        if (moodCount == 0) {
            saveDefaultMoods()
            cachedMoods = Constants.DEFAULT_MOODS
            return Constants.DEFAULT_MOODS
        }

        // Parse stored moods
        val moods = mutableListOf<Mood>()

        for (i in 0 until moodCount) {
            val name = sharedPreferences.getString("mood.$i.name", null) ?: continue
            val color = sharedPreferences.getString("mood.$i.color", "#808080") ?: "#808080"
            val dimension1 = sharedPreferences.getString("mood.$i.dimension1", "") ?: ""
            val dimension2 = sharedPreferences.getString("mood.$i.dimension2", "") ?: ""
            val dimension3 = sharedPreferences.getString("mood.$i.dimension3", "") ?: ""
            val category = sharedPreferences.getString("mood.$i.category", "") ?: ""

            moods.add(Mood(name, color, dimension1, dimension2, dimension3, category))
        }

        // If no valid moods were loaded, use defaults
        if (moods.isEmpty()) {
            saveDefaultMoods()
            cachedMoods = Constants.DEFAULT_MOODS
            return Constants.DEFAULT_MOODS
        }

        cachedMoods = moods
        return moods
    }

    /**
     * Save default moods to SharedPreferences
     */
    private fun saveDefaultMoods() {
        val editor = sharedPreferences.edit()

        // Save mood count
        editor.putInt("mood.count", Constants.DEFAULT_MOODS.size)

        // Save each mood
        Constants.DEFAULT_MOODS.forEachIndexed { index, mood ->
            editor.putString("mood.$index.name", mood.name)
            editor.putString("mood.$index.color", mood.colorHex)
            if (mood.dimension1.isNotEmpty()) editor.putString("mood.$index.dimension1", mood.dimension1)
            if (mood.dimension2.isNotEmpty()) editor.putString("mood.$index.dimension2", mood.dimension2)
            if (mood.dimension3.isNotEmpty()) editor.putString("mood.$index.dimension3", mood.dimension3)
            if (mood.category.isNotEmpty()) editor.putString("mood.$index.category", mood.category)
        }

        editor.apply()
    }

    /**
     * Load configuration settings
     * @return Map of configuration settings with default values if not found
     */
    fun loadConfig(): Map<String, Int> {
        // Return cached config if available
        cachedConfig?.let { return it }

        val config = mutableMapOf<String, Int>()

        // Set default values
        config["min_interval_minutes"] = sharedPreferences.getInt(
            "min_interval_minutes",
            Constants.MIN_INTERVAL_MINUTES
        )

        config["max_interval_minutes"] = sharedPreferences.getInt(
            "max_interval_minutes",
            Constants.MAX_INTERVAL_MINUTES
        )

        config["retry_window_minutes"] = sharedPreferences.getInt(
            "retry_window_minutes",
            Constants.RETRY_WINDOW_MINUTES
        )

        // Validate values
        if (config["min_interval_minutes"]!! < 5) {
            config["min_interval_minutes"] = 5
            sharedPreferences.edit().putInt("min_interval_minutes", 5).apply()
        }

        if (config["max_interval_minutes"]!! > 120) {
            config["max_interval_minutes"] = 120
            sharedPreferences.edit().putInt("max_interval_minutes", 120).apply()
        }

        if (config["min_interval_minutes"]!! >= config["max_interval_minutes"]!!) {
            config["min_interval_minutes"] = Constants.MIN_INTERVAL_MINUTES
            config["max_interval_minutes"] = Constants.MAX_INTERVAL_MINUTES
            sharedPreferences.edit()
                .putInt("min_interval_minutes", Constants.MIN_INTERVAL_MINUTES)
                .putInt("max_interval_minutes", Constants.MAX_INTERVAL_MINUTES)
                .apply()
        }

        if (config["retry_window_minutes"]!! < 1 || config["retry_window_minutes"]!! > 15) {
            config["retry_window_minutes"] = Constants.RETRY_WINDOW_MINUTES
            sharedPreferences.edit().putInt("retry_window_minutes", Constants.RETRY_WINDOW_MINUTES).apply()
        }

        cachedConfig = config
        return config
    }

    /**
     * Clear the cached config to force a reload
     */
    fun clearCache() {
        cachedMoods = null
        cachedConfig = null
    }
}