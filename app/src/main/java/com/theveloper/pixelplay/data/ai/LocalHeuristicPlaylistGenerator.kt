package com.theveloper.pixelplay.data.ai

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.DailyMixManager
import javax.inject.Inject
import javax.inject.Singleton
import java.util.Locale

@Singleton
class LocalHeuristicPlaylistGenerator @Inject constructor(
    private val dailyMixManager: DailyMixManager
) {

    suspend fun generate(
        userPrompt: String,
        allSongs: List<Song>,
        limit: Int = 30
    ): List<Song> {
        val query = userPrompt.lowercase(Locale.ROOT)
        
        // Analyze query for heuristics
        val isChill = query.contains("chill") || query.contains("relax") || query.contains("calm") || query.contains("sleep") || query.contains("study")
        val isEnergetic = query.contains("energy") || query.contains("hype") || query.contains("workout") || query.contains("run") || query.contains("fast") || query.contains("gym")
        val isSad = query.contains("sad") || query.contains("melancholy") || query.contains("cry") || query.contains("slow")
        val isHappy = query.contains("happy") || query.contains("joy") || query.contains("cheerful") || query.contains("upbeat")
        val isFavoriteOnly = query.contains("favorite") || query.contains("loved") || query.contains("best") || query.contains("top")
        
        // Decade filters
        val is80s = query.contains("80s") || query.contains("eighties") || query.contains("198")
        val is90s = query.contains("90s") || query.contains("nineties") || query.contains("199")
        
        // Genre filters (extensible)
        val targetGenres = mutableListOf<String>()
        val genres = listOf("rock", "pop", "jazz", "metal", "electronic", "dance", "classical", "rap", "hip hop", "ambient", "indie")
        for (genre in genres) {
            if (query.contains(genre)) {
                targetGenres.add(genre)
            }
        }

        // Rank every song
        val scoredSongs = mutableListOf<Pair<Song, Double>>()
        for (song in allSongs) {
            var score = 0.0

            // Genre match
            val songGenre = song.genre?.lowercase(Locale.ROOT) ?: ""
            for (g in targetGenres) {
                if (songGenre.contains(g)) {
                    score += 50.0
                }
            }

            // Title/Artist matches query directly
            val title = song.title.lowercase(Locale.ROOT)
            val artist = song.displayArtist.lowercase(Locale.ROOT)
            if (query.contains(title) && title.isNotEmpty()) score += 100.0
            if (query.contains(artist) && artist.isNotEmpty()) score += 80.0

            // Telemetry / Mix Score
            val userScore = dailyMixManager.getScore(song.id)
            score += userScore * 0.1 // integrate listening history preference (0 to 10 points)

            // Favorited preference
            if (song.isFavorite) {
                score += 15.0
                if (isFavoriteOnly) score += 50.0
            }

            // Mood score adaptation based on metadata tags or heuristic titles
            if (isChill) {
                // Chill favors lower playback counts (unfamiliar/deep cuts) or acoustic/ambient genres
                if (songGenre.contains("ambient") || songGenre.contains("classical") || songGenre.contains("acoustic") || songGenre.contains("jazz")) {
                    score += 30.0
                }
                // Penalize very high energy or metal
                if (songGenre.contains("metal") || songGenre.contains("rock") || songGenre.contains("electronic")) {
                    score -= 20.0
                }
            }

            if (isEnergetic) {
                if (songGenre.contains("metal") || songGenre.contains("rock") || songGenre.contains("electronic") || songGenre.contains("dance") || songGenre.contains("hip hop")) {
                    score += 40.0
                }
            }

            if (is80s && song.year in 1980..1989) score += 60.0
            if (is90s && song.year in 1990..1999) score += 60.0

            scoredSongs.add(song to score)
        }

        // Return ranked songs
        return scoredSongs
            .filter { it.second > -100.0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
    }
}
