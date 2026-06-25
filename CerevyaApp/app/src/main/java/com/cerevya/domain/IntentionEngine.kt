package com.cerevya.domain

object IntentionEngine {

    private val savePatterns = listOf(
        "salva isso",
        "guarda isso",
        "lembra disso",
        "vou anotar",
        "anotar isso",
        "preciso lembrar",
        "guarda essa",
        "salva essa",
        "anota isso",
        "isso é importante",
        "salva isso aqui",
        "guarda isso aqui",
        "lembra isso",
        "isso deve ser salvo",
        "salva essa ideia",
        "guarda essa ideia"
    )

    private val searchPatterns = listOf(
        "me mostra",
        "o que eu",
        "minhas ideias",
        "lembrar",
        "busca",
        "procurar",
        "encontrar",
        "acha isso",
        "o que lembra",
        "onde está",
        "o que tinha",
        "lembra daquele",
        "aquele app que",
        "aquilo que falei",
        "onde eu coloquei",
        "o que anotei"
    )

    private val listPatterns = listOf(
        "mostrar tudo",
        "mostrar memórias",
        "minhas memórias",
        "listar memórias",
        "ver memórias",
        "todas as memórias",
        "mostra tudo",
        "lista memórias"
    )

    /**
     * Detecta intenção com base na mensagem
     * Pode usar contexto para interpretação mais inteligente
     */
    fun detectIntention(message: String, context: String? = null): Intention {
        val lowerMessage = message.lowercase().trim()

        // Check for contextual references - if message is just a reference, infer from context
        if (isContextualReference(lowerMessage) && context != null) {
            // If there's context and message is a reference, likely SAVE_MEMORY
            if (context.lowercase().length > 5) {
                return Intention.SAVE_MEMORY
            }
        }

        // Check for save patterns
        if (savePatterns.any { lowerMessage.contains(it) }) {
            return Intention.SAVE_MEMORY
        }

        // Check for list patterns
        if (listPatterns.any { lowerMessage.contains(it) }) {
            return Intention.LIST_MEMORIES
        }

        // Check for search patterns
        if (searchPatterns.any { lowerMessage.contains(it) }) {
            return Intention.SEARCH_MEMORY
        }

        // Check for single-word important patterns
        if (isSingleWordSaveIntent(lowerMessage)) {
            return Intention.SAVE_MEMORY
        }

        return Intention.NORMAL_CHAT
    }

    /**
     * Verifica se mensagem é uma referência contextual
     */
    fun isContextualReference(message: String): Boolean {
        val references = listOf(
            "isso", "isso aí", "aquilo", "aquele", "aquela", 
            "aquilo aí", "daquilo", "daquele", "disso", "dessa"
        )
        return references.any { message.trim() == it || message.contains(Regex("^$it[,!?\\s].*")) }
    }

    /**
     * Verifica se é uma intenção de salvar com uma única palavra
     */
    private fun isSingleWordSaveIntent(message: String): Boolean {
        val singleWordSaveIntents = listOf(
            "salva", "salvar", "guarda", "guardar", 
            "lembra", "lembrar", "anota", "anotar"
        )
        return message.trim().split(" ").size <= 2 && singleWordSaveIntents.any { message.contains(it) }
    }

    /**
     * Extrai conteúdo da memória da mensagem
     * Pode usar contexto se mensagem for uma referência
     */
    fun extractMemoryContent(message: String, context: String? = null): String {
        val lowerMessage = message.lowercase()
        
        // If message is just a contextual reference, use context
        if (isContextualReference(lowerMessage) && context != null) {
            return context.trim()
        }
        
        // Remove save command patterns
        var content = message
        
        savePatterns.sortedByDescending { it.length }.forEach { pattern ->
            if (lowerMessage.contains(pattern)) {
                content = content.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
            }
        }
        
        // Remove common separators
        content = content.replace(Regex("^[:\\-–—=\"]+"), "")
        
        // Clean up
        content = content.trim()
        
        // If still empty, try context
        if (content.isEmpty() && context != null) {
            content = context.trim()
        }
        
        // If still empty, return original message
        if (content.isEmpty()) {
            content = message.trim()
        }
        
        return content
    }

    /**
     * Gera query de busca a partir da mensagem
     */
    fun extractSearchQuery(message: String): String {
        var query = message.lowercase()
        
        // Remove search patterns
        searchPatterns.sortedByDescending { it.length }.forEach { pattern ->
            query = query.replace(pattern, "")
        }
        
        // Remove list patterns
        listPatterns.sortedByDescending { it.length }.forEach { pattern ->
            query = query.replace(pattern, "")
        }
        
        // Remove contextual references
        val references = listOf("isso", "aquilo", "aquele", "aquela", "daquilo", "daquele", "disso", "dessa")
        references.forEach { ref ->
            query = query.replace(Regex("(?i)$ref\\s*"), "")
        }
        
        // Clean up
        query = query.replace(Regex("^[\\s,:\\-]+"), "")
        query = query.trim()
        
        return query.ifEmpty { message }
    }
}
