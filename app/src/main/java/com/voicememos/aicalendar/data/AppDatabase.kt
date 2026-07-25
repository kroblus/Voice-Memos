package com.voicememos.aicalendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN itemType TEXT NOT NULL DEFAULT 'EVENT'")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN triggerAtEpochMillis INTEGER")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN dueAtEpochMillis INTEGER")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN location TEXT")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN recurrenceRule TEXT")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN priority TEXT")
        db.execSQL(
            "ALTER TABLE calendar_events ADD COLUMN notificationStatus TEXT NOT NULL DEFAULT 'PENDING'",
        )
        db.execSQL(
            "ALTER TABLE calendar_events ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'LOCAL_ONLY'",
        )
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN externalCalendarEventId TEXT")
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN creationKey TEXT")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_calendar_events_creationKey " +
                "ON calendar_events(creationKey)",
        )
    }
}

@Database(
    entities = [
        CalendarEventEntity::class,
        AppSettingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun appSettingDao(): AppSettingDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "shengli.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { instance = it }
            }
    }
}
