package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

@Dao
interface FavoritesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFavorite(favorite: FavoritesEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<FavoritesEntity>)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: String)

    @Query("DELETE FROM favorites WHERE songId IN (:songIds)")
    suspend fun removeFavorites(songIds: List<String>)

    @Query("SELECT isFavorite FROM favorites WHERE songId = :songId")
    suspend fun isFavorite(songId: String): Boolean?

    @Query("SELECT songId FROM favorites WHERE isFavorite = 1 ORDER BY songId")
    fun getFavoriteSongIdsRaw(): Flow<List<String>>

    fun getFavoriteSongIds(): Flow<List<String>> = getFavoriteSongIdsRaw().distinctUntilChanged()

    @Query("SELECT songId FROM favorites WHERE isFavorite = 1 ORDER BY songId")
    suspend fun getFavoriteSongIdsOnce(): List<String>

    /** Favourite ids, most recently liked first (the row's timestamp is set when it's liked). */
    @Query("SELECT songId FROM favorites WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteSongIdsByRecentRaw(): Flow<List<String>>

    fun getFavoriteSongIdsByRecent(): Flow<List<String>> = getFavoriteSongIdsByRecentRaw().distinctUntilChanged()

    @Query("SELECT songId FROM favorites WHERE isFavorite = 1 AND songId IN (:ids)")
    suspend fun getFavoriteIdsAmong(ids: List<String>): List<String>

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavoritesOnce(): List<FavoritesEntity>

    @Query("DELETE FROM favorites")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(favorites: List<FavoritesEntity>) {
        clearAll()
        if (favorites.isNotEmpty()) insertAll(favorites)
    }
}
