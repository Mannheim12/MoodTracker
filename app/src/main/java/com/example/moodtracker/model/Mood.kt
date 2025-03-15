package com.example.moodtracker.model

import android.graphics.Color

// Data class to represent a mood
data class Mood(
    val name: String,
    val colorHex: String,
    // Optional attributes for positioning in the grid
    val dimension1: String = "", // positive/neutral/negative
    val dimension2: String = "", // inward/outward
    val dimension3: String = "", // high/low energy
    val category: String = ""    // for "Other" moods
) {
    fun getColor(): Int {
        return try {
            Color.parseColor(colorHex)
        } catch (e: Exception) {
            Color.GRAY // Default color if parsing fails
        }
    }
}

// Data class to represent a mood entry in the CSV
data class MoodEntry(
    val id: String,          // YYYYMMDDHH format
    val timestamp: Long,     // Exact time recorded
    val moodName: String     // The selected mood
)

// Constants for file paths
object Constants {
    const val CONFIG_FILE_NAME = "mood_tracker.config"
    const val DATA_FILE_NAME = "mood_tracker_data.csv"
    const val NOTIFICATION_CHANNEL_ID = "mood_tracker_channel"
    const val NOTIFICATION_ID = 1001
    const val WORKER_TAG = "mood_check_worker"

    // Time constants
    const val MIN_INTERVAL_MINUTES = 30
    const val MAX_INTERVAL_MINUTES = 90
    const val RETRY_WINDOW_MINUTES = 5

    // Default moods if config file is missing or corrupt
    val DEFAULT_MOODS = listOf(
        // Positive
        Mood("Triumphant", "#4CAF50", "Positive", "Inward", "High"),
        Mood("Content", "#8BC34A", "Positive", "Inward", "Low"),
        Mood("Exuberant", "#CDDC39", "Positive", "Outward", "High"),
        Mood("Friendly", "#FFEB3B", "Positive", "Outward", "Low"),

        // Neutral
        Mood("Driven", "#FFC107", "Neutral", "Inward", "High"),
        Mood("Neutral", "#FF9800", "Neutral", "Inward", "Low"),
        Mood("Collaborative", "#FF5722", "Neutral", "Outward", "High"),
        Mood("Cordial", "#F44336", "Neutral", "Outward", "Low"),

        // Negative
        Mood("Panicked", "#E91E63", "Negative", "Inward", "High"),
        Mood("Hopeless", "#9C27B0", "Negative", "Inward", "Low"),
        Mood("Angry", "#673AB7", "Negative", "Outward", "High"),
        Mood("Detached", "#3F51B5", "Negative", "Outward", "Low"),

        // Other
        Mood("Aroused", "#2196F3", "Other", "", "", "Other"),
        Mood("N/A", "#03A9F4", "Other", "", "", "Other"),
        Mood("Asleep", "#00BCD4", "Other", "", "", "Other")
    )
}