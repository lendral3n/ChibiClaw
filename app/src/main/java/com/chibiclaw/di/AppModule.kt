package com.chibiclaw.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Top-level Hilt module for application-scoped bindings.
 *
 * Historically this module manually constructed [com.chibiclaw.memory.MemoryManager],
 * but that class — and all the other singleton services we once listed
 * here — now use @Inject constructors, so Hilt provisions them itself
 * from the DAO providers exposed by [DatabaseModule] and the
 * @ApplicationContext binding.
 *
 * The module stays around as the canonical @InstallIn SingletonComponent
 * anchor so future @Provides methods have an obvious home.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
