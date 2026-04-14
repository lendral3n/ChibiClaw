package com.chibiclaw.di

import android.content.Context
import androidx.room.Room
import com.chibiclaw.memory.local.ChibiDatabase
import com.chibiclaw.memory.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideChibiDatabase(@ApplicationContext context: Context): ChibiDatabase =
        Room.databaseBuilder(context, ChibiDatabase::class.java, "chibi.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun provideCommandHistoryDao(db: ChibiDatabase): CommandHistoryDao = db.commandHistoryDao()
    @Provides fun provideConversationDao(db: ChibiDatabase): ConversationDao = db.conversationDao()
    @Provides fun provideAppPatternDao(db: ChibiDatabase): AppPatternDao = db.appPatternDao()
    @Provides fun provideContactContextDao(db: ChibiDatabase): ContactContextDao = db.contactContextDao()
    @Provides fun provideWhitelistDao(db: ChibiDatabase): WhitelistDao = db.whitelistDao()
    @Provides fun provideCronTaskDao(db: ChibiDatabase): CronTaskDao = db.cronTaskDao()
    @Provides fun provideMemoryEmbeddingDao(db: ChibiDatabase): MemoryEmbeddingDao = db.memoryEmbeddingDao()
}
