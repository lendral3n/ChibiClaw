package com.chibiclaw.memory.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_history")
data class CommandHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val result: String,
    val state: String,
    val severity: String,
    val executionTier: Int = -1,
    val timestamp: Long = System.currentTimeMillis()
)
