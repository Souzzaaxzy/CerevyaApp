package com.cerevya.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cerevya.data.repository.MemoryRepository
import com.cerevya.domain.models.MemoryEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryUiState(
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val editingMemory: MemoryEntity? = null,
    val showEditDialog: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryViewModel(private val repository: MemoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")

    val memories: StateFlow<List<MemoryEntity>> = searchQueryFlow
        .flatMapLatest { query ->
            if (query.isEmpty()) {
                repository.getAllMemories()
            } else {
                repository.searchMemories(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateSearchQuery(query: String) {
        searchQueryFlow.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun deleteMemory(memory: MemoryEntity) {
        viewModelScope.launch {
            try {
                repository.deleteMemory(memory)
            } catch (e: Exception) {
                // Log error in production
            }
        }
    }

    fun showEditDialog(memory: MemoryEntity) {
        _uiState.update { it.copy(editingMemory = memory, showEditDialog = true) }
    }

    fun hideEditDialog() {
        _uiState.update { it.copy(editingMemory = null, showEditDialog = false) }
    }

    fun updateMemory(content: String, category: String, tags: String) {
        viewModelScope.launch {
            _uiState.value.editingMemory?.let { memory ->
                try {
                    val updatedMemory = memory.copy(
                        content = content,
                        category = category,
                        tags = tags,
                        updatedAt = System.currentTimeMillis()
                    )
                    repository.updateMemory(updatedMemory)
                    hideEditDialog()
                } catch (e: Exception) {
                    // Log error in production
                }
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
