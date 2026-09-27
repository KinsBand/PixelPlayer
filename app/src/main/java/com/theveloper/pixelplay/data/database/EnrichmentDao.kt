package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Aggregated counts describing how much of the library has been enriched.
 * Returned by [EnrichmentDao.getEnrichmentStats].
 */
data class EnrichmentStats(
    @ColumnInfo(name = "total_songs") val totalSongs: Int,
    @ColumnInfo(name = "analyzed_count") val analyzedCount: Int,
    @ColumnInfo(name = "with_bpm_count") val withBpmCount: Int,
    @ColumnInfo(name = "with_key_count") val withKeyCount: Int,
    @ColumnInfo(name = "editorial_count") val editorialCount: Int,
    @ColumnInfo(name = "api_artwork_count") val apiArtworkCount: Int
)

/**
 * Snapshot of every enrichment artifact stored for a single track.
 * Assembled by [EnrichmentDao.getFullEnrichment].
 */
data class TrackEnrichment(
    val analysis: TrackAnalysisEntity?,
    val editorial: TrackEditorialEntity?,
    val externalIds: List<ExternalIdEntity>,
    val tags: List<TagEntity>,
    val genres: List<GenreEntity>,
    val moods: List<MoodEntity>,
    val artwork: List<ArtworkEntity>
)

/**
 * DAO for the normalized metadata-enrichment tables (tracks, track_analysis,
 * track_editorial, artwork, external_ids, genres/moods/tags + cross refs).
 *
 * Artwork `source` strings written by later phases: 'embedded',
 * 'directory', 'cover_art_archive'. The stats query treats anything other
 * than 'embedded' as API-sourced artwork.
 */
/** Light projection of [TrackAnalysisEntity] for mixes (see [EnrichmentDao.getMixFeatures]). */
data class TrackMixFeatures(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "bpm") val bpm: Int?,
    @ColumnInfo(name = "music_key") val musicKey: String?,
    @ColumnInfo(name = "key_camelot") val keyCamelot: String?,
    @ColumnInfo(name = "energy") val energy: Float?,
    @ColumnInfo(name = "valence") val valence: Float?,
    @ColumnInfo(name = "danceability") val danceability: Float?,
    @ColumnInfo(name = "acousticness") val acousticness: Float?,
    @ColumnInfo(name = "instrumentalness") val instrumentalness: Float?
)

@Dao
interface EnrichmentDao {

    // ---------------------------------------------------------------------
    // Upserts
    // ---------------------------------------------------------------------

    @Upsert
    suspend fun upsertTrack(track: TrackEntity)

    @Upsert
    suspend fun upsertTrackSource(trackSource: TrackSourceEntity)

    @Upsert
    suspend fun upsertAnalysis(analysis: TrackAnalysisEntity)

    @Upsert
    suspend fun upsertEditorial(editorial: TrackEditorialEntity)

    @Upsert
    suspend fun upsertArtwork(artwork: ArtworkEntity)

    @Upsert
    suspend fun upsertExternalId(externalId: ExternalIdEntity)

    @Upsert
    suspend fun upsertPersonalization(personalization: TrackPersonalizationEntity)

    @Upsert
    suspend fun upsertEmbedding(embedding: TrackEmbeddingEntity)

    @Upsert
    suspend fun upsertTechnical(technical: TrackTechnicalEntity)


    @Query("SELECT * FROM track_technical WHERE track_id = :trackId")
    suspend fun getTechnical(trackId: Long): TrackTechnicalEntity?

    @Query("SELECT * FROM track_personalization WHERE track_id = :trackId")
    suspend fun getPersonalization(trackId: Long): TrackPersonalizationEntity?

    @Query("SELECT * FROM track_embeddings WHERE track_id = :trackId")
    suspend fun getEmbedding(trackId: Long): TrackEmbeddingEntity?

    @Query("SELECT * FROM track_embeddings")
    suspend fun getAllEmbeddings(): List<TrackEmbeddingEntity>

    /**
     * The analysed features the mix engine sequences on, for every analysed track, without the
     * waveform / beat-grid blobs (so the whole library loads in one light query).
     */
    @Query("SELECT track_id, bpm, music_key, key_camelot, energy, valence, danceability, acousticness, instrumentalness FROM track_analysis")
    suspend fun getMixFeatures(): List<TrackMixFeatures>


    /**
     * Makes sure a default track_personalization row exists for the song
     * (same default shape as MIGRATION_42_43's backfill: favorite from
     * songs, rating 0, no comments, no provenance).
     */
    @Query("""
        INSERT OR IGNORE INTO track_personalization (track_id, is_favorite, user_rating, comments, metadata_provenance_json)
        SELECT id, is_favorite, 0, NULL, NULL FROM songs WHERE id = :songId
    """)
    suspend fun ensurePersonalizationRow(songId: Long)

    // ---------------------------------------------------------------------
    // Track row bootstrapping
    // ---------------------------------------------------------------------

    @Query("""
        INSERT OR IGNORE INTO tracks (id, title, album_id, duration_ms, date_added)
        SELECT id, title, album_id, duration, date_added FROM songs WHERE id = :songId
    """)
    suspend fun ensureTrackRow(songId: Long)

    @Query("""
        INSERT OR IGNORE INTO track_sources (track_id, source_type, content_uri, file_path, parent_directory_path)
        SELECT id, source_type, content_uri_string, file_path, parent_directory_path FROM songs WHERE id = :songId
    """)
    suspend fun ensureTrackSourceRow(songId: Long)

    /**
     * Makes sure the normalized `tracks` / `track_sources` rows exist for a
     * song before enrichment rows referencing them are written (FK parents).
     */
    @Transaction
    suspend fun ensureTrackRows(songId: Long) {
        ensureTrackRow(songId)
        ensureTrackSourceRow(songId)
    }

    // ---------------------------------------------------------------------
    // Genres / moods / tags: find-or-create + linking
    //
    // Id generation scheme: these entities use a non-auto Long PK and names
    // are the natural keys here, so ids are assigned deterministically as
    // `name.trim().lowercase().hashCode().toLong()`. Re-runs therefore
    // converge on the same row without a lookup. The (very unlikely) 32-bit
    // hash collision between two different names is detected after an
    // ignored insert and falls back to MAX(id) + 1.
    // ---------------------------------------------------------------------

    @Query("SELECT * FROM genres WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findGenreByName(name: String): GenreEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenre(genre: GenreEntity): Long

    @Query("SELECT MAX(id) FROM genres")
    suspend fun getMaxGenreId(): Long?

    @Query("SELECT * FROM moods WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findMoodByName(name: String): MoodEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMood(mood: MoodEntity): Long

    @Query("SELECT MAX(id) FROM moods")
    suspend fun getMaxMoodId(): Long?

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findTagByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity): Long

    @Query("SELECT MAX(id) FROM tags")
    suspend fun getMaxTagId(): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackGenreCrossRef(crossRef: TrackGenreCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackMoodCrossRef(crossRef: TrackMoodCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackTagCrossRef(crossRef: TrackTagCrossRef)

    @Transaction
    suspend fun findOrCreateGenreId(name: String): Long? {
        val normalized = name.trim()
        if (normalized.isEmpty()) return null
        findGenreByName(normalized)?.let { return it.id }
        val hashId = stableNameId(normalized)
        if (insertGenre(GenreEntity(id = hashId, name = normalized)) != -1L) return hashId
        // Insert ignored: concurrent insert of the same name, or hash collision.
        findGenreByName(normalized)?.let { return it.id }
        val fallbackId = (getMaxGenreId() ?: 0L) + 1L
        insertGenre(GenreEntity(id = fallbackId, name = normalized))
        return fallbackId
    }

    @Transaction
    suspend fun findOrCreateMoodId(name: String): Long? {
        val normalized = name.trim()
        if (normalized.isEmpty()) return null
        findMoodByName(normalized)?.let { return it.id }
        val hashId = stableNameId(normalized)
        if (insertMood(MoodEntity(id = hashId, name = normalized)) != -1L) return hashId
        findMoodByName(normalized)?.let { return it.id }
        val fallbackId = (getMaxMoodId() ?: 0L) + 1L
        insertMood(MoodEntity(id = fallbackId, name = normalized))
        return fallbackId
    }

    @Transaction
    suspend fun findOrCreateTagId(name: String): Long? {
        val normalized = name.trim()
        if (normalized.isEmpty()) return null
        findTagByName(normalized)?.let { return it.id }
        val hashId = stableNameId(normalized)
        if (insertTag(TagEntity(id = hashId, name = normalized)) != -1L) return hashId
        findTagByName(normalized)?.let { return it.id }
        val fallbackId = (getMaxTagId() ?: 0L) + 1L
        insertTag(TagEntity(id = fallbackId, name = normalized))
        return fallbackId
    }

    /** Finds-or-creates the genre row and links it to the track. */
    @Transaction
    suspend fun linkGenreToTrack(trackId: Long, name: String) {
        val genreId = findOrCreateGenreId(name) ?: return
        insertTrackGenreCrossRef(TrackGenreCrossRef(trackId = trackId, genreId = genreId))
    }

    /** Finds-or-creates the mood row and links it to the track. */
    @Transaction
    suspend fun linkMoodToTrack(trackId: Long, name: String) {
        val moodId = findOrCreateMoodId(name) ?: return
        insertTrackMoodCrossRef(TrackMoodCrossRef(trackId = trackId, moodId = moodId))
    }

    /** Finds-or-creates the tag row and links it to the track. */
    @Transaction
    suspend fun linkTagToTrack(trackId: Long, name: String) {
        val tagId = findOrCreateTagId(name) ?: return
        insertTrackTagCrossRef(TrackTagCrossRef(trackId = trackId, tagId = tagId))
    }

    // ---------------------------------------------------------------------
    // Reads
    // ---------------------------------------------------------------------

    @Query("SELECT * FROM track_analysis WHERE track_id = :trackId")
    fun getAnalysisFlow(trackId: Long): Flow<TrackAnalysisEntity?>

    @Query("SELECT * FROM track_analysis WHERE track_id = :trackId")
    suspend fun getAnalysis(trackId: Long): TrackAnalysisEntity?

    @Query("SELECT * FROM track_editorial WHERE track_id = :trackId")
    suspend fun getEditorial(trackId: Long): TrackEditorialEntity?

    @Query("SELECT * FROM artwork WHERE track_id = :trackId")
    suspend fun getArtworkForTrack(trackId: Long): List<ArtworkEntity>

    @Query("SELECT * FROM external_ids WHERE track_id = :trackId")
    suspend fun getExternalIds(trackId: Long): List<ExternalIdEntity>

    @Query("""
        SELECT t.* FROM tags t
        INNER JOIN track_tags tt ON tt.tag_id = t.id
        WHERE tt.track_id = :trackId
    """)
    suspend fun getTagsForTrack(trackId: Long): List<TagEntity>

    @Query("""
        SELECT g.* FROM genres g
        INNER JOIN track_genres tg ON tg.genre_id = g.id
        WHERE tg.track_id = :trackId
    """)
    suspend fun getGenresForTrack(trackId: Long): List<GenreEntity>

    @Query("""
        SELECT m.* FROM moods m
        INNER JOIN track_moods tm ON tm.mood_id = m.id
        WHERE tm.track_id = :trackId
    """)
    suspend fun getMoodsForTrack(trackId: Long): List<MoodEntity>

    // ---------------------------------------------------------------------
    // Coverage / progress queries
    // ---------------------------------------------------------------------

    @Query("""
        SELECT s.id FROM songs s
        LEFT JOIN track_analysis ta ON ta.track_id = s.id
        WHERE ta.track_id IS NULL
    """)
    suspend fun getTrackIdsMissingAnalysis(): List<Long>

    @Query("SELECT track_id FROM track_analysis")
    suspend fun getTrackIdsWithAnalysis(): List<Long>

    @Query("""
        SELECT s.id FROM songs s
        LEFT JOIN track_editorial te ON te.track_id = s.id
        WHERE te.track_id IS NULL
    """)
    suspend fun getTrackIdsMissingEditorial(): List<Long>

    @Query("""
        SELECT
            (SELECT COUNT(*) FROM songs) AS total_songs,
            (SELECT COUNT(*) FROM track_analysis) AS analyzed_count,
            (SELECT COUNT(*) FROM track_analysis WHERE bpm IS NOT NULL) AS with_bpm_count,
            (SELECT COUNT(*) FROM track_analysis WHERE music_key IS NOT NULL OR key_camelot IS NOT NULL) AS with_key_count,
            (SELECT COUNT(*) FROM track_editorial) AS editorial_count,
            (SELECT COUNT(*) FROM artwork WHERE source != 'embedded') AS api_artwork_count
    """)
    fun getEnrichmentStats(): Flow<EnrichmentStats>

    // ---------------------------------------------------------------------
    // Deletes (re-analysis support)
    // ---------------------------------------------------------------------

    @Query("DELETE FROM track_analysis WHERE track_id = :trackId")
    suspend fun deleteAnalysis(trackId: Long)

    @Query("DELETE FROM track_analysis")
    suspend fun clearAllAnalysis()

    // ---------------------------------------------------------------------
    // Full per-track snapshot
    // ---------------------------------------------------------------------

    /**
     * Hand-rolled aggregate (clearer than @Relation across these manual
     * tables): combines the individual suspend getters in one transaction.
     */
    @Transaction
    suspend fun getFullEnrichment(trackId: Long): TrackEnrichment {
        return TrackEnrichment(
            analysis = getAnalysis(trackId),
            editorial = getEditorial(trackId),
            externalIds = getExternalIds(trackId),
            tags = getTagsForTrack(trackId),
            genres = getGenresForTrack(trackId),
            moods = getMoodsForTrack(trackId),
            artwork = getArtworkForTrack(trackId)
        )
    }

    companion object {
        /**
         * Deterministic id for name-keyed rows (genres / moods / tags).
         * See the scheme documentation above [findGenreByName].
         */
        fun stableNameId(name: String): Long = name.trim().lowercase().hashCode().toLong()
    }
}
