package com.cerevya.domain

/**
 * MemorySummarizer - Gera resumos automáticos para memórias
 * 
 * Responsabilidades:
 * - Gerar título automático
 * - Criar resumo curto
 * - Extrair tags inteligentes
 */
object MemorySummarizer {

    private val stopWords = setOf(
        "o", "a", "os", "as", "um", "uma", "uns", "umas", "de", "da", "do",
        "em", "no", "na", "nos", "nas", "por", "para", "com", "sem", "sob",
        "sobre", "entre", "até", "ao", "aos", "à", "às", "e", "é", "ser",
        "que", "qual", "quando", "onde", "como", "porque", "mas", "ou", "se",
        "não", "sim", "muito", "mais", "menos", "todo", "toda", "isso",
        "essa", "este", "esta", "esse", "aquele", "aquela", "você", "vocês",
        "só", "já", "ainda", "tão", "grande", "pequeno", "bom", "ruim",
        "novo", "velho", "outro", "mesmo", "próprio", "qualquer", "cada",
        "algum", "alguma", "nenhum", "nenhuma", "vários", "várias", "pouco",
        "melhor", "pior", "tanto", "agora", "depois", "antes", "aqui",
        "ali", "lá", "dentro", "fora", "cima", "baixo", "longe", "perto",
        "hoje", "amanhã", "ontem", "sempre", "nunca", "talvez", "pode",
        "deve", "quer", "vai", "vou", "tem", "têm", "há", "era", "foi",
        "ser", "estar", "ficar", "tornar", "jadi", "tenho", "tenha"
    )

    private val importantPrefixes = listOf(
        "ideia de", "ideia para", "app", "projeto", "plano", "meta",
        "nota", "lembrete", "tarefa", "compromisso", "reunião",
        "contato", "endereço", "senha", "login", "código",
        "anotação", "observação", "importante", "urgente"
    )

    /**
     * Gera título automático para a memória
     */
    fun generateTitle(content: String): String {
        val words = content
            .split(Regex("[\\s,.!?;:\"]+"))
            .filter { it.length > 2 }
            .filter { it.lowercase() !in stopWords }
        
        // Look for important prefixes
        for (prefix in importantPrefixes) {
            if (content.lowercase().contains(prefix)) {
                val index = content.lowercase().indexOf(prefix)
                val start = maxOf(0, index)
                val end = minOf(content.length, index + 50)
                val extracted = content.substring(start, end).trim()
                if (extracted.length > 5) {
                    return extracted.replaceFirstChar { it.uppercase() }
                }
            }
        }
        
        // Otherwise, use first meaningful words
        return if (words.isNotEmpty()) {
            words.take(4).joinToString(" ").replaceFirstChar { it.uppercase() }
        } else {
            content.take(40).replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Gera resumo curto (primeiras partes do conteúdo)
     */
    fun generateSummary(content: String, maxLength: Int = 100): String {
        return if (content.length <= maxLength) {
            content
        } else {
            val truncated = content.take(maxLength)
            val lastSpace = truncated.lastIndexOf(' ')
            if (lastSpace > maxLength * 0.7) {
                truncated.substring(0, lastSpace) + "..."
            } else {
                truncated + "..."
            }
        }
    }

    /**
     * Gera tags automaticamente
     */
    fun generateTags(content: String, maxTags: Int = 5): List<String> {
        val words = content.lowercase()
            .split(Regex("[\\s,.!?;:\"]+"))
            .filter { it.length > 2 }
            .filter { it.lowercase() !in stopWords }
            .filter { !it.matches(Regex("^\\d+$")) } // Remove pure numbers
            .distinct()
        
        // Score words by importance
        val scored = words.map { word ->
            var score = word.length // Longer words get higher base score
            
            // Boost words that appear in important prefixes
            importantPrefixes.forEach { prefix ->
                if (content.lowercase().contains("$prefix $word") || 
                    content.lowercase().contains("$word $prefix")) {
                    score += 10
                }
            }
            
            // Boost words that appear multiple times
            val count = content.lowercase().split(word).size - 1
            score += count * 2
            
            // Boost words at the start
            if (content.lowercase().startsWith(word)) {
                score += 5
            }
            
            word to score
        }
        
        return scored
            .sortedByDescending { it.second }
            .take(maxTags)
            .map { it.first }
    }

    /**
     * Gera título e resumo juntos
     */
    data class MemorySummary(
        val title: String,
        val summary: String,
        val tags: List<String>
    )
    
    fun summarize(content: String): MemorySummary {
        return MemorySummary(
            title = generateTitle(content),
            summary = generateSummary(content),
            tags = generateTags(content)
        )
    }
}
