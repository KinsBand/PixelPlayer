package com.theveloper.pixelplay.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MixLearningMigrationTest {
    private val name = "micro-skip-migration-test"
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),
        MixLearningDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @After fun cleanup() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
    }

    @Test fun upgradesV3WithoutTurningHistoricalSkipsIntoMicroSkips() {
        helper.createDatabase(name, 3).apply {
            execSQL("INSERT INTO attempts (id,songId,startedAt,activeMs,uniqueMs,repeatedMs,durationMs,voluntary,endReason,seeks,schemaVersion) VALUES ('old','track',1000,5000,5000,0,180000,0,'SKIP',0,2)")
            close()
        }
        helper.runMigrationsAndValidate(name, 4, true, MixLearningDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT startPositionMs,endPositionMs,songId FROM attempts WHERE id='old'").use {
                assertTrue(it.moveToFirst())
                assertEquals(-1L, it.getLong(0))
                assertEquals(-1L, it.getLong(1))
                assertEquals("track", it.getString(2))
            }
            db.query("SELECT COUNT(*) FROM micro_skip_cooldowns").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }

    /** v5 adds the reactions table and leaves existing history untouched. */
    @Test fun upgradesV4AddsEmptyReactionsTable() {
        helper.createDatabase(name, 4).apply {
            execSQL("INSERT INTO attempts (id,songId,startedAt,activeMs,uniqueMs,repeatedMs,durationMs,voluntary,endReason,seeks,schemaVersion) VALUES ('kept','track',1000,5000,5000,0,180000,0,'COMPLETED',0,2)")
            close()
        }
        helper.runMigrationsAndValidate(name, 5, true, MixLearningDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT COUNT(*) FROM attempts WHERE id='kept'").use {
                assertTrue(it.moveToFirst())
                assertEquals(1, it.getInt(0))
            }
            db.execSQL("INSERT INTO reactions (id,songId,recordingId,sessionId,mixId,reaction,axis,polarity,strength,positionMs,durationMs,title,artist,createdAt) VALUES ('r','track',NULL,'s',NULL,'LOVE','SONG_PREFERENCE',1,1.0,30000,180000,'T','A',2000)")
            db.query("SELECT reaction FROM reactions WHERE songId='track'").use {
                assertTrue(it.moveToFirst())
                assertEquals("LOVE", it.getString(0))
            }
        }
    }
}
