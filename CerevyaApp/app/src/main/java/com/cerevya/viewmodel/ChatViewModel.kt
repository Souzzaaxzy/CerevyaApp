package com.cerevya.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cerevya.ai.AIChatManager
import com.cerevya.ai.AIState
import com.cerevya.data.chat.ChatRepository
import com.cerevya.data.chat.MessageEntity
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
    val isAIThinking: Boolean = false,
    val isAIResponding: Boolean = false,
    val aiPartialResponse: String = "",
    val memoryResults: List<MemoryEntity> = emptyList(),
    val error: String? = null,
    val chatTitle: String? = null,
    val showRetryButton: Boolean = false,
    val noInternet: Boolean = false
)

class ChatViewModel(
    private val repository: MemoryRepository,
    private val chatRepository: ChatRepository,
    private val aiChatManager: AIChatManager
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
        
        // Observar estado da IA
        viewModelScope.launch {
            aiChatManager.aiState.collect { state ->
                when (state) {
                    is AIState.Idle -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAIThinking = false,
                            isAIResponding = false,
                            aiPartialResponse = "",
                            showRetryButton = false,
                            noInternet = false
                        )
                    }
                    is AIState.Thinking -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAIThinking = true,
                            isAIResponding = false,
                            aiPartialResponse = "",
                            showRetryButton = false
                        )
                    }
                    is AIState.Responding -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAIThinking = false,
                            isAIResponding = true,
                            aiPartialResponse = state.partialContent,
                            showRetryButton = false
                        )
                    }
                    is AIState.Error -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAIThinking = false,
                            isAIResponding = false,
                            aiPartialResponse = "",
                            error = state.message,
                            showRetryButton = true
                        )
                    }
                    is AIState.NoInternet -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAIThinking = false,
                            isAIResponding = false,
                            aiPartialResponse = "",
                            noInternet = true,
                            showRetryButton = true
                        )
                    }
                    is AIState.Timeout -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isAIThinking = false,
                            isAIResponding = false,
                            aiPartialResponse = "",
                            error = "A IA está demorando mais que o esperado",
                            showRetryButton = true
                        )
                    }
                }
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
    
    /**
     * Envia mensagem para a IA
     */
    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        
        // Verificar se já está processando
        if (_uiState.value.isAIThinking || _uiState.value.isAIResponding) {
            return
        }

        _uiState.value = _uiState.value.copy(
            inputText = "",
            memoryResults = emptyList(),
            error = null,
            showRetryButton = false,
            noInternet = false
        )

        viewModelScope.launch {
            aiChatManager.sendUserMessage(text)
        }
    }
    
    /**
     * Tenta novamente após erro
     */
    fun retry() {
        viewModelScope.launch {
            aiChatManager.retryLastMessage()
        }
    }
    
    /**
     * Cancela processamento atual
     */
    fun cancelProcessing() {
        aiChatManager.cancelProcessing()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
    }

    class Factory(
        private val repository: MemoryRepository,
        private val chatRepository: ChatRepository,
        private val aiChatManager: AIChatManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(repository, chatRepository, aiChatManager) as T
        }
    }
}
