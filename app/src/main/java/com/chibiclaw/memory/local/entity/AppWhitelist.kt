package com.chibiclaw.memory.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_whitelist")
data class AppWhitelist(
    @PrimaryKey val packageName: String,
    val allowedTier: Int = 3,
    val policy: String = "auto",
    val addedAt: Long = System.currentTimeMillis()
)
