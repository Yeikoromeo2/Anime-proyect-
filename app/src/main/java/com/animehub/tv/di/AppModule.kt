package com.animehub.tv.di

import android.content.Context
import androidx.room.Room
import com.animehub.tv.data.db.AppDatabase
import com.animehub.tv.data.db.FavoriteDao
import com.animehub.tv.data.db.SearchHistoryDao
import com.animehub.tv.data.db.WatchProgressDao
import com.animehub.tv.data.net.Http
import com.animehub.tv.data.net.RetryInterceptor
import com.animehub.tv.data.net.UserAgentInterceptor
import com.animehub.tv.data.net.WebViewCookieJar
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.Cache
import com.animehub.tv.nativeplayer.ExoNativePlaybackFactory
import com.animehub.tv.nativeplayer.NativePlaybackFactory
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .cookieJar(WebViewCookieJar())
            .cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
            .addInterceptor(UserAgentInterceptor { Http.DEFAULT_USER_AGENT })
            .addInterceptor(RetryInterceptor())
            .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "animehub.db").build()

    @Provides
    fun provideFavoriteDao(db: AppDatabase): FavoriteDao = db.favoriteDao()

    @Provides
    fun provideWatchProgressDao(db: AppDatabase): WatchProgressDao = db.watchProgressDao()

    @Provides
    fun provideSearchHistoryDao(db: AppDatabase): SearchHistoryDao = db.searchHistoryDao()

    /** Reproductor nativo (Media3/ExoPlayer) para los videos directos. */
    @Provides
    @Singleton
    fun provideNativePlaybackFactory(client: OkHttpClient): NativePlaybackFactory = ExoNativePlaybackFactory(client)

    /** Voz dentro de la app (sin abrir la pantalla del asistente de la TV). */
    @Provides
    @Singleton
    fun provideVoiceEngine(): com.animehub.tv.voice.VoiceEngine = com.animehub.tv.voice.AndroidVoiceEngine()

    /** "Enviar a mi TV": búsqueda de TVs en la red (tablet), órdenes y anuncio de la TV. */
    @Provides
    @Singleton
    fun provideTvFinder(finder: com.animehub.tv.remote.NsdTvFinder): com.animehub.tv.remote.TvFinder = finder

    @Provides
    @Singleton
    fun provideRemoteClient(client: com.animehub.tv.remote.OkHttpRemoteClient): com.animehub.tv.remote.RemoteClient = client

    @Provides
    @Singleton
    fun provideServiceAdvertiser(advertiser: com.animehub.tv.remote.NsdAdvertiser): com.animehub.tv.remote.ServiceAdvertiser = advertiser

    /** Chromecast y TVs DLNA (Samsung, LG…): búsqueda en la red desde la tablet. */
    @Provides
    @IntoSet
    fun provideCastDiscovery(discovery: com.animehub.tv.cast.CastDiscovery): com.animehub.tv.remote.DeviceDiscovery = discovery

    @Provides
    @IntoSet
    fun provideDlnaDiscovery(discovery: com.animehub.tv.dlna.DlnaDiscovery): com.animehub.tv.remote.DeviceDiscovery = discovery

    /** Reproductor de cada tipo de dispositivo: Chromecast (Google Cast) o DLNA (SOAP). */
    @Provides
    @Singleton
    fun provideRendererFactory(
        @ApplicationContext context: Context,
        cast: com.animehub.tv.cast.CastDiscovery,
    ): com.animehub.tv.remote.RendererFactory = com.animehub.tv.remote.RendererFactory { route ->
        when (route.kind) {
            com.animehub.tv.remote.PlayTarget.KIND_CAST -> com.animehub.tv.cast.CastRenderer(context, route.tvId, cast)
            com.animehub.tv.remote.PlayTarget.KIND_DLNA -> route.controlUrl?.let { com.animehub.tv.dlna.DlnaRenderer(it) }
            else -> null
        }
    }

    /** Intermediario de video en la tablet, con el mismo cliente de la app (User-Agent y cookies) sin caché. */
    @Provides
    @Singleton
    fun provideStreamProxy(client: OkHttpClient): com.animehub.tv.stream.StreamProxy = com.animehub.tv.stream.StreamProxy(
        http = client.newBuilder().cache(null).callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build(),
    )
}
