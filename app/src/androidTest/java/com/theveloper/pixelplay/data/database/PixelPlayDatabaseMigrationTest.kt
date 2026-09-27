package com.theveloper.pixelplay.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PixelPlayDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PixelPlayDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (version in 25..41) {
            context.deleteDatabase(databaseNameFor(version))
        }
        context.deleteDatabase(DB_NAME_33_TO_34)
        context.deleteDatabase(DB_NAME_23_TO_24_DRIFTED)
        context.deleteDatabase(DB_NAME_35_TO_36)
        context.deleteDatabase(DB_NAME_39_TO_40)
    }

    @Test
    fun migrateEveryExportedSchemaToLatest() {
        for (startVersion in 25..41) {
            helper.createDatabase(databaseNameFor(startVersion), startVersion).close()

            helper.runMigrationsAndValidate(
                databaseNameFor(startVersion),
                PixelPlayDatabaseVersion.LATEST,
                true,
                *ALL_MIGRATIONS
            ).close()
        }
    }

    @Test
    fun migration33To34AddsArtistsJsonColumnToSongs() {
        helper.createDatabase(DB_NAME_33_TO_34, 33).close()

        helper.runMigrationsAndValidate(
            DB_NAME_33_TO_34,
            34,
            true,
            PixelPlayDatabase.MIGRATION_33_34
        ).let { db ->
            val cursor = db.query("PRAGMA table_info(`songs`)")
            try {
                val nameIndex = cursor.getColumnIndex("name")
                val defaultValueIndex = cursor.getColumnIndex("dflt_value")
                var foundArtistsJson = false
                var defaultValue: String? = null

                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == "artists_json") {
                        foundArtistsJson = true
                        defaultValue = cursor.getString(defaultValueIndex)
                        break
                    }
                }

                assertTrue(foundArtistsJson)
                assertEquals("NULL", defaultValue)
            } finally {
                cursor.close()
                db.close()
            }
        }
    }

    @Test
    fun migration23To24RepairsSongsWithoutDateAddedBeforeCreatingIndexes() {
        val openHelper = createDriftedVersion23Database(DB_NAME_23_TO_24_DRIFTED)
        val db = openHelper.writableDatabase

        try {
            PixelPlayDatabase.MIGRATION_23_24.migrate(db)

            val columns = db.tableColumns("songs")
            assertTrue("date_added" in columns)

            db.query("SELECT date_added FROM songs WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }

            db.query(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_songs_date_added'"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("index_songs_date_added", cursor.getString(0))
            }
        } finally {
            db.close()
            openHelper.close()
        }
    }

    @Test
    fun migration35To36AddsSongsFtsTable() {
        helper.createDatabase(DB_NAME_35_TO_36, 35).close()

        helper.runMigrationsAndValidate(
            DB_NAME_35_TO_36,
            36,
            true,
            PixelPlayDatabase.MIGRATION_35_36
        ).let { db ->
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'songs_fts'")
            try {
                assertTrue(cursor.moveToFirst())
                assertEquals("songs_fts", cursor.getString(0))
            } finally {
                cursor.close()
                db.close()
            }
        }
    }

    @Test
    fun migration39To40AddsCompositeSongIndexes() {
        helper.createDatabase(DB_NAME_39_TO_40, 39).close()

        helper.runMigrationsAndValidate(
            DB_NAME_39_TO_40,
            40,
            true,
            PixelPlayDatabase.MIGRATION_39_40
        ).let { db ->
            try {
                val indexes = db.tableIndexes("songs")
                assertTrue("index_songs_parent_directory_path_source_type_album_id" in indexes)
                assertTrue("index_songs_parent_directory_path_source_type_id" in indexes)
            } finally {
                db.close()
            }
        }
    }

    @org.junit.Test
    fun migration42To43MigratesNormalizedMetadata() {
        // Create database in version 42
        var db = helper.createDatabase("migration-test-42-to-43", 42)
        
        // Insert sample data in old tables
        db.execSQL("INSERT INTO albums (id, title, artist_name, artist_id, album_art_uri_string, song_count, date_added, year) VALUES (1, 'Test Album', 'Test Artist', 10, 'uri_art', 1, 1000, 2026)")
        db.execSQL("INSERT INTO artists (id, name, track_count) VALUES (10, 'Test Artist', 1)")
        db.execSQL("INSERT INTO songs (id, title, artist_name, artist_id, album_name, album_id, content_uri_string, file_path, parent_directory_path, duration, date_added, mime_type, bitrate, sample_rate, size, date_modified, source_type, is_favorite, lyrics) VALUES (100, 'Test Track', 'Test Artist', 10, 'Test Album', 1, 'content://test', '/path/test.mp3', '/path', 180000, 2000, 'audio/mpeg', 320, 44100, 5000000, 3000, 0, 1, 'Test lyrics content')")
        db.execSQL("INSERT INTO song_engagements (song_id, play_count, skip_count, last_played_timestamp) VALUES (100, 5, 2, 4000)")
        
        db.close()
        
        // Migrate to version 43
        db = helper.runMigrationsAndValidate("migration-test-42-to-43", 43, true, PixelPlayDatabase.MIGRATION_42_43)
        
        // Verify tracks migration
        val cursorTracks = db.query("SELECT * FROM tracks WHERE id = 100")
        assertTrue(cursorTracks.moveToFirst())
        assertTrue(cursorTracks.getString(cursorTracks.getColumnIndexOrThrow("title")) == "Test Track")
        assertTrue(cursorTracks.getLong(cursorTracks.getColumnIndexOrThrow("album_id")) == 1L)
        assertTrue(cursorTracks.getLong(cursorTracks.getColumnIndexOrThrow("duration_ms")) == 180000L)
        cursorTracks.close()
        
        // Verify track_sources migration
        val cursorSources = db.query("SELECT * FROM track_sources WHERE track_id = 100")
        assertTrue(cursorSources.moveToFirst())
        assertTrue(cursorSources.getString(cursorSources.getColumnIndexOrThrow("file_path")) == "/path/test.mp3")
        cursorSources.close()
        
        // Verify track_technical migration
        val cursorTech = db.query("SELECT * FROM track_technical WHERE track_id = 100")
        assertTrue(cursorTech.moveToFirst())
        assertTrue(cursorTech.getInt(cursorTech.getColumnIndexOrThrow("bitrate")) == 320)
        cursorTech.close()
        
        // Verify track_personalization migration
        val cursorPers = db.query("SELECT * FROM track_personalization WHERE track_id = 100")
        assertTrue(cursorPers.moveToFirst())
        assertTrue(cursorPers.getInt(cursorPers.getColumnIndexOrThrow("is_favorite")) == 1)
        cursorPers.close()
        
        // Verify track_lyrics migration
        val cursorLyrics = db.query("SELECT * FROM track_lyrics WHERE track_id = 100")
        assertTrue(cursorLyrics.moveToFirst())
        assertTrue(cursorLyrics.getString(cursorLyrics.getColumnIndexOrThrow("content")) == "Test lyrics content")
        cursorLyrics.close()
        
        // Verify album_metadata migration
        val cursorAlbum = db.query("SELECT * FROM album_metadata WHERE album_id = 1")
        assertTrue(cursorAlbum.moveToFirst())
        assertTrue(cursorAlbum.getString(cursorAlbum.getColumnIndexOrThrow("title")) == "Test Album")
        cursorAlbum.close()
        
        // Verify listening_stats migration
        val cursorStats = db.query("SELECT * FROM listening_stats WHERE track_id = 100")
        assertTrue(cursorStats.moveToFirst())
        assertTrue(cursorStats.getInt(cursorStats.getColumnIndexOrThrow("play_count")) == 5)
        cursorStats.close()
        
        db.close()
    }

    @Test
    fun migration50To51CreatesGDriveTablesWhenMissing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "migration-test-50-to-51-no-gdrive"
        context.deleteDatabase(databaseName)

        val callback = object : SupportSQLiteOpenHelper.Callback(50) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS songs (id INTEGER NOT NULL PRIMARY KEY, title TEXT NOT NULL, source_type INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE TABLE IF NOT EXISTS telegram_songs (id TEXT NOT NULL PRIMARY KEY)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
        val db = openHelper.writableDatabase

        try {
            PixelPlayDatabase.MIGRATION_50_51.migrate(db)

            val gdriveSongColumns = db.tableColumns("gdrive_songs")
            val requiredColumns = setOf(
                "id", "drive_file_id", "folder_id", "title", "artist", "album",
                "album_id", "duration", "album_art_url", "mime_type", "bitrate",
                "file_size", "date_added", "date_modified"
            )
            assertTrue(requiredColumns.all(gdriveSongColumns::contains))

            val gdriveFolderColumns = db.tableColumns("gdrive_folders")
            assertTrue(setOf("id", "name", "song_count", "last_sync_time").all(gdriveFolderColumns::contains))

            val songIndexes = db.tableIndexes("gdrive_songs")
            assertTrue("index_gdrive_songs_drive_file_id" in songIndexes)
            assertTrue("index_gdrive_songs_folder_id" in songIndexes)
            assertTrue("index_gdrive_songs_folder_id_date_added" in songIndexes)
        } finally {
            db.close()
            openHelper.close()
        }
    }

    private fun databaseNameFor(startVersion: Int): String = "migration-test-$startVersion"

    private fun createDriftedVersion23Database(
        databaseName: String
    ): SupportSQLiteOpenHelper {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)

        val callback = object : SupportSQLiteOpenHelper.Callback(23) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS songs (
                            id INTEGER NOT NULL PRIMARY KEY,
                            title TEXT NOT NULL,
                            artist_name TEXT NOT NULL,
                            artist_id INTEGER NOT NULL,
                            album_artist TEXT,
                            album_name TEXT NOT NULL,
                            album_id INTEGER NOT NULL,
                            content_uri_string TEXT NOT NULL,
                            album_art_uri_string TEXT,
                            duration INTEGER NOT NULL,
                            genre TEXT,
                            file_path TEXT NOT NULL,
                            parent_directory_path TEXT NOT NULL,
                            is_favorite INTEGER NOT NULL DEFAULT 0,
                            lyrics TEXT DEFAULT null,
                            track_number INTEGER NOT NULL DEFAULT 0,
                            year INTEGER NOT NULL DEFAULT 0,
                            mime_type TEXT,
                            bitrate INTEGER,
                            sample_rate INTEGER,
                            telegram_chat_id INTEGER,
                            telegram_file_id INTEGER
                        )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                        INSERT INTO songs (
                            id,
                            title,
                            artist_name,
                            artist_id,
                            album_artist,
                            album_name,
                            album_id,
                            content_uri_string,
                            album_art_uri_string,
                            duration,
                            genre,
                            file_path,
                            parent_directory_path,
                            is_favorite,
                            lyrics,
                            track_number,
                            year,
                            mime_type,
                            bitrate,
                            sample_rate,
                            telegram_chat_id,
                            telegram_file_id
                        ) VALUES (
                            1,
                            'Song',
                            'Artist',
                            10,
                            NULL,
                            'Album',
                            20,
                            'content://song/1',
                            NULL,
                            180000,
                            NULL,
                            '/music/song.mp3',
                            '/music',
                            0,
                            NULL,
                            1,
                            2024,
                            'audio/mpeg',
                            320000,
                            44100,
                            NULL,
                            NULL
                        )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS favorites (
                            songId INTEGER NOT NULL PRIMARY KEY,
                            isFavorite INTEGER NOT NULL,
                            timestamp INTEGER NOT NULL
                        )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS song_engagements (
                            song_id TEXT NOT NULL PRIMARY KEY,
                            play_count INTEGER NOT NULL DEFAULT 0,
                            total_play_duration_ms INTEGER NOT NULL DEFAULT 0,
                            last_played_timestamp INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(
                db: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = Unit
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(callback)
                .build()
        )
    }

    private fun SupportSQLiteDatabase.tableColumns(tableName: String): Set<String> {
        val columns = mutableSetOf<String>()
        query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        return columns
    }

    private fun SupportSQLiteDatabase.tableIndexes(tableName: String): Set<String> {
        val indexes = mutableSetOf<String>()
        query("PRAGMA index_list(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                indexes += cursor.getString(nameIndex)
            }
        }
        return indexes
    }

    private object PixelPlayDatabaseVersion {
        const val LATEST = 43
    }

    companion object {
        private const val DB_NAME_23_TO_24_DRIFTED = "migration-test-23-to-24-drifted"
        private const val DB_NAME_33_TO_34 = "migration-test-33-to-34"
        private const val DB_NAME_35_TO_36 = "migration-test-35-to-36"
        private const val DB_NAME_39_TO_40 = "migration-test-39-to-40"

        private val ALL_MIGRATIONS = arrayOf(
            PixelPlayDatabase.MIGRATION_25_26,
            PixelPlayDatabase.MIGRATION_26_27,
            PixelPlayDatabase.MIGRATION_27_28,
            PixelPlayDatabase.MIGRATION_28_29,
            PixelPlayDatabase.MIGRATION_29_30,
            PixelPlayDatabase.MIGRATION_30_31,
            PixelPlayDatabase.MIGRATION_31_32,
            PixelPlayDatabase.MIGRATION_32_33,
            PixelPlayDatabase.MIGRATION_33_34,
            PixelPlayDatabase.MIGRATION_34_35,
            PixelPlayDatabase.MIGRATION_35_36,
            PixelPlayDatabase.MIGRATION_36_37,
            PixelPlayDatabase.MIGRATION_37_38,
            PixelPlayDatabase.MIGRATION_38_39,
            PixelPlayDatabase.MIGRATION_39_40,
            PixelPlayDatabase.MIGRATION_40_41,
            PixelPlayDatabase.MIGRATION_41_42,
            PixelPlayDatabase.MIGRATION_42_43
        )
    }
}
