package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_mappings")
data class TrackMappingEntity(
    @PrimaryKey
    @ColumnInfo(name = "spotify_id")
    val spotifyId: String,

    @ColumnInfo(name = "yt_video_id")
    val ytVideoId: String,

    @ColumnInfo(name = "isrc")
    val isrc: String? = null,

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long = System.currentTimeMillis()
)
