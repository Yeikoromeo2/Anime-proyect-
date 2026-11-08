package com.animehub.tv.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favoritos ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favoritos WHERE siteId = :siteId AND animeUrl = :animeUrl)")
    fun observeIsFavorite(siteId: String, animeUrl: String): Flow<Boolean>

    @Query("SELECT * FROM favoritos")
    suspend fun getAll(): List<FavoriteEntity>

    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Upsert
    suspend fun upsertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favoritos WHERE siteId = :siteId AND animeUrl = :animeUrl")
    suspend fun delete(siteId: String, animeUrl: String)
}

@Dao
interface WatchProgressDao {
    /** Último episodio de cada anime, del más reciente al más antiguo. */
    @Query(
        """
        SELECT * FROM progreso p
        WHERE p.updatedAt = (
            SELECT MAX(p2.updatedAt) FROM progreso p2 WHERE p2.siteId = p.siteId AND p2.animeUrl = p.animeUrl
        )
        ORDER BY p.updatedAt DESC
        LIMIT :limit
        """,
    )
    fun observeContinueWatching(limit: Int = 20): Flow<List<WatchProgressEntity>>

    /** Historial completo por fecha. */
    @Query("SELECT * FROM progreso ORDER BY updatedAt DESC")
    fun observeHistory(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM progreso WHERE siteId = :siteId AND animeUrl = :animeUrl")
    fun observeForAnime(siteId: String, animeUrl: String): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM progreso WHERE siteId = :siteId AND episodeUrl = :episodeUrl")
    suspend fun get(siteId: String, episodeUrl: String): WatchProgressEntity?

    @Query("SELECT * FROM progreso")
    suspend fun getAll(): List<WatchProgressEntity>

    @Upsert
    suspend fun upsert(progress: WatchProgressEntity)

    @Upsert
    suspend fun upsertAll(progress: List<WatchProgressEntity>)

    @Query("DELETE FROM progreso WHERE siteId = :siteId AND episodeUrl = :episodeUrl")
    suspend fun delete(siteId: String, episodeUrl: String)

    @Query("DELETE FROM progreso")
    suspend fun clear()
}

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM busquedas ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 15): Flow<List<SearchHistoryEntity>>

    @Upsert
    suspend fun upsert(entry: SearchHistoryEntity)

    @Query("DELETE FROM busquedas WHERE `query` = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM busquedas")
    suspend fun clear()
}
