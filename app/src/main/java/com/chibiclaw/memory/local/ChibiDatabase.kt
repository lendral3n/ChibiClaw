package com.chibiclaw.memory.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chibiclaw.memory.local.dao.*
import com.chibiclaw.memory.local.entity.*

@Database(
    entities = [
        CommandHistory::class,
        ConversationContext::class,
        AppPattern::class,
        ContactContext::class,
        AppWhitelist::class,
        CronTaskEntity::class,
        MemoryEmbedding::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ChibiDatabase : RoomDatabase() {
    abstract fun commandHistoryDao(): CommandHistoryDao
    abstract fun conversationDao(): ConversationDao
    abstract fun appPatternDao(): AppPatternDao
    abstract fun contactContextDao(): ContactContextDao
    abstract fun whitelistDao(): WhitelistDao
    abstract fun cronTaskDao(): CronTaskDao
    abstract fun memoryEmbeddingDao(): MemoryEmbeddingDao
}
