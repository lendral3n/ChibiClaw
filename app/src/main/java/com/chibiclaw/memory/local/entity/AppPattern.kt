package com.chibiclaw.memory.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_patterns")
data class AppPattern(
    @PrimaryKey val packageName: String,
    val successRate: Float = 1.0f,
    val avgTier: Int = 1,
    val usageCount: Int = 0,
    val lastUsed: Long = System.currentTimeMillis()
)
