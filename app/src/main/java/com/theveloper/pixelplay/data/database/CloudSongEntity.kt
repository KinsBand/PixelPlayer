package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cloud_songs",
    indices = [
        Index(value = ["youtube_id"], unique = false),
        Index(value = ["source_type"], unique = false),
        Index(value = ["date_added"], unique = false),
        Index(value = ["is_downloaded"], unique = false)
    ]
)
data class CloudSongEntity(
    @PrimaryKey
    val id: String,                                        // e.g. "yt_dQw4w9WgXcQ"
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Long,                                     // milliseconds
    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String? = null,                       // remote album art URL
    @ColumnInfo(name = "youtube_id")
    val youtubeId: String? = null,                          // YouTube video ID
    @ColumnInfo(name = "source_type")
    val sourceType: String,                                 // "youtube", "spotify", etc.
    @ColumnInfo(name = "content_uri_string")
    val contentUriString: String,                           // "youtube://dQw4w9WgXcQ"
    @ColumnInfo(name = "date_added")
    val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_downloaded", defaultValue = "0")
    val isDownloaded: Boolean = false,
    @ColumnInfo(name = "local_song_id")
    val localSongId: String? = null,                        // maps to SongEntity.id after download
    @ColumnInfo(name = "local_file_path")
    val localFilePath: String? = null,
    @ColumnInfo(name = "local_content_uri")
    val localContentUri: String? = null
)
