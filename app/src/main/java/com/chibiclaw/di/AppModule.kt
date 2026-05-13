package com.chibiclaw.di

import android.content.Context
import com.chibiclaw.data.database.AppDatabase
import com.chibiclaw.data.database.AuditDao
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
}
