package com.cerevya.ai

import com.cerevya.data.chat.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Gerenciador de chat com IA
 * 
 * Responsável por:
 * - Coordenar envio/recebimento de mensagens
 * - Gerenciar contexto da conversa
 * - Salvar histórico no repositório local
 * - Controlar estados da IA
 */
class AIChatManager(
    private val chatRepository: ChatRepository,
    private val aiService: AIService
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _aiState = MutableStateFlow<AIState>(AIState.Idle)
    val aiState: StateFlow<AIState> = _aiState.asStateFlow()
    
    private var currentJob: Job? = null
    private var fullResponse = StringBuilder()
    
    /**
     * Envia mensagem do usuário e obtém resposta da IA
     */
    suspend fun sendUserMessage(
        userMessage: String,
        userName: String = "Usuário"
    ): Boolean = withContext(Dispatchers.IO) {
        // Verificar se já está processando
        if (_aiState.value is AIState.Thinking || _aiState.value is AIState.Responding) {
            return@withContext false
        }
        
        try {
            // Obter contexto completo da conversa
            val conversationHistory = chatRepository.messages.value.map { msg ->
                AIMessage(
                    role = if (msg.role.name == "USER") MessageRole.USER else MessageRole.ASSISTANT,
                    content = msg.text
                )
            }
            
            // Adicionar mensagem do usuário
            val allMessages = conversationHistory + AIMessage(
                role = MessageRole.USER,
                content = userMessage
            )
            
            // Salvar mensagem do usuário
            val saved = chatRepository.sendMessage(userMessage, userName)
            if (!saved) {
                _aiState.value = AIState.Error("Erro ao salvar mensagem")
                return@withContext false
            }
            
            // Iniciar processamento
            _aiState.value = AIState.Thinking
            
            // Fazer requisição com streaming
            fullResponse.clear()
            var hasContent = false
            
            aiService.sendMessageStreaming(allMessages).collect { result ->
                result.fold(
                    onSuccess = { chunk ->
                        if (chunk.isComplete) {
                            // Resposta completa recebida
                            _aiState.value = AIState.Idle
                        } else if (chunk.content.isNotEmpty()) {
                            // Novo conteúdo recebido
                            fullResponse.append(chunk.content)
                            _aiState.value = AIState.Responding(fullResponse.toString())
                            hasContent = true
                        }
                    },
                    onFailure = { error ->
                        val exception = error as? AIException
                        _aiState.value = when (exception?.errorType) {
                            AIErrorType.NO_INTERNET -> AIState.NoInternet
                            AIErrorType.TIMEOUT -> AIState.Timeout
                            else -> AIState.Error(exception?.message ?: "Erro desconhecido")
                        }
                    }
                )
            }
            
            // Salvar resposta da IA se houver conteúdo
            if (fullResponse.isNotEmpty()) {
                chatRepository.saveAssistantMessage(fullResponse.toString())
            }
            
            _aiState.value = AIState.Idle
            true
        } catch (e: Exception) {
            _aiState.value = AIState.Error(e.message ?: "Erro desconhecido")
            false
        }
    }
    
    /**
     * Cancela processamento atual
     */
    fun cancelProcessing() {
        currentJob?.cancel()
        _aiState.value = AIState.Idle
    }
    
    /**
     * Tenta novamente após erro
     */
    suspend fun retryLastMessage(): Boolean {
        val messages = chatRepository.messages.value
        if (messages.isEmpty()) return false
        
        // Pegar última mensagem do usuário
        val lastUserMessage = messages.lastOrNull { it.role.name == "USER" }
            ?: return false
        
        return sendUserMessage(lastUserMessage.text, lastUserMessage.userName)
    }
    
    /**
     * Verifica se a IA está disponível
     */
    fun isAvailable(): Boolean {
        return aiService.let {
            true // A disponibilidade será verificada na primeira requisição
        }
    }
}
