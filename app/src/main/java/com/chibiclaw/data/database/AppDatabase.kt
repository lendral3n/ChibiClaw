package com.chibiclaw.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chibiclaw.data.database.converters.InstantConverter
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Schema v4 entities:
 * - AuditLog (Phase 0)
 * - Task + AgentStep (Phase 1)
 * - MemoryRecord (Phase 1)
 * - ModelConfig (Phase 4 — adapter quota + session)
 * - StandingInstruction (Phase 6 — initiative engine directives)
 *
 * Phase 8 akan tambah TaskDependency.
 */
@Database(
    entities = [
        AuditLogEntity::class,
        TaskEntity::class,
        AgentStepEntity::class,
        MemoryRecordEntity::class,
        ModelConfigEntity::class,
        StandingInstructionEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun auditDao(): AuditDao
    abstract fun taskDao(): TaskDao
    abstract fun agentStepDao(): AgentStepDao
    abstract fun memoryDao(): MemoryDao
    abstract fun modelConfigDao(): ModelConfigDao
    abstract fun standingInstructionDao(): StandingInstructionDao

    companion object {
        private const val DB_NAME = "chibiclaw.db"

        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
