package com.cerevya.ai

/**
 * Papel da mensagem na conversa
 */
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT
}

/**
 * Mensagem trocada com a IA
 */
data class AIMessage(
    val role: MessageRole,
    val content: String
)

/**
 * Requisição para a API de chat
 */
data class ChatRequest(
    val model: String,
    val messages: List<AIMessage>,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val stream: Boolean = true
)

/**
 * Resposta da API de chat
 */
data class ChatResponse(
    val id: String,
    val model: String,
    val content: String,
    val finishReason: String,
    val usage: UsageInfo? = null
)

/**
 * Informações de uso da API
 */
data class UsageInfo(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
)

/**
 * Chunk de streaming da resposta
 */
data class StreamChunk(
    val content: String,
    val isComplete: Boolean
)

/**
 * Estados possíveis da IA
 */
sealed class AIState {
    data object Idle : AIState()
    data object Thinking : AIState()
    data class Responding(val partialContent: String = "") : AIState()
    data class Error(val message: String) : AIState()
    data object NoInternet : AIState()
    data object Timeout : AIState()
}
