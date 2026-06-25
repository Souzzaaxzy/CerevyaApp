package com.cerevya.domain

import com.cerevya.domain.models.MemoryEntity

/**
 * SemanticSearch - Busca semântica simulada
 * 
 * Melhorias sobre busca simples:
 * - Similaridade de palavras
 * - Busca por sinônimos
 * - Ordenação por relevância
 * - Sugestões de resultados relacionados
 */
object SemanticSearch {

    // Common word variations and synonyms
    private val synonyms = mapOf(
        "app" to listOf("aplicativo", "aplicação", "aplicações", "software"),
        "estudo" to listOf("estudos", "aprender", "aprendizado", "conhecimento"),
        "trabalho" to listOf("job", "projeto", "tarefa", "oficina"),
        "ideia" to listOf("ideias", "conceito", "conceitos", "brainstorm"),
        "pessoa" to listOf("pessoal", "vida", "pessoas"),
        "importante" to listOf("urgente", "prioridade", "essencial"),
        "tarefa" to listOf("tarefas", "to-do", "todo", "compromisso"),
        "nota" to listOf("notas", "anotação", "anotações", "recado")
    )

    private val stopWords = setOf(
        "o", "a", "os", "as", "um", "uma", "de", "da", "do", "em", "no", "na",
        "por", "para", "com", "sem", "e", "é", "que", "se", "não", "sim",
        "isso", "essa", "este", "isto", "você", "eu", "ele", "ela", " nós"
    )

    /**
     * Busca memórias por similaridade
     * @param memories Lista de memórias para buscar
     * @param query Query de busca
     * @return Lista ordenada por relevância
     */
    fun search(memories: List<MemoryEntity>, query: String): List<MemorySearchResult> {
        if (query.isBlank()) {
            return memories.map { MemorySearchResult(it, 1.0f, emptyList()) }
        }
        
        val queryTerms = tokenize(query)
        val queryWords = queryTerms.filter { it !in stopWords }
        
        if (queryWords.isEmpty()) {
            return memories.map { MemorySearchResult(it, 0.5f, emptyList()) }
        }
        
        val results = memories.map { memory ->
            val score = calculateRelevance(memory, queryWords, query)
            val relatedTerms = findRelatedTerms(memory, queryWords)
            
            MemorySearchResult(
                memory = memory,
                relevanceScore = score,
                relatedTerms = relatedTerms
            )
        }
        
        return results
            .filter { it.relevanceScore > 0.1f }
            .sortedByDescending { it.relevanceScore }
    }

    /**
     * Tokeniza texto em palavras
     */
    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[\\s,.!?;:\"]+"))
            .filter { it.isNotBlank() }
    }

    /**
     * Calcula relevância de uma memória para a query
     */
    private fun calculateRelevance(memory: MemoryEntity, queryWords: List<String>, query: String): Float {
        var score = 0f
        
        val contentWords = tokenize(memory.content)
        val tagWords = memory.tags.split(",").map { it.trim().lowercase() }
        val categoryWords = tokenize(memory.category)
        
        // Exact match in content (highest weight)
        queryWords.forEach { word ->
            contentWords.forEach { contentWord ->
                if (contentWord.contains(word) || word.contains(contentWord)) {
                    score += 1.0f
                    // Exact match bonus
                    if (contentWord == word) {
                        score += 0.5f
                    }
                }
            }
        }
        
        // Tag match (high weight)
        queryWords.forEach { word ->
            tagWords.forEach { tagWord ->
                if (tagWord.contains(word) || word.contains(tagWord)) {
                    score += 1.5f
                }
            }
        }
        
        // Category match (medium weight)
        queryWords.forEach { word ->
            categoryWords.forEach { categoryWord ->
                if (categoryWord.contains(word) || word.contains(categoryWord)) {
                    score += 0.8f
                }
            }
        }
        
        // Synonym match (bonus)
        queryWords.forEach { word ->
            synonyms[word]?.forEach { synonym ->
                contentWords.forEach { contentWord ->
                    if (contentWord.contains(synonym) || synonym.contains(contentWord)) {
                        score += 0.5f
                    }
                }
            }
            // Also check if query word is a synonym
            synonyms.entries.forEach { (key, values) ->
                if (values.contains(word)) {
                    contentWords.forEach { contentWord ->
                        if (contentWord.contains(key)) {
                            score += 0.3f
                        }
                    }
                }
            }
        }
        
        // Title/beginning match bonus
        if (memory.content.lowercase().startsWith(query.lowercase()) ||
            query.lowercase().startsWith(memory.content.lowercase().take(20))) {
            score += 2.0f
        }
        
        // Exact match bonus
        if (memory.content.lowercase().contains(query.lowercase())) {
            score += 1.0f
        }
        
        // Normalize score
        val maxPossibleScore = queryWords.size * 4.0f
        return (score / maxPossibleScore).coerceIn(0f, 1f)
    }

    /**
     * Encontra termos relacionados
     */
    private fun findRelatedTerms(memory: MemoryEntity, queryWords: List<String>): List<String> {
        val related = mutableListOf<String>()
        val contentWords = tokenize(memory.content)
        
        queryWords.forEach { queryWord ->
            contentWords.forEach { contentWord ->
                if (contentWord != queryWord && 
                    (contentWord.contains(queryWord) || queryWord.contains(contentWord))) {
                    related.add(contentWord)
                }
            }
        }
        
        return related.distinct().take(3)
    }

    /**
     * Sugere termos de busca relacionados
     */
    fun suggestRelatedSearches(allMemories: List<MemoryEntity>, query: String): List<String> {
        val suggestions = mutableSetOf<String>()
        val queryWords = tokenize(query).filter { it !in stopWords }
        
        // Get tags from matching memories
        allMemories.forEach { memory ->
            memory.tags.split(",").forEach { tag ->
                val trimmedTag = tag.trim().lowercase()
                if (queryWords.any { queryWord -> 
                    trimmedTag.contains(queryWord) || queryWord.contains(trimmedTag) 
                }) {
                    suggestions.add(trimmedTag)
                }
            }
        }
        
        return suggestions.take(5).toList()
    }

    /**
     * Resultado de busca com score de relevância
     */
    data class MemorySearchResult(
        val memory: MemoryEntity,
        val relevanceScore: Float,
        val relatedTerms: List<String>
    )
}
