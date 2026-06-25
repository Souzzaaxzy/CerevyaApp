package com.cerevya.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val memoryResults: List<MemoryEntity> = emptyList()
)

class ChatViewModel(private val repository: MemoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Context manager for conversation context
    private val contextManager = ContextManager()

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
        // Get recent context
        val recentContext = contextManager.getLastUserMessage()
        
        // Resolve contextual references
        val resolvedText = contextManager.resolveContext(text)
        
        // Detect intention with context
        val intention = IntentionEngine.detectIntention(resolvedText, recentContext)
        
        // Add user message to context
        contextManager.addMessage(Message(content = text, isUser = true))

        when (intention) {
            Intention.SAVE_MEMORY -> handleSaveMemory(resolvedText)
            Intention.SEARCH_MEMORY -> handleSearchMemory(resolvedText)
            Intention.LIST_MEMORIES -> handleListMemories()
            Intention.NORMAL_CHAT -> handleNormalChat()
        }
    }

    private suspend fun handleSaveMemory(text: String) {
        // Extract content with context
        val context = contextManager.getLastUserMessage()
        val content = IntentionEngine.extractMemoryContent(text, context)
        
        if (content.isNotEmpty()) {
            // Generate summary
            val summary = MemorySummarizer.summarize(content)
            
            // Categorize
            val category = MemoryCategorizer.categorize(content)
            
            val memory = MemoryEntity(
                content = content,
                category = category.displayName,
                tags = summary.tags.joinToString(",")
            )
            
            repository.insertMemory(memory)
            
            val successMessage = Message(
                content = "✅ Memória salva!\n\n📝 ${summary.title}\n📁 Categoria: ${category.displayName}\n🏷️ Tags: ${summary.tags.take(3).joinToString(", ")}",
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
            // Extract search query
            val searchQuery = IntentionEngine.extractSearchQuery(text)
            
            // Get all memories for semantic search
            val allMemories = repository.getAllMemories().first()
            
            // Perform semantic search
            val searchResults = SemanticSearch.search(allMemories, searchQuery)
            
            if (searchResults.isEmpty()) {
                addSystemMessage(Message(
                    content = "🔍 Nenhuma memória encontrada para: \"$searchQuery\"",
                    isUser = false
                ))
            } else {
                // Get top results
                val topResults = searchResults.take(5).map { it.memory }
                _uiState.value = _uiState.value.copy(memoryResults = topResults)
                
                val responseText = if (searchResults.size > 5) {
                    "🔍 Encontrei ${searchResults.size} resultados (mostrando os 5 mais relevantes):"
                } else {
                    "🔍 Encontrei ${searchResults.size} memória(s):"
                }
                
                addSystemMessage(Message(
                    content = responseText,
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
                // Sort by relevance (most recent first for now)
                val sortedMemories = memories.sortedByDescending { it.createdAt }
                _uiState.value = _uiState.value.copy(memoryResults = sortedMemories)
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
            "🧠 Estou aqui para ajudar a organizar seus pensamentos!",
            "💭 Pode me contar sobre suas ideias!",
            "🎯 Como posso ajudar a organizar suas ideias?"
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
        // Add system message to context
        contextManager.addMessage(message)
    }

    class Factory(private val repository: MemoryRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repository) as T
        }
    }
}
