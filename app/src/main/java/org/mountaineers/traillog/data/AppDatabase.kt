package org.mountaineers.traillog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TrailReport::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(
    DateConverter::class,           // ← Add this
    ReportTypeConverter::class      // if you already have one for ReportType
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trailReportDao(): TrailReportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "traillog_database"
                )
                    .fallbackToDestructiveMigration()   // Forces recreation on schema change
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}