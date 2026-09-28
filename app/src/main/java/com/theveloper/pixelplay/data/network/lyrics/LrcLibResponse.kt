package com.theveloper.pixelplay.data.network.lyrics

import com.google.gson.annotations.SerializedName

/**
 * Representa la respuesta de la API de LRCLIB.
 * Contiene la letra de la canción, tanto en formato simple como sincronizado.
 */
data class LrcLibResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String,
    @SerializedName("artistName") val artistName: String,
    @SerializedName("albumName") val albumName: String,
    @SerializedName("duration") val duration: Double,
    @SerializedName("plainLyrics") val plainLyrics: String?,
    @SerializedName("syncedLyrics") val syncedLyrics: String?,
    /**
     * The record as a Lyricsfile (YAML). Every LRCLIB record has one, and it is the only field
     * that can carry word timing; [syncedLyrics] is its line-level LRC.
     */
    @SerializedName("lyricsfile") val lyricsfile: String? = null,
    @SerializedName("instrumental") val instrumental: Boolean? = null
)