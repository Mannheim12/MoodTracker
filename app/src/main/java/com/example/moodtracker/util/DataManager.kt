package com.example.moodtracker.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.moodtracker.data.AppDatabase
import com.example.moodtracker.model.Constants
import com.example.moodtracker.model.MoodEntry
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.util.Calendar

/**
 * Handles reading and writing mood data using Room database and CSV export
 */
class DataManager(private val context: Context) {

    private val hourIdFormat = SimpleDateFormat("yyyyMMddHH", Locale.US)
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

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
     * Export all entries to a CSV file at the given URI
     * @param uri The URI to export to
     * @return true if export was successful, false otherwise
     */
    suspend fun exportToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get all entries from Room
            val entries = database.moodEntryDao().getAllEntries()

            // Use content resolver to write to the URI
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    // Use Apache Commons CSV for proper formatting
                    val printer = CSVPrinter(
                        writer,
                        CSVFormat.DEFAULT.builder()
                            .setHeader("id", "timestamp", "mood")
                            .build()
                    )

                    // Print records
                    entries.forEach { entry ->
                        val formattedDate = fullDateFormat.format(Date(entry.timestamp))
                        printer.printRecord(entry.id, formattedDate, entry.moodName)
                    }

                    printer.flush()
                }
            }
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    /**
     * Import mood entries from a CSV file
     * @param uri The URI of the CSV file to import
     * @return true if import was successful, false otherwise
     */
    suspend fun importFromCSV(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = inputStream.bufferedReader()

                // Use Apache Commons CSV for proper CSV parsing
                val csvParser = CSVFormat.DEFAULT.builder()
                    .setHeader("id", "timestamp", "mood")
                    .setSkipHeaderRecord(true)
                    .build()
                    .parse(reader)

                for (record in csvParser) {
                    val id = record.get("id")
                    val timestampStr = record.get("timestamp")
                    val moodName = record.get("mood")

                    val timestamp = try {
                        fullDateFormat.parse(timestampStr)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        System.currentTimeMillis()
                    }

                    // Create and save the entry
                    val entry = MoodEntry(id, timestamp, moodName)
                    database.moodEntryDao().insertOrUpdateEntry(entry)
                }

                return@withContext true
            }
            return@withContext false
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
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
     * Get all mood entries
     * @return List of all mood entries
     */
    suspend fun getAllEntries(): List<MoodEntry> = withContext(Dispatchers.IO) {
        return@withContext database.moodEntryDao().getAllEntries()
    }

    /**
     * Formats an hour ID (like "2023111522") into a displayable hour string (like "10 PM").
     * Assumes the input hourId, if not empty, is in the valid "yyyyMMddHH" format.
     * Uses the device's default locale for AM/PM formatting.
     *
     * @param hourId The hour ID string
     * @return The formatted hour string (e.g., "10 PM"), or "N/A" if the input is empty.
     */
    fun formatHourIdForDisplay(hourId: String): String {
        // Handle the edge case where the preference might not have been set yet
        if (hourId.isEmpty()) {
            return "N/A"
        }

        // Proceed assuming hourId is valid yyyyMMddHH format based on user requirement
        val hourOfDay = hourId.takeLast(2).toInt() // Potential NumberFormatException if assumption violated

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val sdfHour = SimpleDateFormat("h a", Locale.getDefault())
        return sdfHour.format(calendar.time)
    }
}