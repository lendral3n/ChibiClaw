package com.chibiclaw.di

import android.content.Context
import com.chibiclaw.data.database.AgentStepDao
import com.chibiclaw.data.database.AppDatabase
import com.chibiclaw.data.database.AuditDao
import com.chibiclaw.data.database.MemoryDao
import com.chibiclaw.data.database.ModelConfigDao
import com.chibiclaw.data.database.StandingInstructionDao
import com.chibiclaw.data.database.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-wide DI bindings: database, DAOs, top-level singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        passphrase: ByteArray,
    ): AppDatabase = AppDatabase.create(context, passphrase)

    @Provides
    fun provideAuditDao(db: AppDatabase): AuditDao = db.auditDao()

    @Provides
    fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()

    @Provides
    fun provideAgentStepDao(db: AppDatabase): AgentStepDao = db.agentStepDao()

    @Provides
    fun provideMemoryDao(db: AppDatabase): MemoryDao = db.memoryDao()

    @Provides
    fun provideModelConfigDao(db: AppDatabase): ModelConfigDao = db.modelConfigDao()

    @Provides
    fun provideStandingInstructionDao(db: AppDatabase): StandingInstructionDao =
        db.standingInstructionDao()
}
