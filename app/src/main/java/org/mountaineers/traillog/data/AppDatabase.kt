package org.mountaineers.traillog.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TrailReport::class],
    // Bump whenever TrailReport / converters change. Without a bump, Room throws
    // IllegalStateException (identity hash mismatch) even with destructive migration.
    version = 5, // Room schema version (bump on entity/converter changes)
    exportSchema = false
)
@TypeConverters(
    DateConverter::class,
    ReportTypeConverter::class
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
                    // Dev-friendly: wipe local DB on version change (no migrations yet).
                    // Local data is re-pulled from Firestore after login.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}