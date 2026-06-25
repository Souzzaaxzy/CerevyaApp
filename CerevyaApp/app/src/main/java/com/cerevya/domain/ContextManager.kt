package com.cerevya.domain

import com.cerevya.domain.models.Message

/**
 * ContextManager - Armazena e gerencia contexto da conversa
 * 
 * Responsabilidades:
 * - Manter histórico recente de mensagens
 * - Rastrear entidades mencionadas
 * - Permitir referência a mensagens anteriores ("isso", "aquilo")
 */
class ContextManager(
    private val maxContextSize: Int = 20
) {
    private val contextMessages = mutableListOf<Message>()
    private val mentionedEntities = mutableMapOf<String, String>() // reference -> content
    
    /**
     * Adiciona mensagem ao contexto
     */
    fun addMessage(message: Message) {
        contextMessages.add(message)
        
        // Keep only last N messages
        while (contextMessages.size > maxContextSize) {
            contextMessages.removeAt(0)
        }
        
        // Extract and store entities from user messages
        if (message.isUser) {
            extractEntities(message.content)
        }
    }
    
    /**
     * Extrai entidades do texto para referências futuras
     */
    private fun extractEntities(content: String) {
        // Store last user message as "última mensagem"
        mentionedEntities["última"] = content
        mentionedEntities["isso"] = content
        mentionedEntities["isso aí"] = content
        
        // Store first 50 chars as reference
        val reference = content.take(50).lowercase().replace(Regex("[^a-zA-Z0-9\\s]"), "")
        if (reference.length > 10) {
            mentionedEntities[reference] = content
        }
        
        // Extract potential entities (capitalized words or quoted strings)
        val quotedMatch = Regex("\"([^\"]+)\"|'([^']+)'").find(content)
        quotedMatch?.groupValues?.filter { it.isNotEmpty() }?.forEach { quoted ->
            mentionedEntities[quoted.lowercase()] = quoted
        }
    }
    
    /**
     * Resolve referência contextual
     * Ex: "salva isso" -> "salva [conteúdo anterior]"
     */
    fun resolveContext(message: String): String {
        var resolved = message
        val lowerMessage = message.lowercase()
        
        // Check for contextual references
        val references = listOf("isso", "isso aí", "aquilo", "aquele", "aquela", "aquilo aí", "aquele app", "aquilo que")
        
        for (reference in references) {
            if (lowerMessage.contains(reference)) {
                val context = mentionedEntities["última"] ?: mentionedEntities["isso"]
                if (context != null) {
                    // Replace reference with actual content
                    resolved = resolved.replace(Regex("(?i)$reference", RegexOption.IGNORE_CASE), context)
                }
            }
        }
        
        return resolved
    }
    
    /**
     * Retorna contexto recente para análise
     */
    fun getRecentContext(): List<Message> = contextMessages.toList()
    
    /**
     * Retorna entidades mencionadas
     */
    fun getMentionedEntities(): Map<String, String> = mentionedEntities.toMap()
    
    /**
     * Limpa contexto
     */
    fun clear() {
        contextMessages.clear()
        mentionedEntities.clear()
    }
    
    /**
     * Retorna última mensagem do usuário
     */
    fun getLastUserMessage(): String? {
        return contextMessages.filter { it.isUser }.lastOrNull()?.content
    }
    
    /**
     * Verifica se mensagem é uma referência contextual
     */
    fun isContextualReference(message: String): Boolean {
        val lower = message.lowercase()
        val references = listOf("isso", "isso aí", "aquilo", "aquele", "aquela", "aquilo aí", "aquele app", "aquilo que", "daquilo", "daquele", "disso")
        return references.any { lower.contains(it) }
    }
}
