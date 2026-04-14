package com.chibiclaw.di

import com.chibiclaw.ai.GemmaEngineManager
import com.chibiclaw.ai.GemmaInference
import com.chibiclaw.debug.DevLogger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideGemmaInference(engineManager: GemmaEngineManager, devLogger: DevLogger): GemmaInference =
        GemmaInference(engineManager, devLogger)
}
