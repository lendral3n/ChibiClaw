package com.chibiclaw.memory.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chibiclaw.memory.local.entity.ConversationContext

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(context: ConversationContext): Long

    @Query("SELECT * FROM conversation_context WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySession(sessionId: String): List<ConversationContext>

    @Query("SELECT * FROM conversation_context ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 10): List<ConversationContext>

    @Query("SELECT * FROM conversation_context WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 10")
    suspend fun search(query: String): List<ConversationContext>

    @Query("DELETE FROM conversation_context WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}
