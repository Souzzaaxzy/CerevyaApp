package com.cerevya.sync

import android.util.Log
import com.cerevya.auth.AuthManager
import com.cerevya.cloud.CloudMemoryManager
import com.cerevya.data.repository.MemoryRepository
import com.cerevya.domain.models.MemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * SyncManager - Gerencia sincronização entre local e cloud
 * 
 * Princípios:
 * - Offline-first: sempre funciona sem internet
 * - Silent sync: sincronização acontece em background
 * - Last-write-wins: estratégia simples de resolução de conflitos
 */
private const val TAG = "SyncManager"

class SyncManager(
    private val repository: MemoryRepository,
    private val cloudManager: CloudMemoryManager,
    private val authManager: AuthManager
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    private val _pendingSyncs = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val pendingSyncs: StateFlow<List<MemoryEntity>> = _pendingSyncs.asStateFlow()
    
    /**
     * Inicializa sync manager
     */
    init {
        // Observa estado de autenticação
        scope.launch {
            authManager.session.collect { session ->
                if (session.isLoggedIn && session.user != null) {
                    enableSync(session.user.userId)
                } else {
                    disableSync()
                }
            }
        }
    }
    
    /**
     * Ativa sincronização
     */
    private fun enableSync(userId: String) {
        Log.d(TAG, "Sync enabled for user: $userId")
        cloudManager.enable(userId)
        _syncState.value = _syncState.value.copy(isEnabled = true)
        
        // Tenta sincronizar pendências
        syncPendingMemories()
    }
    
    /**
     * Desativa sincronização
     */
    private fun disableSync() {
        Log.d(TAG, "Sync disabled")
        cloudManager.disable()
        _syncState.value = _syncState.value.copy(isEnabled = false, isSyncing = false)
    }
    
    /**
     * Salva memória local e agenda sync
     * NUNCA bloqueia - sempre salva local primeiro
     */
    fun saveMemory(memory: MemoryEntity) {
        // Salva localmente imediatamente
        scope.launch {
            try {
                repository.insertMemory(memory)
                Log.d(TAG, "Memory saved locally: ${memory.id}")
                
                // Agenda sync em background
                if (_syncState.value.isEnabled) {
                    addToPendingSync(memory)
                    attemptSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save memory: ${e.message}")
            }
        }
    }
    
    /**
     * Deleta memória local e agenda sync de delete
     */
    fun deleteMemory(memory: MemoryEntity) {
        scope.launch {
            try {
                repository.deleteMemory(memory)
                Log.d(TAG, "Memory deleted locally: ${memory.id}")
                
                if (_syncState.value.isEnabled) {
                    // Agenda delete no cloud
                    removeFromPendingSync(memory.id)
                    scope.launch {
                        cloudManager.deleteMemory(memory.id, authManager.getCurrentUser()?.userId ?: "")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete memory: ${e.message}")
            }
        }
    }
    
    /**
     * Adiciona memória à fila de sync
     */
    private fun addToPendingSync(memory: MemoryEntity) {
        val current = _pendingSyncs.value.toMutableList()
        // Remove se já existir (atualiza)
        current.removeAll { it.id == memory.id }
        current.add(memory)
        _pendingSyncs.value = current
    }
    
    /**
     * Remove memória da fila de sync
     */
    private fun removeFromPendingSync(memoryId: String) {
        _pendingSyncs.value = _pendingSyncs.value.filter { it.id != memoryId }
    }
    
    /**
     * Sincroniza memórias pendentes
     */
    private fun syncPendingMemories() {
        scope.launch {
            val pending = _pendingSyncs.value
            if (pending.isEmpty()) return@launch
            
            val userId = authManager.getCurrentUser()?.userId ?: return@launch
            
            for (memory in pending) {
                val result = cloudManager.syncMemory(memory, userId)
                if (result.isSuccess) {
                    removeFromPendingSync(memory.id)
                    Log.d(TAG, "Synced: ${memory.id}")
                }
            }
            
            authManager.updateLastSync()
        }
    }
    
    /**
     * Tenta sincronizar (se não estiver em progresso)
     */
    private fun attemptSync() {
        if (_syncState.value.isSyncing) {
            Log.d(TAG, "Sync already in progress")
            return
        }
        
        _syncState.value = _syncState.value.copy(isSyncing = true)
        
        scope.launch {
            try {
                syncPendingMemories()
                
                // Também tenta buscar do cloud
                fetchFromCloud()
            } finally {
                _syncState.value = _syncState.value.copy(
                    isSyncing = false,
                    lastSyncTime = System.currentTimeMillis()
                )
            }
        }
    }
    
    /**
     * Busca memórias do cloud e mescla com local
     * Usa estratégia last-write-wins
     */
    private suspend fun fetchFromCloud() {
        val userId = authManager.getCurrentUser()?.userId ?: return
        
        val result = cloudManager.fetchMemories(userId)
        if (result.isSuccess) {
            val cloudMemories = result.getOrNull() ?: return
            
            // Busca memórias locais
            val localMemories = repository.getAllMemories().first()
            
            // Mescla: cloud wins para memórias mais recentes
            for (cloudMemory in cloudMemories) {
                val localMemory = localMemories.find { it.id == cloudMemory.id }
                
                if (localMemory == null) {
                    // Nova memória do cloud
                    repository.insertMemory(cloudMemory)
                    Log.d(TAG, "New memory from cloud: ${cloudMemory.id}")
                } else if (cloudMemory.updatedAt > localMemory.updatedAt) {
                    // Cloud é mais recente - atualiza local
                    repository.updateMemory(cloudMemory)
                    Log.d(TAG, "Updated from cloud: ${cloudMemory.id}")
                }
                // Se local é mais recente, mantém local (já está na fila de sync)
            }
        }
    }
    
    /**
     * Força sincronização completa
     */
    fun forceSync() {
        Log.d(TAG, "Force sync triggered")
        _syncState.value = _syncState.value.copy(isSyncing = true)
        attemptSync()
    }
    
    /**
     * Retorna número de sync pendentes
     */
    fun getPendingCount(): Int = _pendingSyncs.value.size
}

/**
 * Estado da sincronização
 */
data class SyncState(
    val isEnabled: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncTime: Long = 0L,
    val pendingCount: Int = 0
) {
    val lastSyncFormatted: String
        get() = if (lastSyncTime == 0L) {
            "Nunca"
        } else {
            val diff = System.currentTimeMillis() - lastSyncTime
            when {
                diff < 60000 -> "Agora"
                diff < 3600000 -> "${diff / 60000}min"
                diff < 86400000 -> "${diff / 3600000}h"
                else -> "${diff / 86400000}d"
            }
        }
}

