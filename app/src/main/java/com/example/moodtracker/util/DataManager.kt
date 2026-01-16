package com.example.moodtracker.util

import android.content.Context
import android.net.Uri
import com.example.moodtracker.data.AppDatabase
import com.example.moodtracker.model.MoodEntry
import com.example.moodtracker.worker.MoodCheckWorker
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.util.Calendar
import java.util.TimeZone
import kotlin.random.Random

/**
 * Handles reading and writing mood data using Room database and CSV export
 */
class DataManager(private val context: Context) {
    // Keep UTC hour ID generation - this is correct
    private val hourIdFormat = SimpleDateFormat("yyyyMMddHH", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val configManager = ConfigManager(context)

    // Get the Room database instance
    private val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    /**
     * Generate a UTC-based hour ID for a given timestamp
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
        val timeZoneId = TimeZone.getDefault().id

        // Create entry and save to database
        val entry = MoodEntry(id, timestamp, moodName, timeZoneId)
        database.moodEntryDao().insertOrUpdateEntry(entry)
    }

    /**
     * Finds which hourly entries are missing from the database, starting from the first recorded entry.
     * @return A list of hour ID strings (YYYYMMDDHH) for which no entry exists.
     */
    suspend fun getMissedEntryHourIds(): List<String> = withContext(Dispatchers.IO) {
        if (!MoodCheckWorker.isTrackingActive(context)) {
            return@withContext emptyList()
        }

        // 1. Find the first-ever entry to establish a starting point.
        val oldestEntry = database.moodEntryDao().getOldestEntry()
            ?: return@withContext emptyList() // No entries means no missed entries.

        // 2. Determine the time window to check.
        val config = configManager.loadConfig()
        val timelineHours = config.timelineHours
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.HOUR_OF_DAY, -timelineHours)

        // The start time is the more recent of the two: either the oldest entry or the retention window.
        val startTime = maxOf(calendar.timeInMillis, oldestEntry.timestamp)

        // 3. Generate all hour IDs that should exist within this window.
        val expectedHourIds = mutableListOf<String>()
        calendar.timeInMillis = startTime
        while (calendar.timeInMillis < endTime) {
            expectedHourIds.add(generateHourId(calendar.timeInMillis))
            calendar.add(Calendar.HOUR_OF_DAY, 1)
        }

        if (expectedHourIds.isEmpty()) {
            return@withContext emptyList()
        }

        // 4. Get the currently pending hour ID from SharedPreferences.
        val prefs = context.getSharedPreferences(MoodCheckWorker.PREF_NAME, Context.MODE_PRIVATE)
        val pendingHourId = prefs.getString(MoodCheckWorker.PREF_HOURLY_ID, null)

        // 5. Get all existing hour IDs from the database within the calculated range.
        val existingEntries = database.moodEntryDao().getEntriesByHourIds(expectedHourIds)
        val existingHourIds = existingEntries.map { it.id }.toSet()

        // 6. A missed entry is one that was expected, doesn't exist, and is not currently pending.
        //    Sleep window hours are NOT filtered out - they should appear as missed entries
        //    so they can be auto-filled with "Asleep" by the worker.
        return@withContext expectedHourIds.filter { hourId ->
            hourId !in existingHourIds && hourId != pendingHourId
        }
    }

    /**
     * Export all entries to a CSV file at the given URI
     * Uses ConfigManager for consistent timezone handling
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
                            .setHeader("id", "utc_timestamp_millis", "mood", "timezone")
                            .build()
                    )

                    // Print records with the raw UTC timestamp
                    entries.forEach { entry ->
                        printer.printRecord(entry.id, entry.timestamp, entry.moodName, entry.timeZoneId)
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
                    .setHeader("id", "utc_timestamp_millis", "mood", "timezone")
                    .setSkipHeaderRecord(true)
                    .build()
                    .parse(reader)

                for (record in csvParser) {
                    val id = record.get("id")
                    val timestampStr = record.get("utc_timestamp_millis")
                    val moodName = record.get("mood")
                    val timeZoneId = record.get("timezone") ?: TimeZone.getDefault().id

                    // Parse the raw long timestamp from the CSV
                    val timestamp = try {
                        timestampStr.toLong()
                    } catch (e: NumberFormatException) {
                        System.currentTimeMillis() // Fallback for malformed data
                    }

                    // Create and save the entry
                    val entry = MoodEntry(id, timestamp, moodName, timeZoneId)
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
     * Get the entry with the most recent hour ID (furthest forward in time)
     */
    suspend fun getMostRecentEntry(): MoodEntry? = withContext(Dispatchers.IO) {
        val entries = database.moodEntryDao().getAllEntries()
        return@withContext entries.maxByOrNull { it.id }
    }

    /**
     * Get all mood entries
     * @return List of all mood entries
     */
    suspend fun getAllEntries(): List<MoodEntry> = withContext(Dispatchers.IO) {
        return@withContext database.moodEntryDao().getAllEntries()
    }

    /**
     * Get a specific mood entry by hour ID
     * @param hourId The hour ID to look up
     * @return The mood entry if it exists, null otherwise
     */
    suspend fun getEntryByHourId(hourId: String): MoodEntry? = withContext(Dispatchers.IO) {
        return@withContext database.moodEntryDao().getEntryByHourId(hourId)
    }

    /**
     * Deletes all mood entries from the database.
     */
    suspend fun resetDatabase() = withContext(Dispatchers.IO) {
        database.moodEntryDao().deleteAllEntries()
    }

    /**
     * Retrieves mood entries recorded since a given timestamp, ordered by most recent first.
     * @param sinceTimestamp The Unix timestamp (milliseconds) from which to retrieve entries.
     * @return List of mood entries.
     */
    suspend fun getMoodEntriesSince(sinceTimestamp: Long): List<MoodEntry> = withContext(Dispatchers.IO) {
        // Ensure your MoodEntryDao has a corresponding query method.
        // If your existing getAllEntries is already sorted by timestamp descending,
        // you could filter in memory, but a direct DB query is more efficient for larger datasets.
        // Let's assume we add a new DAO method for this.
        return@withContext database.moodEntryDao().getEntriesSince(sinceTimestamp)
    }

    /**
     * For debugging: Fills the database with sample data over the last 48 hours.
     * Does nothing if the database already contains entries.
     */
    suspend fun populateWithSampleData() = withContext(Dispatchers.IO) {
        if (getEntryCount() > 0) return@withContext // Only populate if empty

        val sampleMoods = listOf("Happy", "Content", "Neutral", "Anxious", "Sad", "Driven", "Bored")
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.HOUR_OF_DAY, -48) // Start 48 hours ago

        val endTime = System.currentTimeMillis()

        while (calendar.timeInMillis < endTime) {
            // Randomly decide whether to skip an hour to create a "missed" entry
            if (Random.nextInt(0, 4) != 0) { // 75% chance to add an entry
                val hourId = generateHourId(calendar.timeInMillis)
                val mood = sampleMoods.random()
                addMoodEntry(mood, hourId, calendar.timeInMillis)
            }
            calendar.add(Calendar.HOUR_OF_DAY, 1)
        }
    }
}