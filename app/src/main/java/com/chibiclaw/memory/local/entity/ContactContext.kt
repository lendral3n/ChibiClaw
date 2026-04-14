package com.chibiclaw.memory.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_context")
data class ContactContext(
    @PrimaryKey val contactId: String,
    val name: String,
    val preferredApp: String = "phone",
    val notes: String = "",
    val lastContacted: Long = System.currentTimeMillis()
)
