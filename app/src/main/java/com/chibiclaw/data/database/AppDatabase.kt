package com.chibiclaw.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.chibiclaw.data.database.converters.InstantConverter
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Phase 0 database — hanya AuditLog entity.
 *
 * Phase 1+ akan tambah: TaskEntity, AgentStepEntity, MemoryRecordEntity, dll
 * dengan migration explicit (jangan auto-migrate; data sensitif, harus terkontrol).
 *
 * Database di-encrypt dengan SQLCipher passphrase yang di-store di
 * EncryptedSharedPreferences (Keystore-backed). Lihat SecurityModule.
 */
@Database(
    entities = [
        AuditLogEntity::class,
        // Phase 1 add: TaskEntity, AgentStepEntity, MemoryRecordEntity, ModelConfigEntity, CommandHistoryEntity
        // Phase 6 add: StandingInstructionEntity
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(InstantConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun auditDao(): AuditDao

    companion object {
        private const val DB_NAME = "chibiclaw.db"

        fun create(context: Context, passphrase: ByteArray): AppDatabase {
            // SQLCipher init harus dipanggil sekali sebelum Room create.
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration(dropAllTables = false)
                .build()
        }
    }
}
