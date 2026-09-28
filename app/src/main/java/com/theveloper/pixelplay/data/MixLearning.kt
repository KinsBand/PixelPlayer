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
    val recordingId: String? = null,
    val artist: String? = null,
    val genre: String? = null,
    @ColumnInfo(defaultValue = "-1") val startPositionMs: Long = -1L,
    @ColumnInfo(defaultValue = "-1") val endPositionMs: Long = -1L,
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
    @Query("SELECT * FROM attempts WHERE endReason != 'UNKNOWN' AND id != :id ORDER BY startedAt DESC LIMIT 1")
    suspend fun previousFinished(id: String): MixAttempt?
    @Query("SELECT * FROM attempts WHERE id = :id") suspend fun byId(id: String): MixAttempt?
    @Upsert suspend fun saveCooldowns(entries: List<MicroSkipCooldown>)
    @Query("SELECT * FROM micro_skip_cooldowns WHERE expiresAt > :now") suspend fun cooldowns(now: Long): List<MicroSkipCooldown>
    @Query("DELETE FROM micro_skip_cooldowns WHERE expiresAt <= :now") suspend fun expireCooldowns(now: Long)
    @Query("DELETE FROM micro_skip_cooldowns") suspend fun clearCooldowns()
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
@Database(entities = [MixAttempt::class, MixRecommendation::class, MixFeedbackEntry::class, MicroSkipCooldown::class], version = 4, exportSchema = true)
abstract class MixLearningDatabase : RoomDatabase() {
    abstract fun attempts(): MixAttemptDao
    abstract fun feedback(): MixFeedbackDao
    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attempts ADD COLUMN artist TEXT")
                db.execSQL("ALTER TABLE attempts ADD COLUMN genre TEXT")
                db.execSQL("ALTER TABLE attempts ADD COLUMN startPositionMs INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE attempts ADD COLUMN endPositionMs INTEGER NOT NULL DEFAULT -1")
                db.execSQL("CREATE TABLE IF NOT EXISTS micro_skip_cooldowns (vector TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, expiresAt INTEGER NOT NULL)")
            }
        }
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
        Room.databaseBuilder(context, MixLearningDatabase::class.java, "mix_learning.db").addMigrations(MixLearningDatabase.MIGRATION_1_2, MixLearningDatabase.MIGRATION_2_3, MixLearningDatabase.MIGRATION_3_4).build()
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
     * Finished plays after their attempt and cooldown transaction commits. The live mix
     * can immediately replan using the same persisted evidence as the next refill.
     */
    private val _finished = kotlinx.coroutines.flow.MutableSharedFlow<MixAttempt>(extraBufferCapacity = 16,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val finished: kotlinx.coroutines.flow.SharedFlow<MixAttempt> = _finished

    fun record(attempt: MixAttempt) {
        if (attempt.startedAt < historyCutoff) return
        if (!writes.trySend(attempt).isSuccess) Timber.w("Mix analytics buffer full; playback continues")
    }
    @Volatile var sessionId: String = java.util.UUID.randomUUID().toString()
    @Volatile var mixId: String? = null
    suspend fun save(attempt: MixAttempt) = withContext(Dispatchers.IO) {
        storageMutex.withLock {
            if (attempt.startedAt >= historyCutoff) {
                var finished = false
                database.withTransaction {
                    val dao = database.attempts()
                    val existing = dao.byId(attempt.id)
                    // Late checkpoints must not overwrite a final outcome or trigger it twice.
                    if (existing != null && existing.endReason != MixEndReason.UNKNOWN.name) return@withTransaction
                    finished = attempt.endReason != MixEndReason.UNKNOWN.name
                    if (finished) {
                        dao.saveCooldowns(MicroSkipPolicy.triggered(dao.previousFinished(attempt.id), attempt))
                        dao.expireCooldowns(System.currentTimeMillis())
                    }
                    dao.save(attempt)
                    dao.compact()
                }
                // Publish only after persistence, so the immediate replan sees the new cooldown.
                if (finished) _finished.emit(attempt)
            }
        }
    }
    suspend fun recent(): List<MixAttempt> = withContext(Dispatchers.IO) { database.attempts().recent() }
    suspend fun microSkipCooldowns(): List<MicroSkipCooldown> = withContext(Dispatchers.IO) {
        database.attempts().cooldowns(System.currentTimeMillis())
    }
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
            database.attempts().clearCooldowns()
        }
    }
}
