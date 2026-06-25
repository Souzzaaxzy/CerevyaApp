package com.cerevya.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cerevya.data.repository.MemoryRepository
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
    val isLoading: Boolean = false
)

class ChatViewModel(private val repository: MemoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val showMemoriesCommand = "mostrar memórias"

    fun updateInputText(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        val userMessage = Message(content = text, isUser = true)
        val currentMessages = _uiState.value.messages + userMessage
        
        _uiState.value = _uiState.value.copy(
            messages = currentMessages,
            inputText = ""
        )

        viewModelScope.launch {
            processUserMessage(text)
        }
    }

    private suspend fun processUserMessage(text: String) {
        val lowerText = text.lowercase()

        when {
            lowerText.startsWith("salva isso") -> handleSaveMemory(text)
            lowerText.startsWith("lembra disso") -> handleSaveMemory(text, "lembra disso")
            lowerText.startsWith("guarda isso") -> handleSaveMemory(text, "guarda isso")
            lowerText == showMemoriesCommand -> handleShowMemories()
            else -> {
                val systemMessage = Message(
                    content = "Olá! Sou o Cerevya, seu segundo cérebro digital. \n\n" +
                            "Diga 'salva isso: [sua ideia]' para salvar uma memória.\n" +
                            "Diga 'mostrar memórias' para ver suas memórias.",
                    isUser = false
                )
                addSystemMessage(systemMessage)
            }
        }
    }

    private suspend fun handleSaveMemory(text: String, command: String = "salva isso") {
        val content = text.removePrefix(command).removePrefix(":").removePrefix(" ").trim()
        
        if (content.isNotEmpty()) {
            val memory = MemoryEntity(content = content)
            repository.insertMemory(memory)
            
            val successMessage = Message(
                content = "✅ Memória salva com sucesso!",
                isUser = false
            )
            addSystemMessage(successMessage)
        } else {
            val errorMessage = Message(
                content = "Por favor, forneça o conteúdo para salvar. Ex: 'salva isso: minha ideia'",
                isUser = false
            )
            addSystemMessage(errorMessage)
        }
    }

    private suspend fun handleShowMemories() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        try {
            val memories = repository.getAllMemories().first()
            val memoriesList = memories.joinToString("\n") { "• ${it.content}" }
            
            val message = if (memories.isEmpty()) {
                "📝 Nenhuma memória encontrada. Comece salvando algo!"
            } else {
                "🧠 Minhas memórias:\n\n$memoriesList"
            }
            
            val systemMessage = Message(content = message, isUser = false)
            addSystemMessage(systemMessage)
        } catch (e: Exception) {
            val errorMessage = Message(
                content = "❌ Erro ao carregar memórias.",
                isUser = false
            )
            addSystemMessage(errorMessage)
        } finally {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
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
