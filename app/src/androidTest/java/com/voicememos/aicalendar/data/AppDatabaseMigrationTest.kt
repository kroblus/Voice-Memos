package com.voicememos.aicalendar.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate1To2PreservesRowsAndAddsTypedDefaultsAndUniqueCreationKey() {
        migrationHelper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO calendar_events (
                    id, title, notes, startAtEpochMillis, endAtEpochMillis, allDay,
                    reminderMinutesCsv, status, sourceTranscript, aiConfidence,
                    createdAtEpochMillis, updatedAtEpochMillis
                ) VALUES (
                    'legacy-1', '旧日程', NULL, 1784966400000, NULL, 0,
                    '0,10', 'SCHEDULED', '明天开会', 0.9,
                    1784880000000, 1784880000000
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            MIGRATION_1_2,
        )
        try {
            migrated.query(
                """
                SELECT title, itemType, notificationStatus, syncStatus,
                       triggerAtEpochMillis, dueAtEpochMillis, creationKey
                FROM calendar_events
                WHERE id = 'legacy-1'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧日程", cursor.getString(0))
                assertEquals("EVENT", cursor.getString(1))
                assertEquals("PENDING", cursor.getString(2))
                assertEquals("LOCAL_ONLY", cursor.getString(3))
                assertNull(cursor.getString(4))
                assertNull(cursor.getString(5))
                assertNull(cursor.getString(6))
            }

            insertWithCreationKey(migrated, id = "first", creationKey = "session-1:item-1")
            insertWithCreationKey(migrated, id = "second", creationKey = "session-1:item-1")
            migrated.query(
                "SELECT COUNT(*) FROM calendar_events WHERE creationKey = 'session-1:item-1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrate2To3AddsTimeZoneAndDurableMirrorTombstones() {
        migrationHelper.createDatabase(TEST_DATABASE_V2, 2).apply {
            execSQL(
                """
                INSERT INTO calendar_events (
                    id, title, notes, startAtEpochMillis, endAtEpochMillis, allDay,
                    reminderMinutesCsv, status, sourceTranscript, aiConfidence,
                    createdAtEpochMillis, updatedAtEpochMillis, itemType,
                    notificationStatus, syncStatus, externalCalendarEventId
                ) VALUES (
                    'v2-event', '系统日历迁移', NULL, 1784966400000, NULL, 0,
                    '0,10', 'SCHEDULED', NULL, 1.0,
                    1784880000000, 1784880000000, 'EVENT',
                    'SCHEDULED', 'LOCAL_ONLY', NULL
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE_V2,
            3,
            true,
            MIGRATION_2_3,
        )
        try {
            migrated.query(
                "SELECT timeZoneId FROM calendar_events WHERE id = 'v2-event'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertNull(cursor.getString(0))
            }
            migrated.execSQL(
                """
                INSERT INTO calendar_mirror_tombstones (
                    localEventId, itemType, externalCalendarEventId,
                    customAppUri, createdAtEpochMillis
                ) VALUES (
                    'v2-event', 'EVENT', '42',
                    'shengli://item/event/v2-event', 1784880000000
                )
                """.trimIndent(),
            )
            migrated.query("SELECT COUNT(*) FROM calendar_mirror_tombstones").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        } finally {
            migrated.close()
        }
    }

    private fun insertWithCreationKey(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        id: String,
        creationKey: String,
    ) {
        database.execSQL(
            """
            INSERT OR IGNORE INTO calendar_events (
                id, title, notes, startAtEpochMillis, endAtEpochMillis, allDay,
                reminderMinutesCsv, status, sourceTranscript, aiConfidence,
                createdAtEpochMillis, updatedAtEpochMillis, creationKey
            ) VALUES (
                '$id', '幂等测试', NULL, 1784966400000, NULL, 0,
                '0', 'SCHEDULED', NULL, 1.0,
                1784880000000, 1784880000000, '$creationKey'
            )
            """.trimIndent(),
        )
    }

    private companion object {
        const val TEST_DATABASE = "migration-1-2-test"
        const val TEST_DATABASE_V2 = "migration-2-3-test"
    }
}
