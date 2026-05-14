package com.chibiclaw.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant

/**
 * Standing instruction — proactive agent directive yang Fuu evaluasi terus.
 *
 * Storage: trigger di-serialize sebagai JSON (ComplexTrigger sealed-class
 * polymorphic). preAuthorizedToolsCsv comma-separated tool name list.
 *
 * Audit log: setiap fire entry append AuditActionType.STANDING_INSTRUCTION_FIRED
 * (Phase 6) — bisa di-query by user untuk transparency.
 */
@Entity(
    tableName = "standing_instruction",
    indices = [Index("enabled"), Index("priority"), Index("created_at")],
)
data class StandingInstructionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    @ColumnInfo(name = "trigger_json") val triggerJson: String,
    @ColumnInfo(name = "task_template") val taskTemplate: String,
    val enabled: Boolean = true,
    val priority: Int = 3,
    @ColumnInfo(name = "cooldown_ms") val cooldownMs: Long = 0L,
    @ColumnInfo(name = "max_fires_per_day") val maxFiresPerDay: Int = -1,
    @ColumnInfo(name = "pre_authorized_tools_csv") val preAuthorizedToolsCsv: String = "",
    @ColumnInfo(name = "channel") val channel: TaskChannel = TaskChannel.STANDING,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "updated_at") val updatedAt: Instant,
    @ColumnInfo(name = "last_fired_at") val lastFiredAt: Instant? = null,
    @ColumnInfo(name = "fires_today") val firesToday: Int = 0,
    @ColumnInfo(name = "fires_today_reset_at") val firesTodayResetAt: Instant? = null,
    @ColumnInfo(name = "total_fires") val totalFires: Long = 0,
) {
    fun preAuthorizedTools(): List<String> =
        preAuthorizedToolsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
