package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Albums made of songs you liked but only stream (not downloaded, not local files).
 * The table is derived data: [com.theveloper.pixelplay.data.library.StreamCollectionRepository]
 * rewrites it from your likes, so it can always be rebuilt.
 *
 * [mergedInto] is set when the album already exists in the library (same album artist and
 * title). Those rows are not shown as their own card; they only add the "N streaming"
 * count to the library album.
 */
@Entity(
    tableName = "stream_albums",
    indices = [Index(value = ["merged_into"]), Index(value = ["album_key"])]
)
data class StreamAlbumEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "album_key") val albumKey: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "album_artist") val albumArtist: String?,
    @ColumnInfo(name = "album_art_uri_string") val albumArtUriString: String?,
    @ColumnInfo(name = "song_count") val songCount: Int,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "merged_into") val mergedInto: Long?
)

/**
 * Albums you saved with the heart on an album page (usually an online album you don't own).
 * Unlike [StreamAlbumEntity] this is user data, never rebuilt: StreamCollectionRepository adds
 * every saved album to `stream_albums` on each rebuild, so it shows in Library → Albums.
 * [id] is [com.theveloper.pixelplay.data.library.CollectionKeys.streamAlbumId] of [albumKey].
 */
@Entity(tableName = "saved_albums")
data class SavedAlbumEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "album_key") val albumKey: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "album_art_uri_string") val albumArtUriString: String?,
    @ColumnInfo(name = "year") val year: Int,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "saved_at") val savedAt: Long
)

/** Artists of streamed liked songs. Same idea as [StreamAlbumEntity]. */
@Entity(
    tableName = "stream_artists",
    indices = [Index(value = ["merged_into"]), Index(value = ["name"])]
)
data class StreamArtistEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "merged_into") val mergedInto: Long?
)

data class StreamMergedCount(
    @ColumnInfo(name = "target_id") val targetId: Long,
    @ColumnInfo(name = "stream_count") val streamCount: Int
)

@Dao
interface StreamCollectionDao {
    @Query("SELECT * FROM stream_albums")
    suspend fun getAlbumsOnce(): List<StreamAlbumEntity>

    @Query("SELECT * FROM stream_artists")
    suspend fun getArtistsOnce(): List<StreamArtistEntity>

    @Query("SELECT * FROM stream_albums WHERE id = :id LIMIT 1")
    suspend fun getAlbumById(id: Long): StreamAlbumEntity?

    @Query("SELECT * FROM stream_artists WHERE id = :id LIMIT 1")
    suspend fun getArtistById(id: Long): StreamArtistEntity?

    @Query("SELECT merged_into AS target_id, SUM(song_count) AS stream_count FROM stream_albums WHERE merged_into IS NOT NULL GROUP BY merged_into")
    fun observeMergedAlbumCounts(): Flow<List<StreamMergedCount>>

    @Query("SELECT merged_into AS target_id, SUM(track_count) AS stream_count FROM stream_artists WHERE merged_into IS NOT NULL GROUP BY merged_into")
    fun observeMergedArtistCounts(): Flow<List<StreamMergedCount>>

    @Query("""
        SELECT * FROM stream_albums
        WHERE merged_into IS NULL AND song_count >= :minTracks
        AND (title LIKE '%' || :query || '%' OR artist_name LIKE '%' || :query || '%')
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun searchAlbums(query: String, minTracks: Int): Flow<List<StreamAlbumEntity>>

    @Query("""
        SELECT * FROM stream_artists
        WHERE merged_into IS NULL AND name LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun searchArtists(query: String): Flow<List<StreamArtistEntity>>

    @Query("SELECT * FROM saved_albums ORDER BY saved_at DESC")
    fun observeSavedAlbums(): Flow<List<SavedAlbumEntity>>

    @Query("SELECT * FROM saved_albums")
    suspend fun getSavedAlbumsOnce(): List<SavedAlbumEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_albums WHERE id = :id)")
    fun observeIsSaved(id: Long): Flow<Boolean>

    @Query("SELECT * FROM saved_albums WHERE id = :id LIMIT 1")
    suspend fun getSavedAlbumById(id: Long): SavedAlbumEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedAlbum(album: SavedAlbumEntity)

    @Query("DELETE FROM saved_albums WHERE id = :id")
    suspend fun deleteSavedAlbum(id: Long)

    @Query("UPDATE stream_artists SET image_url = :imageUrl WHERE id = :id")
    suspend fun updateArtistImage(id: Long, imageUrl: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<StreamAlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<StreamArtistEntity>)

    @Query("DELETE FROM stream_albums WHERE id IN (:ids)")
    suspend fun deleteAlbums(ids: List<Long>)

    @Query("DELETE FROM stream_artists WHERE id IN (:ids)")
    suspend fun deleteArtists(ids: List<Long>)

    /** Applies a diff in one transaction, so the Library tabs refresh once. */
    @Transaction
    suspend fun applyDiff(
        upsertAlbums: List<StreamAlbumEntity>,
        removeAlbumIds: List<Long>,
        upsertArtists: List<StreamArtistEntity>,
        removeArtistIds: List<Long>
    ) {
        removeAlbumIds.chunked(500).forEach { deleteAlbums(it) }
        removeArtistIds.chunked(500).forEach { deleteArtists(it) }
        if (upsertAlbums.isNotEmpty()) upsertAlbums.chunked(300).forEach { insertAlbums(it) }
        if (upsertArtists.isNotEmpty()) upsertArtists.chunked(300).forEach { insertArtists(it) }
    }
}
