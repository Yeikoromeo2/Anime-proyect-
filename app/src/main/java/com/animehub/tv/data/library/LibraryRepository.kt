package com.animehub.tv.data.library

import com.animehub.tv.data.db.FavoriteDao
import com.animehub.tv.data.db.FavoriteEntity
import com.animehub.tv.data.db.SearchHistoryDao
import com.animehub.tv.data.db.SearchHistoryEntity
import com.animehub.tv.data.db.WatchProgressDao
import com.animehub.tv.data.db.WatchProgressEntity
import com.animehub.tv.domain.model.WatchProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Favoritos, progreso de reproducción e historial de búsquedas. */
@Singleton
class LibraryRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val progressDao: WatchProgressDao,
    private val searchDao: SearchHistoryDao,
) {
    val favorites: Flow<List<FavoriteEntity>> = favoriteDao.observeAll()

    val continueWatching: Flow<List<WatchProgress>> = progressDao.observeContinueWatching().map { it.map(::toModel) }

    val history: Flow<List<WatchProgress>> = progressDao.observeHistory().map { it.map(::toModel) }

    val recentSearches: Flow<List<String>> = searchDao.observeRecent().map { list -> list.map { it.query } }

    fun isFavorite(siteId: String, animeUrl: String): Flow<Boolean> = favoriteDao.observeIsFavorite(siteId, animeUrl)

    suspend fun setFavorite(siteId: String, animeUrl: String, title: String, coverUrl: String?, favorite: Boolean) {
        if (favorite) favoriteDao.upsert(FavoriteEntity(siteId, animeUrl, title, coverUrl, System.currentTimeMillis()))
        else favoriteDao.delete(siteId, animeUrl)
    }

    fun progressForAnime(siteId: String, animeUrl: String): Flow<List<WatchProgress>> =
        progressDao.observeForAnime(siteId, animeUrl).map { it.map(::toModel) }

    suspend fun saveProgress(progress: WatchProgress) = progressDao.upsert(toEntity(progress))

    suspend fun progressFor(siteId: String, episodeUrl: String): WatchProgress? = progressDao.get(siteId, episodeUrl)?.let(::toModel)

    suspend fun deleteHistoryItem(siteId: String, episodeUrl: String) = progressDao.delete(siteId, episodeUrl)

    suspend fun clearHistory() = progressDao.clear()

    suspend fun addSearch(query: String) {
        val clean = query.trim()
        if (clean.isNotEmpty()) searchDao.upsert(SearchHistoryEntity(clean, System.currentTimeMillis()))
    }

    suspend fun clearSearches() = searchDao.clear()

    private fun toModel(e: WatchProgressEntity) = WatchProgress(
        siteId = e.siteId, animeUrl = e.animeUrl, animeTitle = e.title, coverUrl = e.coverUrl,
        episodeNumber = e.episodeNumber, episodeUrl = e.episodeUrl, serverName = e.serverName,
        positionMs = e.positionMs, durationMs = e.durationMs, updatedAt = e.updatedAt, completed = e.completed,
    )

    private fun toEntity(p: WatchProgress) = WatchProgressEntity(
        siteId = p.siteId, animeUrl = p.animeUrl, title = p.animeTitle, coverUrl = p.coverUrl,
        episodeNumber = p.episodeNumber, episodeUrl = p.episodeUrl, serverName = p.serverName,
        positionMs = p.positionMs, durationMs = p.durationMs, updatedAt = p.updatedAt,
        completed = p.completed || WatchProgress.isCompleted(p.positionMs, p.durationMs),
    )
}
