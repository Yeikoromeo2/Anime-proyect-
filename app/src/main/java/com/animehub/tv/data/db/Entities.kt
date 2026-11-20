package com.animehub.tv.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "favoritos", primaryKeys = ["siteId", "animeUrl"])
data class FavoriteEntity(
    val siteId: String,
    val animeUrl: String,
    val title: String,
    val coverUrl: String?,
    val addedAt: Long,
)

/** Progreso por episodio. "Continuar viendo" toma el último de cada anime. */
@Entity(
    tableName = "progreso",
    primaryKeys = ["siteId", "episodeUrl"],
    indices = [Index(value = ["siteId", "animeUrl"]), Index(value = ["updatedAt"])],
)
data class WatchProgressEntity(
    val siteId: String,
    val animeUrl: String,
    val title: String,
    val coverUrl: String?,
    val episodeNumber: Float,
    val episodeUrl: String,
    val serverName: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    /** Visto (≥ 90 %). */
    val completed: Boolean,
)

@Entity(tableName = "busquedas", primaryKeys = ["query"])
data class SearchHistoryEntity(
    val query: String,
    val searchedAt: Long,
)
