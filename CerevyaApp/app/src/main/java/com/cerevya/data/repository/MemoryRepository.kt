package com.cerevya.data.repository

import com.cerevya.data.database.MemoryDao
import com.cerevya.domain.models.MemoryEntity
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {

    fun getAllMemories(): Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    fun searchMemories(query: String): Flow<List<MemoryEntity>> = memoryDao.searchMemories(query)

    suspend fun getMemoryById(id: String): MemoryEntity? = memoryDao.getMemoryById(id)

    suspend fun insertMemory(memory: MemoryEntity) = memoryDao.insertMemory(memory)

    suspend fun updateMemory(memory: MemoryEntity) = memoryDao.updateMemory(memory)

    suspend fun deleteMemory(memory: MemoryEntity) = memoryDao.deleteMemory(memory)
}
