package com.animehub.tv.vod

import android.content.Context
import com.animehub.tv.vod.cache.VodCache
import com.animehub.tv.vod.providers.OkHttpVodFetcher
import com.animehub.tv.vod.providers.VodFetcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VodBindingsModule {
    @Binds abstract fun fetcher(impl: OkHttpVodFetcher): VodFetcher
    @Binds abstract fun config(impl: SettingsVodConfigProvider): VodConfigProvider
}

@Module
@InstallIn(SingletonComponent::class)
object VodCacheModule {
    @Provides @Singleton fun cache(@ApplicationContext context: Context) = VodCache(File(context.filesDir, "vod"))
}
