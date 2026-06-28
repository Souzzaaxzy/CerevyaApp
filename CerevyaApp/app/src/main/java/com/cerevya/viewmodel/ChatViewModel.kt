package com.cerevya.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cerevya.data.chat.ChatRepository
import com.cerevya.data.chat.MessageEntity
import com.cerevya.data.chat.MessageRole
import com.cerevya.data.database.ChatEntity
import com.cerevya.data.repository.MemoryRepository
import com.cerevya.domain.models.MemoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<MessageEntity> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val memoryResults: List<MemoryEntity> = emptyList(),
    val error: String? = null,
    val chatTitle: String? = null
)

class ChatViewModel(
    private val repository: MemoryRepository,
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Observar chats
    val chats: StateFlow<List<ChatEntity>> = chatRepository.chats
    
    // Chat ativo
    val activeChat: StateFlow<ChatEntity?> = chatRepository.activeChat

    init {
        // Observar chat ativo
        viewModelScope.launch {
            chatRepository.activeChat.collect { chat ->
                chat?.let {
                    _uiState.value = _uiState.value.copy(chatTitle = it.title)
                }
            }
        }
        
        // Observar mensagens do chat ativo
        viewModelScope.launch {
            chatRepository.messages.collect { messages ->
                _uiState.value = _uiState.value.copy(messages = messages)
            }
        }
    }

    /**
     * Carrega as mensagens de um chat específico
     */
    fun loadChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.setActiveChat(chatId)
            chatRepository.observeMessages(chatId)
        }
    }

    /**
     * Cria um novo chat vazio
     */
    fun createNewChat() {
        viewModelScope.launch {
            val chat = chatRepository.createChat()
            _uiState.value = _uiState.value.copy(
                messages = emptyList(),
                chatTitle = chat.title
            )
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
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            // Salvar mensagem localmente
            val success = chatRepository.sendMessage(text)
            
            if (success) {
                // Adicionar resposta mock para testes
                addMockResponse()
            } else {
                _uiState.value = _uiState.value.copy(
                    error = "Erro ao enviar mensagem"
                )
            }
            
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
    
    /**
     * Adiciona resposta mock para testes
     */
    private suspend fun addMockResponse() {
        val responses = listOf(
            "👋 Entendi! Sua mensagem foi recebida.\n\nEsta é uma resposta de teste enquanto a IA não está integrada. Continue enviando mensagens para testar o sistema!",
            "💬 Mensagem registrada!\n\nO sistema de chat está funcionando corretamente. Aguarde a integração com a IA para respostas mais inteligentes.",
            "✅ Recebido! Mensagem salva com sucesso.\n\nPosso ajudar com algo mais?",
            "🎯 Entendi sua mensagem!\n\nO sistema está funcionando em modo de testes. Aguarde a integração da IA para funcionalidades completas.",
            "🧠 Mensagem processada!\n\nO histórico da conversa está sendo mantido corretamente."
        )
        
        chatRepository.saveAssistantMessage(responses.random())
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
    }

    class Factory(
        private val repository: MemoryRepository,
        private val chatRepository: ChatRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repository, chatRepository) as T
        }
    }
}
