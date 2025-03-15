package com.example.moodtracker.util

import android.os.Environment
import com.example.moodtracker.model.Constants
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVParser
import java.io.BufferedReader

/**
 * Handles reading and writing mood data to/from CSV
 */
class DataManager {

    private val hourIdFormat = SimpleDateFormat("yyyyMMddHH", Locale.US)
    private val fullDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    // CSV header columns
    private val csvHeaders = arrayOf("id", "timestamp", "mood")

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
     * Get the CSV data file
     */
    fun getDataFile(): File {
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }
        return File(documentsDir, Constants.DATA_FILE_NAME)
    }

    /**
     * Add a mood entry to the CSV
     * @param moodName The mood to record
     * @param hourId Optional hour ID. If not provided, uses current hour.
     * @param timestamp Optional timestamp. If not provided, uses current time.
     */
    fun addMoodEntry(moodName: String, hourId: String? = null, timestamp: Long = System.currentTimeMillis()) {
        // Use provided hour ID or generate from current time
        val id = hourId ?: generateHourId(timestamp)

        // Safety check: Only write to our specific CSV file
        val dataFile = getDataFile()
        if (!isValidMoodTrackerFile(dataFile)) {
            return
        }

        try {
            if (!dataFile.exists()) {
                // Create new file with headers
                createNewCsvFile(dataFile, id, timestamp, moodName)
                return
            }

            // File exists, update or append entry
            updateOrAppendMoodEntry(dataFile, id, timestamp, moodName)

        } catch (e: Exception) {
            // Handle error - in production app, would log this
        }
    }

    /**
     * Create a new CSV file with headers and first entry
     */
    private fun createNewCsvFile(file: File, hourId: String, timestamp: Long, moodName: String) {
        try {
            FileWriter(file).use { writer ->
                val csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader(*csvHeaders)
                    .build()
                val csvPrinter = CSVPrinter(writer, csvFormat)
                csvPrinter.printRecord(hourId, fullDateFormat.format(Date(timestamp)), moodName)
                csvPrinter.flush()
            }
        } catch (e: Exception) {
            // Handle error
        }
    }

    /**
     * Update an existing entry or append a new one
     */
    private fun updateOrAppendMoodEntry(file: File, hourId: String, timestamp: Long, moodName: String) {
        try {
            // Safety check
            if (!isValidMoodTrackerFile(file)) {
                return
            }

            val lines = mutableListOf<String>()
            var foundHeader = false
            var updatedExisting = false

            // Read existing file
            BufferedReader(FileReader(file)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue

                    if (!foundHeader) {
                        // Keep header line
                        lines.add(currentLine)
                        foundHeader = true
                        continue
                    }

                    // Check if line starts with our hour ID
                    if (currentLine.startsWith(hourId) || currentLine.startsWith("\"$hourId\"")) {
                        // Replace this line
                        lines.add("$hourId,${fullDateFormat.format(Date(timestamp))},$moodName")
                        updatedExisting = true
                    } else {
                        // Keep existing line
                        lines.add(currentLine)
                    }
                }
            }

            // If no existing entry was updated, append new entry
            if (!updatedExisting) {
                lines.add("$hourId,${fullDateFormat.format(Date(timestamp))},$moodName")
            }

            // Write back to file
            FileWriter(file).use { writer ->
                writer.write(lines.joinToString("\n"))
            }

        } catch (e: Exception) {
            // Handle error
        }
    }

    /**
     * Check if a mood entry exists for the specified hour
     * @param hourId The hour ID to check
     * @return true if an entry exists, false otherwise
     */
    fun hasEntryForHour(hourId: String): Boolean {
        val dataFile = getDataFile()

        if (!dataFile.exists()) {
            return false
        }

        try {
            // Simple check for the hour ID in the file
            BufferedReader(FileReader(dataFile)).use { reader ->
                var line: String?
                // Skip header
                reader.readLine()

                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line ?: continue
                    if (currentLine.startsWith(hourId) || currentLine.startsWith("\"$hourId\"")) {
                        return true
                    }
                }
            }
            return false

        } catch (e: Exception) {
            // If there's an error reading the file, assume no entry exists
            return false
        }
    }

    /**
     * Safety check to ensure we only write to our specific CSV file
     */
    private fun isValidMoodTrackerFile(file: File): Boolean {
        // Check file name
        if (file.name != Constants.DATA_FILE_NAME) {
            return false
        }

        // Check that it's in the Documents directory
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        if (!file.absolutePath.startsWith(documentsDir.absolutePath)) {
            return false
        }

        // If file exists, check that it has the expected header format
        if (file.exists() && file.length() > 0) {
            try {
                BufferedReader(FileReader(file)).use { reader ->
                    val firstLine = reader.readLine() ?: return true // Empty file is valid

                    // Very basic check for our headers
                    return firstLine.contains("id") &&
                            firstLine.contains("timestamp") &&
                            firstLine.contains("mood")
                }
            } catch (e: Exception) {
                // If can't read, assume it's valid and let the write operation handle errors
                return true
            }
        }

        return true
    }


}