package com.scribesync.scribesync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Meeting::class, TranscriptEntry::class], version = 3, exportSchema = false)
@TypeConverters(DateConverters::class, TagsConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meetingDao(): MeetingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scribesync_database"
                )
                .fallbackToDestructiveMigration() // Added for development ease
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
