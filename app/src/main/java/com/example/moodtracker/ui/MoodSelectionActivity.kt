package com.example.moodtracker.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.moodtracker.R
import com.example.moodtracker.model.Mood
import com.example.moodtracker.util.ConfigManager
import com.example.moodtracker.util.DataManager
import com.example.moodtracker.worker.MoodCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Activity that displays the mood selection grid
 */
class MoodSelectionActivity : AppCompatActivity() {

    private lateinit var configManager: ConfigManager
    private lateinit var dataManager: DataManager
    private lateinit var gridLayout: GridLayout
    private lateinit var timeTextView: TextView

    // Flag to prevent double recording
    private var moodRecorded = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_selection)

        // Setup back press handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If back is pressed without selecting a mood, record "Asleep"
                if (!moodRecorded) {
                    recordMood("Asleep")
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        // Keep screen on while selecting a mood
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Handle lock screen behavior correctly
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        configManager = ConfigManager(this)
        dataManager = DataManager(this)

        gridLayout = findViewById(R.id.mood_grid)
        timeTextView = findViewById(R.id.time_text)

        // Set the current time
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        val timeString = getString(R.string.current_time_format, sdf.format(Date()))
        timeTextView.text = timeString

        // Set a timeout to automatically close the activity if no mood is selected
        handler.postDelayed({
            if (!isFinishing && !moodRecorded) {
                recordMood("Asleep")
            }
        }, 60000) // Close after 1 minute of inactivity

        // Load moods and create the grid
        lifecycleScope.launch {
            createMoodGrid()
        }
    }

    // Create the mood selection grid
    private suspend fun createMoodGrid() = withContext(Dispatchers.Main) {
        val moods = withContext(Dispatchers.IO) {
            configManager.loadMoods()
        }

        // Group moods by dimension
        val positiveInwardHigh = moods.filter { it.dimension1 == "Positive" && it.dimension2 == "Inward" && it.dimension3 == "High" }
        val positiveInwardLow = moods.filter { it.dimension1 == "Positive" && it.dimension2 == "Inward" && it.dimension3 == "Low" }
        val positiveOutwardHigh = moods.filter { it.dimension1 == "Positive" && it.dimension2 == "Outward" && it.dimension3 == "High" }
        val positiveOutwardLow = moods.filter { it.dimension1 == "Positive" && it.dimension2 == "Outward" && it.dimension3 == "Low" }

        val neutralInwardHigh = moods.filter { it.dimension1 == "Neutral" && it.dimension2 == "Inward" && it.dimension3 == "High" }
        val neutralInwardLow = moods.filter { it.dimension1 == "Neutral" && it.dimension2 == "Inward" && it.dimension3 == "Low" }
        val neutralOutwardHigh = moods.filter { it.dimension1 == "Neutral" && it.dimension2 == "Outward" && it.dimension3 == "High" }
        val neutralOutwardLow = moods.filter { it.dimension1 == "Neutral" && it.dimension2 == "Outward" && it.dimension3 == "Low" }

        val negativeInwardHigh = moods.filter { it.dimension1 == "Negative" && it.dimension2 == "Inward" && it.dimension3 == "High" }
        val negativeInwardLow = moods.filter { it.dimension1 == "Negative" && it.dimension2 == "Inward" && it.dimension3 == "Low" }
        val negativeOutwardHigh = moods.filter { it.dimension1 == "Negative" && it.dimension2 == "Outward" && it.dimension3 == "High" }
        val negativeOutwardLow = moods.filter { it.dimension1 == "Negative" && it.dimension2 == "Outward" && it.dimension3 == "Low" }

        val other = moods.filter { it.category == "Other" }

        // Clear the grid
        gridLayout.removeAllViews()

        // Set grid dimensions
        val cols = 4
        val rows = 4

        gridLayout.columnCount = cols
        gridLayout.rowCount = rows

        // Create the grid layout
        val orderedMoods = listOf(
            // First row - Positive
            positiveInwardHigh, positiveInwardLow, positiveOutwardHigh, positiveOutwardLow,
            // Second row - Neutral
            neutralInwardHigh, neutralInwardLow, neutralOutwardHigh, neutralOutwardLow,
            // Third row - Negative
            negativeInwardHigh, negativeInwardLow, negativeOutwardHigh, negativeOutwardLow,
            // Fourth row - Other
            other.getOrNull(0)?.let { listOf(it) } ?: emptyList(),
            other.getOrNull(1)?.let { listOf(it) } ?: emptyList(),
            other.getOrNull(2)?.let { listOf(it) } ?: emptyList(),
            emptyList() // Empty slot
        )

        for (rowIndex in 0 until rows) {
            for (colIndex in 0 until cols) {
                val index = rowIndex * cols + colIndex
                val currentMoods = orderedMoods.getOrNull(index) ?: continue

                if (currentMoods.isEmpty()) continue

                val mood = currentMoods.first()
                val moodButton = createMoodButton(mood)

                val params = GridLayout.LayoutParams()
                params.width = 0
                params.height = 0
                params.rowSpec = GridLayout.spec(rowIndex, 1, 1f)
                params.columnSpec = GridLayout.spec(colIndex, 1, 1f)
                params.setMargins(8, 8, 8, 8)

                gridLayout.addView(moodButton, params)
            }
        }
    }

    // Create a button for a specific mood using LinearLayout
    private fun createMoodButton(mood: Mood): View {
        // Create a shape drawable for the background
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.RECTANGLE
        shape.cornerRadius = resources.getDimension(R.dimen.card_corner_radius)
        shape.setColor(mood.getColor())

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16)
            background = shape
            elevation = resources.getDimension(R.dimen.card_elevation)
        }

        val textView = TextView(this).apply {
            text = mood.name
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        layout.addView(textView)

        // Set click listener to record the mood
        layout.setOnClickListener {
            recordMood(mood.name)
        }

        return layout
    }

    // Record the selected mood and close the activity
    private fun recordMood(moodName: String) {
        // Prevent double recording
        if (moodRecorded) return
        moodRecorded = true

        // Cancel the timeout handler
        handler.removeCallbacksAndMessages(null)

        // Record the mood and schedule next check
        lifecycleScope.launch {
            try {
                // Record the mood in background
                withContext(Dispatchers.IO) {
                    dataManager.addMoodEntry(moodName)
                }

                // Save tracking state
                getSharedPreferences("mood_tracker_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("was_tracking", true)
                    .apply()

                // MoodCheckWorker will schedule the next mood check
            } catch (e: Exception) {
                // Log error but don't crash
                e.printStackTrace()
            } finally {
                // Always finish the activity
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Make sure we record a mood if the user navigates away
        if (!moodRecorded) {
            recordMood("Asleep")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove any pending callbacks
        handler.removeCallbacksAndMessages(null)
    }
}