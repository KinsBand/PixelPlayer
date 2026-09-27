package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudSongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(cloudSong: CloudSongEntity)

    /** A metadata refresh must not undo a download that committed while it was loading. */
    @Transaction
    suspend fun upsert(cloudSong: CloudSongEntity) {
        val current = getById(cloudSong.id)
        insertOrReplace(cloudSong.preserveDownloadedSource(current))
    }

    @Transaction
    suspend fun upsertAll(cloudSongs: List<CloudSongEntity>) {
        cloudSongs.forEach { upsert(it) }
    }

    @Query("SELECT * FROM cloud_songs WHERE id = :id")
    suspend fun getById(id: String): CloudSongEntity?

    @Query("SELECT * FROM cloud_songs WHERE (youtube_id = :videoId OR youtube_id = 'yt_' || :videoId) AND is_downloaded = 1")
    suspend fun getDownloadsByVideoId(videoId: String): List<CloudSongEntity>

    @Query("SELECT * FROM cloud_songs WHERE is_downloaded = 1")
    fun observeDownloads(): Flow<List<CloudSongEntity>>

    @Query("SELECT * FROM cloud_songs WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<CloudSongEntity>

    @Query("SELECT * FROM cloud_songs ORDER BY date_added DESC")
    fun getAll(): Flow<List<CloudSongEntity>>

    @Query("SELECT * FROM cloud_songs ORDER BY date_added DESC")
    suspend fun getAllOnce(): List<CloudSongEntity>

    @Query("SELECT id FROM cloud_songs WHERE is_downloaded = 1")
    fun getDownloadedSongIds(): Flow<List<String>>

    @Query("SELECT cs.* FROM cloud_songs cs INNER JOIN favorites f ON cs.id = f.songId WHERE f.isFavorite = 1 ORDER BY f.timestamp DESC")
    fun getFavoritedCloudSongs(): Flow<List<CloudSongEntity>>

    @Query("SELECT cs.* FROM cloud_songs cs INNER JOIN favorites f ON cs.id = f.songId WHERE f.isFavorite = 1 ORDER BY f.timestamp DESC")
    suspend fun getFavoritedCloudSongsOnce(): List<CloudSongEntity>

    @Query("UPDATE cloud_songs SET is_downloaded = :isDownloaded, local_file_path = :localFilePath, local_content_uri = :localContentUri WHERE id = :id")
    suspend fun updateDownloadStatus(
        id: String,
        isDownloaded: Boolean,
        localFilePath: String?,
        localContentUri: String?
    )

    @Query("DELETE FROM cloud_songs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM cloud_songs")
    suspend fun clearAll()
}
