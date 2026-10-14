package com.theveloper.pixelplay.data.ai.nano

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NanoModule {
    @Binds
    @Singleton
    // Gemma 4 when picked and downloaded, Gemini Nano otherwise.
    abstract fun bindNanoEngine(impl: RoutingNanoEngine): NanoEngine
}
