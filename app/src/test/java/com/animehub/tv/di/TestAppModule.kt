package com.animehub.tv.di

import android.content.Context
import androidx.room.Room
import com.animehub.tv.data.db.AppDatabase
import com.animehub.tv.data.db.FavoriteDao
import com.animehub.tv.data.db.SearchHistoryDao
import com.animehub.tv.data.db.WatchProgressDao
import com.animehub.tv.fixtures.Fixtures
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Singleton

/**
 * En las pruebas de UI: base de datos en memoria y red simulada. Las páginas de Anime Ninja
 * se sirven desde los HTML guardados; cualquier otra petición responde 503.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestAppModule {

    fun fixtureFor(url: HttpUrl): String? {
        if (url.host != "veranime.ninja") return null
        val query = url.queryParameter("s")
        return when {
            query == "one piece" -> "animeninja/busqueda_one_piece.html"
            query != null && url.encodedPath.startsWith("/page/2/") -> "animeninja/busqueda_dragon_p2.html"
            query != null -> "animeninja/busqueda_dragon.html"
            url.encodedPath == "/" -> "animeninja/inicio.html"
            url.encodedPath == "/anime/one-piece/" -> "animeninja/anime_one_piece.html"
            url.encodedPath.startsWith("/anime/") -> "animeninja/anime_tank_chair.html"
            else -> null
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val fixture = fixtureFor(chain.request().url)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(if (fixture != null) 200 else 503)
                .message(if (fixture != null) "OK" else "Sin red en las pruebas")
                .body((fixture?.let(Fixtures::read) ?: "").toResponseBody())
                .build()
        }
        .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideWatchProgressDao(db: AppDatabase): WatchProgressDao = db.watchProgressDao()

    @Provides
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()
}
