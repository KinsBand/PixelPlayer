package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackMappingDao {
    @Query("SELECT * FROM track_mappings WHERE spotify_id = :spotifyId LIMIT 1")
    suspend fun getMappingBySpotifyId(spotifyId: String): TrackMappingEntity?

    @Query("SELECT * FROM track_mappings WHERE isrc = :isrc LIMIT 1")
    suspend fun getMappingByIsrc(isrc: String): TrackMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: TrackMappingEntity)

    @Query("DELETE FROM track_mappings WHERE spotify_id = :spotifyId")
    suspend fun deleteMapping(spotifyId: String)

    @Query("DELETE FROM track_mappings")
    suspend fun clearAllMappings()
}
