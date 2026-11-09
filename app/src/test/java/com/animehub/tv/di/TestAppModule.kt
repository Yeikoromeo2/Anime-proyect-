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
 * En las pruebas de UI: base de datos en memoria y red simulada. Las páginas de Anime Ninja y
 * Latanime se sirven desde los HTML guardados (la búsqueda de Latanime devuelve su desafío de
 * Cloudflare, como en la realidad); cualquier otra petición responde 503.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class])
object TestAppModule {

    /** Página guardada y código HTTP para cada URL de los sitios (null = sin red). */
    fun fixtureFor(url: HttpUrl): Pair<String, Int>? = when (url.host) {
        "veranime.ninja" -> ninja(url)?.let { it to 200 }
        "latanime.org" -> latanime(url)
        "jkanime.org" -> jkanime(url)?.let { it to 200 }
        "animeav1.com" -> "animeninja/embed_animeav1_tank_chair_6.html" to 200
        "darkanimes.com" -> "animeninja/embed_darkanimes_one_piece_1.html" to 200
        else -> videoHost(url.host)
    }

    /** Servidores de video simulados: HLS y MP4Upload funcionan, Voe "borrado", Byse caído. */
    private fun videoHost(host: String): Pair<String, Int>? = when (host) {
        "player.zilla-networks.com", "www.mp4upload.com" -> "TEXTO:<html><video src='v.mp4'></video></html>" to 200
        "voe.sx" -> "TEXTO:<h1>File Not Found</h1>" to 200
        "byselapuix.com" -> "TEXTO:" to 503
        else -> null
    }

    private fun ninja(url: HttpUrl): String? {
        val query = url.queryParameter("s")
        val path = url.encodedPath
        return when {
            path == "/wp-admin/admin-ajax.php" -> "animeninja/player_ajax_tank_chair_6.json"
            path.startsWith("/ver/") -> "animeninja/episodio_tank_chair_6.html"
            query == "one piece" -> "animeninja/busqueda_one_piece.html"
            query != null && path.startsWith("/page/2/") -> "animeninja/busqueda_dragon_p2.html"
            query != null -> "animeninja/busqueda_dragon.html"
            path == "/" -> "animeninja/inicio.html"
            path == "/anime/" -> "animeninja/directorio_anime.html"
            path.startsWith("/genero/") -> "animeninja/genero_accion.html"
            path.startsWith("/pelicula/") && path.count { it == '/' } <= 2 -> "animeninja/directorio_peliculas.html"
            path == "/anime/one-piece/" -> "animeninja/anime_one_piece.html"
            // Fichas de dos "Recomendados" (para que su reseña sea la real).
            path == "/anime/jigokuraku/" -> "animeninja/anime_jigokuraku.html"
            path == "/anime/oshi-no-ko/" -> "animeninja/anime_oshi-no-ko.html"
            path.startsWith("/anime/") -> "animeninja/anime_tank_chair.html"
            else -> null
        }
    }

    private fun latanime(url: HttpUrl): Pair<String, Int>? {
        val path = url.encodedPath
        val file = when {
            path == "/" -> "latanime/inicio.html"
            path == "/buscar" -> return "cloudflare/latanime_buscar_interactive.html" to 403
            path == "/animes" && url.queryParameter("fecha") == "2024" && url.queryParameter("genero") == "accion" ->
                if (url.queryParameter("p") == "2") "latanime/directorio_2024_accion_p2.html" else "latanime/directorio_2024_accion.html"
            path == "/animes" && url.queryParameter("categoria") == "ova" -> "latanime/directorio_ova.html"
            path == "/animes" -> "latanime/directorio.html"
            path == "/emision" -> "latanime/emision.html"
            path.startsWith("/anime/") -> "latanime/anime_finalizado.html"
            path.startsWith("/ver/") -> "latanime/episodio.html"
            else -> null
        }
        return file?.let { it to 200 }
    }

    private fun jkanime(url: HttpUrl): String? {
        val path = url.encodedPath
        return when {
            path == "/" -> "jkanime/inicio.html"
            path.startsWith("/buscar/one") -> "jkanime/busqueda_one_piece.html"
            path.startsWith("/buscar/") -> "jkanime/busqueda_dragon.html"
            path == "/directorio" && url.queryParameter("p") == "2" -> "jkanime/directorio_accion_2025_p2.html"
            path == "/directorio" && url.queryParameter("genero") != null -> "jkanime/directorio_accion_2025.html"
            path == "/directorio" -> "jkanime/directorio.html"
            path == "/one-piece/" -> "jkanime/anime_one_piece.html"
            path == "/moyashimon/" -> "jkanime/anime_moyashimon.html"
            Regex("""^/[^/]+/\d+/$""").matches(path) -> "jkanime/episodio_tank_chair_6.html"
            Regex("""^/[^/]+/$""").matches(path) -> "jkanime/anime_tank_chair.html"
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
                .code(fixture?.second ?: 503)
                .message(if (fixture != null) "OK" else "Sin red en las pruebas")
                .apply { if (fixture?.second == 403) header("cf-mitigated", "challenge") }
                .body((fixture?.first?.let { if (it.startsWith("TEXTO:")) it.removePrefix("TEXTO:") else Fixtures.read(it) } ?: "").toResponseBody())
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
