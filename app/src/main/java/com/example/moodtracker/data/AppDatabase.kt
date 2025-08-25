package com.example.moodtracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.moodtracker.model.MoodEntry

@Database(entities = [MoodEntry::class], version = 1) // Add exportSchema = false if not providing schemas
abstract class AppDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao

    @Dao
    interface MoodEntryDao {
        @Query("SELECT * FROM mood_entries ORDER BY id DESC")
        fun getAllEntries(): List<MoodEntry>

        @Query("SELECT * FROM mood_entries ORDER BY timestamp ASC LIMIT 1")
        fun getOldestEntry(): MoodEntry?

        @Query("SELECT * FROM mood_entries WHERE id = :hourId")
        fun getEntryByHourId(hourId: String): MoodEntry?

        @Query("SELECT * FROM mood_entries WHERE id IN (:hourIds)")
        fun getEntriesByHourIds(hourIds: List<String>): List<MoodEntry>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun insertOrUpdateEntry(entry: MoodEntry)

        @Query("SELECT EXISTS(SELECT 1 FROM mood_entries WHERE id = :hourId)")
        fun hasEntryForHour(hourId: String): Boolean

        @Query("DELETE FROM mood_entries") // New method
        suspend fun deleteAllEntries()

        @Query("SELECT * FROM mood_entries WHERE timestamp >= :sinceTimestamp ORDER BY id ASC")
        suspend fun getEntriesSince(sinceTimestamp: Long): List<MoodEntry>
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mood_tracker_database"
                )
                    // If you're not providing migration paths for schema changes,
                    // you might need .fallbackToDestructiveMigration() during development.
                    // For production, proper migrations are essential.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}