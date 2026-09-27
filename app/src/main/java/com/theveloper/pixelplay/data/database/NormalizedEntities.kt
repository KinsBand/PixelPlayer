package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["album_id"])]
)
data class TrackEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "date_added") val dateAdded: Long
)

@Entity(
    tableName = "track_sources",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackSourceEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "source_type") val sourceType: Int,
    @ColumnInfo(name = "content_uri") val contentUri: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "parent_directory_path") val parentDirectoryPath: String
)

@Entity(tableName = "album_metadata")
data class AlbumMetadataEntity(
    @PrimaryKey @ColumnInfo(name = "album_id") val albumId: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "album_artist") val albumArtist: String?,
    @ColumnInfo(name = "date_added") val dateAdded: Long
)

@Entity(tableName = "contributors")
data class ContributorEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "image_url") val imageUrl: String?
)

@Entity(
    tableName = "track_credits",
    primaryKeys = ["track_id", "contributor_id", "credit_role"],
    indices = [Index(value = ["contributor_id"])],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ContributorEntity::class,
            parentColumns = ["id"],
            childColumns = ["contributor_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackCreditEntity(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "contributor_id") val contributorId: Long,
    @ColumnInfo(name = "credit_role") val creditRole: String
)

@Entity(tableName = "genres")
data class GenreEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String
)

@Entity(tableName = "moods")
data class MoodEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String
)

@Entity(
    tableName = "track_personalization",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackPersonalizationEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean,
    @ColumnInfo(name = "user_rating") val userRating: Int,
    @ColumnInfo(name = "comments") val comments: String?,
    @ColumnInfo(name = "metadata_provenance_json") val metadataProvenanceJson: String?
)

@Entity(
    tableName = "track_lyrics",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackLyricsEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "content") val content: String?,
    @ColumnInfo(name = "is_synced") val isSynced: Boolean,
    @ColumnInfo(name = "source") val source: String?
)

@Entity(
    tableName = "track_editorial",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackEditorialEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "release_date") val releaseDate: String?,
    @ColumnInfo(name = "record_label") val recordLabel: String?,
    @ColumnInfo(name = "country") val country: String?,
    @ColumnInfo(name = "upc_ean") val upcEan: String? = null,
    @ColumnInfo(name = "catalogue_number") val catalogueNumber: String? = null,
    @ColumnInfo(name = "release_type") val releaseType: String? = null,
    @ColumnInfo(name = "language") val language: String? = null
)

@Entity(
    tableName = "track_technical",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackTechnicalEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "bitrate") val bitrate: Int,
    @ColumnInfo(name = "sample_rate") val sampleRate: Int,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "container_format") val containerFormat: String?,
    @ColumnInfo(name = "bit_depth") val bitDepth: Int? = null,
    @ColumnInfo(name = "channel_count") val channelCount: Int? = null,
    @ColumnInfo(name = "lufs_integrated") val lufsIntegrated: Float? = null,
    @ColumnInfo(name = "dynamic_range") val dynamicRange: Float? = null,
    @ColumnInfo(name = "replay_gain") val replayGain: Float? = null,
    @ColumnInfo(name = "file_size_bytes") val fileSizeBytes: Long? = null,
    @ColumnInfo(name = "sha256_checksum") val sha256Checksum: String? = null
)

@Entity(
    tableName = "track_analysis",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackAnalysisEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "bpm") val bpm: Int?,
    @ColumnInfo(name = "music_key") val musicKey: String?,
    @ColumnInfo(name = "key_camelot") val keyCamelot: String?,
    @ColumnInfo(name = "loudness") val loudness: Float?,
    @ColumnInfo(name = "energy") val energy: Float?,
    @ColumnInfo(name = "valence") val valence: Float?,
    @ColumnInfo(name = "danceability") val danceability: Float? = null,
    @ColumnInfo(name = "acousticness") val acousticness: Float? = null,
    @ColumnInfo(name = "instrumentalness") val instrumentalness: Float? = null,
    @ColumnInfo(name = "speechiness") val speechiness: Float? = null,
    @ColumnInfo(name = "liveness") val liveness: Float? = null,
    @ColumnInfo(name = "beat_grid_json") val beatGridJson: String? = null,
    @ColumnInfo(name = "waveform", typeAffinity = ColumnInfo.BLOB) val waveform: ByteArray?,
    @ColumnInfo(name = "tuning_hz") val tuningHz: Float? = null,
    @ColumnInfo(name = "silence_start_ms") val silenceStartMs: Long? = null,
    @ColumnInfo(name = "silence_end_ms") val silenceEndMs: Long? = null,
    @ColumnInfo(name = "analyzed_at", defaultValue = "0") val analyzedAt: Long = 0,
    @ColumnInfo(name = "analysis_version", defaultValue = "1") val analysisVersion: Int = 1
) {
    // Manual equals/hashCode because of the ByteArray property (data class
    // generated ones would compare the array by reference).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackAnalysisEntity) return false
        return trackId == other.trackId &&
            bpm == other.bpm &&
            musicKey == other.musicKey &&
            keyCamelot == other.keyCamelot &&
            loudness == other.loudness &&
            energy == other.energy &&
            valence == other.valence &&
            danceability == other.danceability &&
            acousticness == other.acousticness &&
            instrumentalness == other.instrumentalness &&
            speechiness == other.speechiness &&
            liveness == other.liveness &&
            beatGridJson == other.beatGridJson &&
            waveform.contentEquals(other.waveform) &&
            tuningHz == other.tuningHz &&
            silenceStartMs == other.silenceStartMs &&
            silenceEndMs == other.silenceEndMs &&
            analyzedAt == other.analyzedAt &&
            analysisVersion == other.analysisVersion
    }

    override fun hashCode(): Int {
        var result = trackId.hashCode()
        result = 31 * result + (bpm ?: 0)
        result = 31 * result + (musicKey?.hashCode() ?: 0)
        result = 31 * result + (keyCamelot?.hashCode() ?: 0)
        result = 31 * result + (loudness?.hashCode() ?: 0)
        result = 31 * result + (energy?.hashCode() ?: 0)
        result = 31 * result + (valence?.hashCode() ?: 0)
        result = 31 * result + (danceability?.hashCode() ?: 0)
        result = 31 * result + (acousticness?.hashCode() ?: 0)
        result = 31 * result + (instrumentalness?.hashCode() ?: 0)
        result = 31 * result + (speechiness?.hashCode() ?: 0)
        result = 31 * result + (liveness?.hashCode() ?: 0)
        result = 31 * result + (beatGridJson?.hashCode() ?: 0)
        result = 31 * result + (waveform?.contentHashCode() ?: 0)
        result = 31 * result + (tuningHz?.hashCode() ?: 0)
        result = 31 * result + (silenceStartMs?.hashCode() ?: 0)
        result = 31 * result + (silenceEndMs?.hashCode() ?: 0)
        result = 31 * result + analyzedAt.hashCode()
        result = 31 * result + analysisVersion
        return result
    }
}


@Entity(
    tableName = "track_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackEmbeddingEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "embedding_blob") val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrackEmbeddingEntity) return false
        return trackId == other.trackId && embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = trackId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

@Entity(
    tableName = "track_genres",
    primaryKeys = ["track_id", "genre_id"],
    indices = [Index(value = ["genre_id"])],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["id"],
            childColumns = ["genre_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackGenreCrossRef(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "genre_id") val genreId: Long
)

@Entity(
    tableName = "track_moods",
    primaryKeys = ["track_id", "mood_id"],
    indices = [Index(value = ["mood_id"])],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["mood_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackMoodCrossRef(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "mood_id") val moodId: Long
)

@Entity(
    tableName = "track_tags",
    primaryKeys = ["track_id", "tag_id"],
    indices = [Index(value = ["tag_id"])],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackTagCrossRef(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long
)

@Entity(
    tableName = "listening_events",
    indices = [Index(value = ["track_id"])],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ListeningEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "duration_played_ms") val durationPlayedMs: Long,
    @ColumnInfo(name = "skip_reason") val skipReason: String?
)

@Entity(
    tableName = "listening_stats",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ListeningStatsEntity(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "play_count") val playCount: Int,
    @ColumnInfo(name = "skip_count") val skipCount: Int,
    @ColumnInfo(name = "last_played_timestamp") val lastPlayedTimestamp: Long
)

@Entity(
    tableName = "artwork",
    indices = [Index(value = ["track_id"])],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ArtworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "album_id") val albumId: Long?,
    @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "source") val source: String
)

@Entity(
    tableName = "external_ids",
    primaryKeys = ["track_id", "provider_name"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ExternalIdEntity(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "provider_name") val providerName: String,
    @ColumnInfo(name = "external_id") val externalId: String
)
