package com.example.moodtracker.util

import android.content.Context
import android.os.Environment
import com.example.moodtracker.data.AppDatabase
import com.example.moodtracker.model.Constants
import com.example.moodtracker.model.MoodEntry
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles reading and writing mood data using Room database and CSV export
 */
class DataManager(private val context: Context) {

    private val hourIdFormat = SimpleDateFormat("yyyyMMddHH", Locale.US)
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // CSV header columns
    private val csvHeaders = "id,timestamp,mood\n"

    // Get the Room database instance
    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    /**
     * Generate an hour ID for a given timestamp
     * @param timestamp The timestamp to generate an ID for. Default is current time.
     * @return Hour ID in YYYYMMDDHH format
     */
    fun generateHourId(timestamp: Long = System.currentTimeMillis()): String {
        val date = Date(timestamp)
        return hourIdFormat.format(date)
    }

    /**
     * Add a mood entry to the database and export to CSV
     * @param moodName The mood to record
     * @param hourId Optional hour ID. If not provided, uses current hour.
     * @param timestamp Optional timestamp. If not provided, uses current time.
     */
    suspend fun addMoodEntry(moodName: String, hourId: String? = null, timestamp: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        // Use provided hour ID or generate from current time
        val id = hourId ?: generateHourId(timestamp)

        // Create entry and save to database
        val entry = MoodEntry(id, timestamp, moodName)
        database.moodEntryDao().insertOrUpdateEntry(entry)

        // Export to CSV
        exportToCsv()
    }

    /**
     * Check if a mood entry exists for the specified hour
     * @param hourId The hour ID to check
     * @return true if an entry exists, false otherwise
     */
    suspend fun hasEntryForHour(hourId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext database.moodEntryDao().hasEntryForHour(hourId)
    }

    /**
     * Export all entries to a CSV file in the Downloads/MoodTracker folder
     */
    private suspend fun exportToCsv() = withContext(Dispatchers.IO) {
        try {
            // Get all entries from Room
            val entries = database.moodEntryDao().getAllEntries()

            // Get the appropriate directory
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val moodTrackerDir = File(downloadsDir, Constants.EXPORT_DIRECTORY_NAME)

            // Create directory if it doesn't exist
            if (!moodTrackerDir.exists()) {
                moodTrackerDir.mkdirs()
            }

            // Create CSV file
            val csvFile = File(moodTrackerDir, Constants.DATA_FILE_NAME)

            // Write to file
            FileOutputStream(csvFile).use { outputStream ->
                BufferedWriter(OutputStreamWriter(outputStream)).use { writer ->
                    writer.write(csvHeaders)

                    for (entry in entries) {
                        val formattedDate = fullDateFormat.format(Date(entry.timestamp))
                        writer.write("${entry.id},${formattedDate},${entry.moodName}\n")
                    }

                    writer.flush()
                }
            }
        } catch (e: Exception) {
            // Handle error
            e.printStackTrace()
        }
    }

    /**
     * For debugging: get number of entries in database
     */
    suspend fun getEntryCount(): Int = withContext(Dispatchers.IO) {
        return@withContext database.moodEntryDao().getAllEntries().size
    }

    /**
     * For debugging: get the most recent entry
     */
    suspend fun getMostRecentEntry(): MoodEntry? = withContext(Dispatchers.IO) {
        val entries = database.moodEntryDao().getAllEntries()
        return@withContext entries.maxByOrNull { it.timestamp }
    }

    /**
     * Get the path where CSV is exported for display purposes
     */
    fun getExportPath(): String {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return "${downloadsDir.absolutePath}/${Constants.EXPORT_DIRECTORY_NAME}/${Constants.DATA_FILE_NAME}"
    }

    /**
     * Get all mood entries
     * @return List of all mood entries
     */
    suspend fun getAllEntries(): List<MoodEntry> = withContext(Dispatchers.IO) {
        return@withContext database.moodEntryDao().getAllEntries()
    }
}