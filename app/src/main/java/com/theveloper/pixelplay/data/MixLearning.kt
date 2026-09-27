package com.theveloper.pixelplay.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import timber.log.Timber
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Entity(tableName = "attempts", indices = [Index("songId"), Index("startedAt")])
data class MixAttempt(
    @PrimaryKey val id: String,
    val songId: String,
    val mixId: String?,
    val startedAt: Long,
    val activeMs: Long,
    val uniqueMs: Long,
    val repeatedMs: Long,
    val durationMs: Long,
    val voluntary: Boolean,
    val endReason: String,
    val seeks: Int,
    val schemaVersion: Int = 2,
    val decisionId: String? = null,
    val sessionId: String? = null,
    val recordingId: String? = null
) {
    val coverage: Double get() = if (durationMs > 0) (uniqueMs.toDouble() / durationMs).coerceIn(0.0, 1.0) else 0.0
}

@Entity(tableName = "recommendations", indices = [Index("songId"), Index("plannedAt")])
data class MixRecommendation(
    @PrimaryKey val id: String,
    val songId: String,
    val recordingId: String,
    val sessionId: String,
    val mixId: String,
    val plannedAt: Long,
    val source: String,
    val modelVersion: String,
    val queueRevision: Long,
    val selectionProbability: Double,
    val explored: Boolean,
    val scoreComponents: String
)

@Dao
interface MixAttemptDao {
    @Upsert suspend fun save(attempt: MixAttempt)
    @Query("SELECT * FROM attempts ORDER BY startedAt DESC LIMIT 2000")
    suspend fun recent(): List<MixAttempt>
    @Query("DELETE FROM attempts WHERE id NOT IN (SELECT id FROM attempts ORDER BY startedAt DESC LIMIT 2000)")
    suspend fun compact()
    @Query("DELETE FROM attempts") suspend fun clear()
    @Upsert suspend fun saveRecommendation(decision: MixRecommendation)
    @Query("SELECT * FROM recommendations ORDER BY plannedAt DESC LIMIT 2000")
    suspend fun recommendations(): List<MixRecommendation>
    @Query("DELETE FROM recommendations WHERE id NOT IN (SELECT id FROM recommendations ORDER BY plannedAt DESC LIMIT 2000)")
    suspend fun compactRecommendations()
    @Query("DELETE FROM recommendations") suspend fun clearRecommendations()
}

/**
 * One piece of mix feedback the user gave: an exclusion, a snooze or a queue removal.
 * [aliases] holds every name the recording goes by (newline separated) so a stream and a
 * library copy of the same song both match. [expiresAt] = null means until undone.
 */
@Entity(tableName = "feedback", indices = [Index("createdAt")])
data class MixFeedbackEntry(
    @PrimaryKey val id: String,
    /** [MixFeedback.Kind] name. */
    val kind: String,
    /** "global", or a legacy "mix:normal" / "mix:smart" scope. */
    val scope: String,
    val aliases: String,
    val songId: String,
    val title: String,
    val artist: String,
    val genre: String?,
    val createdAt: Long,
    val expiresAt: Long?
)

@Dao
interface MixFeedbackDao {
    @Query("SELECT * FROM feedback ORDER BY createdAt DESC")
    suspend fun all(): List<MixFeedbackEntry>
    @Upsert suspend fun save(entry: MixFeedbackEntry)
    @Upsert suspend fun saveAll(entries: List<MixFeedbackEntry>)
    @Query("DELETE FROM feedback WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM feedback WHERE expiresAt IS NOT NULL AND expiresAt < :now") suspend fun deleteExpired(now: Long)
    @Query("DELETE FROM feedback") suspend fun clear()
}

// Independent, versioned store so recommendation history does not risk the music catalogue.
@Database(entities = [MixAttempt::class, MixRecommendation::class, MixFeedbackEntry::class], version = 3, exportSchema = true)
abstract class MixLearningDatabase : RoomDatabase() {
    abstract fun attempts(): MixAttemptDao
    abstract fun feedback(): MixFeedbackDao
    companion object {
        /** v3: mix feedback moves from SharedPreferences into its own table (see [MixFeedback]). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS feedback (id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, scope TEXT NOT NULL, aliases TEXT NOT NULL, songId TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, genre TEXT, createdAt INTEGER NOT NULL, expiresAt INTEGER)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_feedback_createdAt ON feedback(createdAt)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempts ADD COLUMN decisionId TEXT")
                db.execSQL("ALTER TABLE attempts ADD COLUMN sessionId TEXT")
                db.execSQL("ALTER TABLE attempts ADD COLUMN recordingId TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS recommendations (id TEXT NOT NULL PRIMARY KEY, songId TEXT NOT NULL, recordingId TEXT NOT NULL, sessionId TEXT NOT NULL, mixId TEXT NOT NULL, plannedAt INTEGER NOT NULL, source TEXT NOT NULL, modelVersion TEXT NOT NULL, queueRevision INTEGER NOT NULL, selectionProbability REAL NOT NULL, explored INTEGER NOT NULL, scoreComponents TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recommendations_songId ON recommendations(songId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recommendations_plannedAt ON recommendations(plannedAt)")
            }
        }
    }
}

@Singleton
class MixLearning @Inject constructor(@ApplicationContext private val context: Context) {
    private val database by lazy {
        Room.databaseBuilder(context, MixLearningDatabase::class.java, "mix_learning.db").addMigrations(MixLearningDatabase.MIGRATION_1_2, MixLearningDatabase.MIGRATION_2_3).build()
    }
    private val storageMutex = Mutex()
    @Volatile private var historyCutoff = 0L
    private val writes = Channel<MixAttempt>(64)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init {
        scope.launch {
            for (attempt in writes) {
                try { save(attempt) } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { Timber.w(error, "Mix history write failed") }
            }
        }
    }
    /**
     * Finished plays (a final end reason, not a checkpoint), as they happen. The live mix
     * listens to this so a skip reshapes the upcoming songs straight away instead of waiting
     * for the attempt to reach the database and the next refill.
     */
    private val _finished = kotlinx.coroutines.flow.MutableSharedFlow<MixAttempt>(extraBufferCapacity = 16,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val finished: kotlinx.coroutines.flow.SharedFlow<MixAttempt> = _finished

    fun record(attempt: MixAttempt) {
        if (attempt.startedAt < historyCutoff) return
        if (attempt.endReason != MixEndReason.UNKNOWN.name) _finished.tryEmit(attempt)
        if (!writes.trySend(attempt).isSuccess) Timber.w("Mix analytics buffer full; playback continues")
    }
    @Volatile var sessionId: String = java.util.UUID.randomUUID().toString()
    @Volatile var mixId: String? = null
    suspend fun save(attempt: MixAttempt) = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            if (attempt.startedAt >= historyCutoff) {
                database.attempts().save(attempt)
                database.attempts().compact()
            }
        }
    }
    suspend fun recent(): List<MixAttempt> = withContext(Dispatchers.IO) { database.attempts().recent() }
    /** Mix feedback table (exclusions, snoozes, removals); owned by [MixFeedback]. */
    internal fun feedbackDao(): MixFeedbackDao = database.feedback()
    suspend fun recordRecommendation(decision: MixRecommendation) = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            if (decision.plannedAt >= historyCutoff) {
                database.attempts().saveRecommendation(decision)
                database.attempts().compactRecommendations()
            }
        }
    }
    suspend fun recommendations(): List<MixRecommendation> = withContext(Dispatchers.IO) { database.attempts().recommendations() }
    suspend fun reset() = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            historyCutoff = System.currentTimeMillis()
            database.attempts().clear()
            database.attempts().clearRecommendations()
        }
    }
}
