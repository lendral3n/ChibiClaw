package com.chibiclaw.di

import com.chibiclaw.executor.tier4.ShizukuExecutor
import com.chibiclaw.service.ShizukuHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExecutorModule {

    // ExecutionRouter uses @Inject constructor — Hilt constructs it directly.
    // The previous manual provider became a maintenance burden every time a
    // new executor was added, so it was removed. New executors only need to
    // put @Singleton + @Inject on their own class.

    @Provides @Singleton
    fun provideShizukuExecutor(handler: ShizukuHandler): ShizukuExecutor =
        ShizukuExecutor(handler)
}
