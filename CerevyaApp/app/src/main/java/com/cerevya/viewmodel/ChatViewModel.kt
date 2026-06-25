package com.cerevya.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cerevya.data.repository.MemoryRepository
import com.cerevya.domain.Intention
import com.cerevya.domain.IntentionEngine
import com.cerevya.domain.MemoryCategorizer
import com.cerevya.domain.models.Message
import com.cerevya.domain.models.MemoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val memoryResults: List<MemoryEntity> = emptyList()
)

class ChatViewModel(private val repository: MemoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun clearMemoryResults() {
        _uiState.value = _uiState.value.copy(memoryResults = emptyList())
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val userMessage = Message(content = text, isUser = true)
        val currentMessages = _uiState.value.messages + userMessage
        
        _uiState.value = _uiState.value.copy(
            messages = currentMessages,
            inputText = "",
            memoryResults = emptyList()
        )

        viewModelScope.launch {
            processUserMessage(text)
        }
    }

    private suspend fun processUserMessage(text: String) {
        val intention = IntentionEngine.detectIntention(text)

        when (intention) {
            Intention.SAVE_MEMORY -> handleSaveMemory(text)
            Intention.SEARCH_MEMORY -> handleSearchMemory(text)
            Intention.LIST_MEMORIES -> handleListMemories()
            Intention.NORMAL_CHAT -> handleNormalChat()
        }
    }

    private suspend fun handleSaveMemory(text: String) {
        val content = IntentionEngine.extractMemoryContent(text)
        
        if (content.isNotEmpty()) {
            val category = MemoryCategorizer.categorize(content)
            val tags = MemoryCategorizer.generateTags(content)
            
            val memory = MemoryEntity(
                content = content,
                category = category.displayName,
                tags = tags.joinToString(",")
            )
            
            repository.insertMemory(memory)
            
            val successMessage = Message(
                content = "✅ Memória salva!\n\n📁 Categoria: ${category.displayName}\n🏷️ Tags: ${tags.take(3).joinToString(", ")}",
                isUser = false,
                memoryId = memory.id
            )
            addSystemMessage(successMessage)
        } else {
            addSystemMessage(Message(
                content = "🤔 Não consegui identificar o conteúdo para salvar. Tente: 'salva isso: sua ideia'",
                isUser = false
            ))
        }
    }

    private suspend fun handleSearchMemory(text: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        try {
            // Extract search query from message
            val searchQuery = text.lowercase()
                .replace("me mostra", "")
                .replace("o que eu", "")
                .replace("minhas ideias", "")
                .replace("lembrar", "")
                .replace("busca", "")
                .replace("procurar", "")
                .replace("acha isso", "")
                .trim()
            
            val memories = if (searchQuery.isNotEmpty()) {
                repository.searchMemories(searchQuery).first()
            } else {
                repository.getAllMemories().first()
            }
            
            if (memories.isEmpty()) {
                addSystemMessage(Message(
                    content = "🔍 Nenhuma memória encontrada para: \"$searchQuery\"",
                    isUser = false
                ))
            } else {
                _uiState.value = _uiState.value.copy(memoryResults = memories)
                addSystemMessage(Message(
                    content = "🔍 Encontrei ${memories.size} memória(s):",
                    isUser = false
                ))
            }
        } catch (e: Exception) {
            addSystemMessage(Message(
                content = "❌ Erro ao buscar memórias.",
                isUser = false
            ))
        } finally {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private suspend fun handleListMemories() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        try {
            val memories = repository.getAllMemories().first()
            
            if (memories.isEmpty()) {
                addSystemMessage(Message(
                    content = "📝 Nenhuma memória ainda.\n\nDiga 'vou anotar isso: sua ideia' para criar a primeira!",
                    isUser = false
                ))
            } else {
                _uiState.value = _uiState.value.copy(memoryResults = memories)
                addSystemMessage(Message(
                    content = "🧠 Você tem ${memories.size} memória(s) salvas:",
                    isUser = false
                ))
            }
        } catch (e: Exception) {
            addSystemMessage(Message(
                content = "❌ Erro ao carregar memórias.",
                isUser = false
            ))
        } finally {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private fun handleNormalChat() {
        val responses = listOf(
            "👋 Olá! Sou o Cerevya, seu segundo cérebro digital.",
            "💡 Diga 'vou anotar isso: minha ideia' para salvar uma memória.",
            "🔍 Diga 'me mostra minhas ideias' para buscar memórias.",
            "📋 Diga 'minhas memórias' para ver tudo.",
            "🧠 Estou aqui para ajudar a organizar seus pensamentos!"
        )
        
        addSystemMessage(Message(
            content = responses.random(),
            isUser = false
        ))
    }

    private fun addSystemMessage(message: Message) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + message
        )
    }

    class Factory(private val repository: MemoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repository) as T
        }
    }
}
