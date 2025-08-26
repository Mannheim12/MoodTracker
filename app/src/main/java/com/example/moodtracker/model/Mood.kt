package com.example.moodtracker.model

import android.graphics.Color
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Data class to represent a mood
data class Mood(
    val name: String,
    val colorHex: String,
    val valence: Int = 0,    // -1, 0, +1
    val arousal: Int = 0,    // -1, 0, +1
    val dominance: Int = 0   // -1, 0, +1
) {
    fun getColor(): Int {
        return try {
            Color.parseColor(colorHex)
        } catch (e: Exception) {
            Color.GRAY // Default color if parsing fails
        }
    }
    // Determines if text should be white or black based on background color brightness
    fun getTextColor(): Int {
        val color = getColor()

        // Extract RGB components
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        // Calculate luminance using standard formula (perceived brightness)
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255

        // Use white text on dark backgrounds, black text on light backgrounds
        return if (luminance > 0.5) Color.BLACK else Color.WHITE
    }
}

// Data class to represent a mood entry in the database
@Entity(tableName = "mood_entries", indices = [Index(value = ["id"], unique = true)])
data class MoodEntry(
    @PrimaryKey val id: String,          // YYYYMMDDHH format
    val timestamp: Long,                 // Exact time recorded
    val moodName: String,                // The selected mood
    val timeZoneId: String               // The time zone ID when the entry was recorded
)

// Constants for file paths and other settings
object Constants {
    const val CONFIG_FILE_NAME = "mood_tracker_config.json"
    const val DATA_FILE_NAME = "mood_tracker_data.csv"
    const val NOTIFICATION_CHANNEL_ID = "mood_tracker_channel"
    const val NOTIFICATION_ID = 1001
    const val WORKER_TAG = "mood_check_worker"

    // Time constants
    const val MIN_INTERVAL_MINUTES = 30
    const val MAX_INTERVAL_MINUTES = 90

    // Default moods if config file is missing or corrupt
    val DEFAULT_MOODS = listOf(
        // Valence/Arousal/Dominance information: -1 = low, 0 = medium, 1 = high, 2 = N/A
        // Top row (V=+1)
        Mood("Joyful", "#FFD600", valence=1, arousal=1, dominance=0),      // Bright yellow
        Mood("Happy", "#FF9A1F", valence=1, arousal=0, dominance=0),       // Tangerine orange
        Mood("Content", "#8DD466", valence=1, arousal=-1, dominance=0),    // Soft spring green

        // Middle row (V=0)
        Mood("Driven", "#1E8CFF", valence=0, arousal=1, dominance=0),      // Clear sky blue
        Mood("Neutral", "#5FAFAF", valence=0, arousal=0, dominance=0),     // Subtle teal
        Mood("Bored", "#B8AD73", valence=0, arousal=-1, dominance=0),      // Muted khaki

        // Bottom row (V=-1)
        Mood("Angry", "#E12727", valence=-1, arousal=1, dominance=1),      // Vivid red
        Mood("Fearful", "#111111", valence=-1, arousal=1, dominance=-1),   // Near-black
        Mood("Anxious", "#7F7F7F", valence=-1, arousal=0, dominance=0),    // Medium gray
        Mood("Sad", "#2C3652", valence=-1, arousal=-1, dominance=0),       // Dark blue-gray

        // Special mood for timeout/sleep
        Mood("Asleep", "#464268", valence=2, arousal=2, dominance=2),     // Dark blue-gray

        // N/A mood for custom entry (future feature)
        Mood("N/A", "#FFFFFF", valence=2, arousal=2, dominance=2)          // White
    )
}