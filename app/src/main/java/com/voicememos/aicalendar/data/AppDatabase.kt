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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE calendar_events ADD COLUMN timeZoneId TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS calendar_mirror_tombstones (
                localEventId TEXT NOT NULL,
                itemType TEXT NOT NULL,
                externalCalendarEventId TEXT,
                customAppUri TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(localEventId)
            )
            """.trimIndent(),
        )
    }
}

@Database(
    entities = [
        CalendarEventEntity::class,
        AppSettingEntity::class,
        CalendarMirrorTombstoneEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun calendarMirrorTombstoneDao(): CalendarMirrorTombstoneDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
