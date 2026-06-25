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
        "anota isso"
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
        "o que tinha"
    )

    private val listPatterns = listOf(
        "mostrar tudo",
        "mostrar memórias",
        "minhas memórias",
        "listar memórias",
        "ver memórias",
        "todas as memórias"
    )

    fun detectIntention(message: String): Intention {
        val lowerMessage = message.lowercase().trim()

        // Check for save patterns first
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

        return Intention.NORMAL_CHAT
    }

    fun extractMemoryContent(message: String): String {
        val lowerMessage = message.lowercase()
        
        // Remove save command patterns
        var content = message
        
        savePatterns.forEach { pattern ->
            if (lowerMessage.contains(pattern)) {
                content = content.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
            }
        }
        
        // Remove common separators
        content = content.replace(Regex("^[:\\-–—]+"), "")
        
        // Clean up
        content = content.trim()
        
        // If still empty, return original message
        if (content.isEmpty()) {
            content = message
        }
        
        return content
    }
}
