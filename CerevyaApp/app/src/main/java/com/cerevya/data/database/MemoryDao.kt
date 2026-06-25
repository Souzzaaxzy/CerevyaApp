package com.cerevya.data.database

import androidx.room.*
import com.cerevya.domain.models.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity)

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("""
        SELECT * FROM memories 
        WHERE content LIKE '%' || :query || '%' 
        OR category LIKE '%' || :query || '%' 
        OR tags LIKE '%' || :query || '%' 
        ORDER BY createdAt DESC
    """)
    fun searchMemories(query: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun getMemoryById(id: String): MemoryEntity?
}
