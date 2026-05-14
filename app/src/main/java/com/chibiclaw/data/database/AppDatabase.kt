package com.chibiclaw.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chibiclaw.data.database.converters.InstantConverter
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Schema v6 entities (Phase 9):
 * - AuditLog (Phase 0)
 * - Task + AgentStep (Phase 1)
 * - MemoryRecord (Phase 1 + pinned flag v6)
 * - ModelConfig (Phase 4 — adapter quota + session)
 * - StandingInstruction (Phase 6 — initiative engine directives)
 * - TaskDependency (Phase 8 — subtask edges)
 *
 * Migration history:
 *   v1 → v5: dev iterasi Phase 0-8 (destructive — early data tidak penting)
 *   v5 → v6: Phase 9 (MemoryRecord.pinned)
 *
 * Pre-v5 builds: destructive fallback (acceptable, masih pre-MVP).
 * v5+ builds: proper migrations untuk preserve memory + standing instruction.
 */
@Database(
    entities = [
        AuditLogEntity::class,
        TaskEntity::class,
        AgentStepEntity::class,
        MemoryRecordEntity::class,
        ModelConfigEntity::class,
        StandingInstructionEntity::class,
        TaskDependencyEntity::class,
    ],
    version = 6,
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
    abstract fun taskDependencyDao(): TaskDependencyDao

    companion object {
        private const val DB_NAME = "chibiclaw.db"

        /** v5 → v6: tambah kolom MemoryRecord.pinned default 0. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memory_record ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .fallbackToDestructiveMigrationFrom(dropAllTables = true, 1, 2, 3, 4)
                .build()
        }
    }
}
