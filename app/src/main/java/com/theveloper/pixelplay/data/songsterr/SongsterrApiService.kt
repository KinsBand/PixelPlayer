package com.theveloper.pixelplay.data.songsterr

import com.theveloper.pixelplay.data.songsterr.model.SongsterrSong
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SongsterrApiService {
    @GET("api/songs")
    suspend fun searchSongs(
        @Query("pattern") pattern: String
    ): List<SongsterrSong>

    @GET("api/song/{id}")
    suspend fun getSong(
        @Path("id") id: Long
    ): SongsterrSong
}
