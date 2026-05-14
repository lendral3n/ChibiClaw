package com.chibiclaw.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Dao
interface AuditDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AuditLogEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun listRecent(limit: Int = 100): List<AuditLogEntity>

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_log WHERE action_type = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun listByActionType(type: AuditActionType, limit: Int = 100): List<AuditLogEntity>

    @Query("SELECT COUNT(*) FROM audit_log")
    suspend fun count(): Int

    @Query("DELETE FROM audit_log WHERE ttl_until < :now")
    suspend fun deleteExpired(now: Instant): Int

    @Query("DELETE FROM audit_log")
    suspend fun deleteAll()

    @Query("""
        SELECT result_status as status, COUNT(*) as cnt
        FROM audit_log
        WHERE action_type = :type AND timestamp >= :since
        GROUP BY result_status
    """)
    suspend fun countByOutcome(type: AuditActionType, since: Instant): List<AuditOutcomeCount>
}

data class AuditOutcomeCount(
    val status: AuditResultStatus,
    val cnt: Int,
)
