package com.cerevya.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cerevya.data.chat.ChatManager
import com.cerevya.data.chat.MessageEntity
import com.cerevya.data.chat.MessageRole
import com.cerevya.data.repository.MemoryRepository
import com.cerevya.domain.ContextManager
import com.cerevya.domain.Intention
import com.cerevya.domain.IntentionEngine
import com.cerevya.domain.MemoryCategorizer
import com.cerevya.domain.MemorySummarizer
import com.cerevya.domain.SemanticSearch
import com.cerevya.domain.models.Message
import com.cerevya.domain.models.MemoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val memoryResults: List<MemoryEntity> = emptyList(),
    val error: String? = null
)

class ChatViewModel(
    private val repository: MemoryRepository,
    private val chatManager: ChatManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val contextManager = ContextManager()

    init {
        // Observar mensagens em tempo real do Firestore
        viewModelScope.launch {
            chatManager.observeMessages().collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
    }

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun clearMemoryResults() {
        _uiState.value = _uiState.value.copy(memoryResults = emptyList())
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            inputText = "",
            memoryResults = emptyList()
        )

        viewModelScope.launch {
            // Salvar mensagem no Firestore
            val success = chatManager.sendMessage(text)
            
            if (success) {
                processUserMessage(text)
            } else {
                _uiState.value = _uiState.value.copy(
                    error = "Erro ao enviar mensagem"
                )
            }
        }
    }

    private suspend fun processUserMessage(text: String) {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        try {
            val recentContext = contextManager.getLastUserMessage()
            val resolvedText = contextManager.resolveContext(text)
            val intention = IntentionEngine.detectIntention(resolvedText, recentContext)
            contextManager.addMessage(Message(content = text, isUser = true))

            when (intention) {
                Intention.SAVE_MEMORY -> handleSaveMemory(resolvedText)
                Intention.SEARCH_MEMORY -> handleSearchMemory(resolvedText)
                Intention.LIST_MEMORIES -> handleListMemories()
                Intention.NORMAL_CHAT -> handleNormalChat()
            }
        } finally {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private suspend fun handleSaveMemory(text: String) {
        val context = contextManager.getLastUserMessage()
        val content = IntentionEngine.extractMemoryContent(text, context)
        
        if (content.isNotEmpty()) {
            val summary = MemorySummarizer.summarize(content)
            val category = MemoryCategorizer.categorize(content)
            
            val memory = MemoryEntity(
                content = content,
                category = category.displayName,
                tags = summary.tags.joinToString(",")
            )
            
            repository.insertMemory(memory)
            
            // Salvar resposta no Firestore
            chatManager.saveAssistantMessage(
                "✅ Memória salva!\n\n📝 ${summary.title}\n📁 Categoria: ${category.displayName}\n🏷️ Tags: ${summary.tags.take(3).joinToString(", ")}"
            )
        } else {
            chatManager.saveAssistantMessage(
                "🤔 Não consegui identificar o conteúdo para salvar. Tente: 'salva isso: sua ideia'"
            )
        }
    }

    private suspend fun handleSearchMemory(text: String) {
        try {
            val searchQuery = IntentionEngine.extractSearchQuery(text)
            val allMemories = repository.getAllMemories().first()
            val searchResults = SemanticSearch.search(allMemories, searchQuery)
            
            if (searchResults.isEmpty()) {
                chatManager.saveAssistantMessage(
                    "🔍 Nenhuma memória encontrada para: \"$searchQuery\""
                )
            } else {
                val topResults = searchResults.take(5).map { it.memory }
                _uiState.value = _uiState.value.copy(memoryResults = topResults)
                
                val responseText = if (searchResults.size > 5) {
                    "🔍 Encontrei ${searchResults.size} resultados (mostrando os 5 mais relevantes):"
                } else {
                    "🔍 Encontrei ${searchResults.size} memória(s):"
                }
                
                chatManager.saveAssistantMessage(responseText)
            }
        } catch (e: Exception) {
            chatManager.saveAssistantMessage("❌ Erro ao buscar memórias.")
        }
    }

    private suspend fun handleListMemories() {
        try {
            val memories = repository.getAllMemories().first()
            
            if (memories.isEmpty()) {
                chatManager.saveAssistantMessage(
                    "📝 Nenhuma memória ainda.\n\nDiga 'vou anotar isso: sua ideia' para criar a primeira!"
                )
            } else {
                val sortedMemories = memories.sortedByDescending { it.createdAt }
                _uiState.value = _uiState.value.copy(memoryResults = sortedMemories)
                chatManager.saveAssistantMessage(
                    "🧠 Você tem ${memories.size} memória(s) salvas:"
                )
            }
        } catch (e: Exception) {
            chatManager.saveAssistantMessage("❌ Erro ao carregar memórias.")
        }
    }

    private suspend fun handleNormalChat() {
        val responses = listOf(
            "👋 Olá! Sou o Cerevya, seu segundo cérebro digital.",
            "💡 Diga 'vou anotar isso: minha ideia' para salvar uma memória.",
            "🔍 Diga 'me mostra minhas ideias' para buscar memórias.",
            "📋 Diga 'minhas memórias' para ver tudo.",
            "🧠 Estou aqui para ajudar a organizar seus pensamentos!",
            "💭 Pode me contar sobre suas ideias!",
            "🎯 Como posso ajudar a organizar suas ideias?"
        )
        
        chatManager.saveAssistantMessage(responses.random())
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearChat() {
        viewModelScope.launch {
            chatManager.clearAllMessages()
        }
    }

    override fun onCleared() {
        super.onCleared()
        chatManager.clearMessages()
    }

    class Factory(
        private val repository: MemoryRepository,
        private val chatManager: ChatManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repository, chatManager) as T
        }
    }
}
