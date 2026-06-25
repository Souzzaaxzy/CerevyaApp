package com.cerevya.domain

/**
 * AIService - Interface para futura integração com IA real
 * 
 * Esta interface permite preparação para:
 * - OpenAI API (GPT-4, GPT-3.5)
 * - LLM local (Ollama, Llama)
 * - Modelos customizados
 * 
 * Por enquanto, usa implementação simulada local
 */
interface AIService {
    
    /**
     * Interpreta mensagem usando contexto
     * @param message Mensagem atual
     * @param context Lista de mensagens de contexto
     * @return Resultado da interpretação
     */
    suspend fun interpret(message: String, context: List<String>): AIResult
    
    /**
     * Gera resposta inteligente
     * @param prompt Prompt para geração
     * @param context Contexto adicional
     * @return Resposta gerada
     */
    suspend fun generateResponse(prompt: String, context: List<String> = emptyList()): String
    
    /**
     * Resume texto
     * @param text Texto para resumir
     * @param maxLength Tamanho máximo do resumo
     * @return Resumo gerado
     */
    suspend fun summarize(text: String, maxLength: Int = 100): String
    
    /**
     * Verifica se o serviço está disponível
     */
    fun isAvailable(): Boolean
}

/**
 * Resultado da interpretação de IA
 */
data class AIResult(
    val intention: Intention,
    val confidence: Float, // 0.0 a 1.0
    val extractedContent: String?,
    val suggestion: String? = null
)

/**
 * Implementação simulada local do AIService
 * Usa regras e heurísticas em vez de IA real
 */
class LocalAIService : AIService {
    
    private val intentionEngine = IntentionEngine
    private val contextManager = ContextManager()
    
    override suspend fun interpret(message: String, context: List<String>): AIResult {
        // Combine message with context
        val fullContext = context.takeLast(10).toMutableList()
        fullContext.add(message)
        
        // Detect intention
        val intention = intentionEngine.detectIntention(message)
        
        // Extract content
        val content = intentionEngine.extractMemoryContent(message)
        
        // Calculate confidence based on how clear the intention is
        val confidence = calculateConfidence(message, intention)
        
        return AIResult(
            intention = intention,
            confidence = confidence,
            extractedContent = content
        )
    }
    
    override suspend fun generateResponse(prompt: String, context: List<String>): String {
        // Simple response generation based on rules
        return generateRuleBasedResponse(prompt)
    }
    
    override suspend fun summarize(text: String, maxLength: Int): String {
        return MemorySummarizer.generateSummary(text, maxLength)
    }
    
    override fun isAvailable(): Boolean = true
    
    private fun calculateConfidence(message: String, intention: Intention): Float {
        val lowerMessage = message.lowercase()
        
        // High confidence patterns
        val highConfidencePatterns = listOf(
            "salva isso:", "guarda isso:", "lembra disso:",
            "vou anotar", "anotar isso"
        )
        
        // Medium confidence patterns
        val mediumConfidencePatterns = listOf(
            "salva", "guarda", "lembra", "anota"
        )
        
        return when {
            highConfidencePatterns.any { lowerMessage.contains(it) } -> 0.9f
            mediumConfidencePatterns.any { lowerMessage.contains(it) } -> 0.7f
            intention == Intention.NORMAL_CHAT -> 0.8f
            else -> 0.5f
        }
    }
    
    private fun generateRuleBasedResponse(prompt: String): String {
        val lower = prompt.lowercase()
        
        return when {
            lower.contains("oi") || lower.contains("olá") || lower.contains("hey") ->
                "Olá! Como posso ajudar?"
            lower.contains("obrigado") || lower.contains("valeu") ->
                "De nada! Estou aqui para ajudar."
            lower.contains("ajuda") || lower.contains("help") ->
                "Posso ajudar a salvar memórias, buscar ideias ou organizar seus pensamentos!"
            else ->
                "Entendi. Como posso ajudar com isso?"
        }
    }
}
