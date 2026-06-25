package com.cerevya.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cerevya.data.repository.MemoryRepository
import com.cerevya.domain.models.MemoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MemoryUiState(
    val memories: List<MemoryEntity> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val editingMemory: MemoryEntity? = null,
    val showEditDialog: Boolean = false
)

class MemoryViewModel(private val repository: MemoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        loadMemories()
    }

    private fun loadMemories() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getAllMemories().collect { memories ->
                _uiState.value = _uiState.value.copy(
                    memories = memories,
                    isLoading = false
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        viewModelScope.launch {
            if (query.isEmpty()) {
                repository.getAllMemories().collect { memories ->
                    _uiState.value = _uiState.value.copy(memories = memories)
                }
            } else {
                repository.searchMemories(query).collect { memories ->
                    _uiState.value = _uiState.value.copy(memories = memories)
                }
            }
        }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            repository.deleteMemory(memory)
        }
    }

    fun showEditDialog(memory: MemoryEntity) {
        _uiState.value = _uiState.value.copy(
            editingMemory = memory,
            showEditDialog = true
        )
    }

    fun hideEditDialog() {
        _uiState.value = _uiState.value.copy(
            editingMemory = null,
            showEditDialog = false
        )
    }

    fun updateMemory(content: String, category: String, tags: String) {
        viewModelScope.launch {
            _uiState.value.editingMemory?.let { memory ->
                val updatedMemory = memory.copy(
                    content = content,
                    category = category,
                    tags = tags,
                    updatedAt = System.currentTimeMillis()
                )
                repository.updateMemory(updatedMemory)
                hideEditDialog()
            }
        }
    }

    class Factory(private val repository: MemoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MemoryViewModel(repository) as T
        }
    }
}
