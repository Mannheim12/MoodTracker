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

@Database(entities = [MoodEntry::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun moodEntryDao(): MoodEntryDao

    // DAO interface defined here to avoid creating another file
    @Dao
    interface MoodEntryDao {
        @Query("SELECT * FROM mood_entries ORDER BY id DESC")
        fun getAllEntries(): List<MoodEntry>

        @Query("SELECT * FROM mood_entries WHERE id = :hourId")
        fun getEntryByHourId(hourId: String): MoodEntry?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        fun insertOrUpdateEntry(entry: MoodEntry)

        @Query("SELECT EXISTS(SELECT 1 FROM mood_entries WHERE id = :hourId)")
        fun hasEntryForHour(hourId: String): Boolean
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}