package com.example.moodtracker.model

import android.graphics.Color
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

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

// Data class to represent a mood entry in the database
@Entity(tableName = "mood_entries", indices = [Index(value = ["id"], unique = true)])
data class MoodEntry(
    @PrimaryKey val id: String,          // YYYYMMDDHH format
    val timestamp: Long,                 // Exact time recorded
    val moodName: String                 // The selected mood
)

// Constants for file paths and other settings
object Constants {
    const val CONFIG_FILE_NAME = "mood_tracker_config.json"
    const val DATA_FILE_NAME = "mood_tracker_data.csv"
    const val EXPORT_DIRECTORY_NAME = "MoodTracker" // For later
    const val NOTIFICATION_CHANNEL_ID = "mood_tracker_channel"
    const val NOTIFICATION_ID = 1001
    const val WORKER_TAG = "mood_check_worker"

    // Time constants
    const val MIN_INTERVAL_MINUTES = 30
    const val MAX_INTERVAL_MINUTES = 90

    // Default moods if config file is missing or corrupt
    val DEFAULT_MOODS = listOf(
        // Positive
        Mood("Triumphant", "#FFEB3B", "Positive", "Inward", "High"),    // Bright yellow
        Mood("Content", "#FFECB3", "Positive", "Inward", "Low"),        // Original pale gold
        Mood("Exuberant", "#FF6D00", "Positive", "Outward", "High"),    // Pure orange
        Mood("Friendly", "#FFE0B2", "Positive", "Outward", "Low"),      // Soft cream

        // Neutral
        Mood("Driven", "#1976D2", "Neutral", "Inward", "High"),         // Strong blue
        Mood("Neutral", "#BBDEFB", "Neutral", "Inward", "Low"),         // Very light blue
        Mood("Collaborative", "#4CAF50", "Neutral", "Outward", "High"), // Green
        Mood("Cordial", "#A5D6A7", "Neutral", "Outward", "Low"),        // Light green

        // Negative
        Mood("Panicked", "#212121", "Negative", "Inward", "High"),      // Almost black
        Mood("Hopeless", "#757575", "Negative", "Inward", "Low"),       // Medium gray
        Mood("Angry", "#FF0000", "Negative", "Outward", "High"),        // Pure red
        Mood("Detached", "#9E9E9E", "Negative", "Outward", "Low"),      // Light gray

        // Other
        Mood("Aroused", "#E91E63", "Other", "", "", "Other"),   // Pink
        Mood("N/A", "#F5F5F5", "Other", "", "", "Other"),       // Off-white
        Mood("Asleep", "#464268", "Other", "", "", "Other")     // Dark purple-gray
    )
}