package com.chibiclaw.memory.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.chibiclaw.memory.local.entity.ContactContext

@Dao
interface ContactContextDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: ContactContext)

    @Update
    suspend fun update(contact: ContactContext)

    @Query("SELECT * FROM contact_context WHERE name LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<ContactContext>

    @Query("SELECT * FROM contact_context WHERE contactId = :id")
    suspend fun getById(id: String): ContactContext?
}
