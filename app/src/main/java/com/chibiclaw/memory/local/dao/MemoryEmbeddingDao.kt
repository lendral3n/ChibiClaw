package com.chibiclaw.memory.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chibiclaw.memory.local.entity.MemoryEmbedding

@Dao
interface MemoryEmbeddingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(embedding: MemoryEmbedding): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(embeddings: List<MemoryEmbedding>): List<Long>

    @Query("SELECT * FROM memory_embedding WHERE sourceType = :type ORDER BY timestamp DESC LIMIT :limit")
    suspend fun byType(type: String, limit: Int = 500): List<MemoryEmbedding>

    @Query("SELECT * FROM memory_embedding ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int = 1000): List<MemoryEmbedding>

    @Query("SELECT COUNT(*) FROM memory_embedding")
    suspend fun count(): Int

    @Query("DELETE FROM memory_embedding WHERE sourceType = :type AND sourceId = :sourceId")
    suspend fun deleteSource(type: String, sourceId: Long)

    @Query("DELETE FROM memory_embedding WHERE timestamp < :beforeMs")
    suspend fun purgeOlderThan(beforeMs: Long): Int

    @Query("DELETE FROM memory_embedding")
    suspend fun clear()
}
