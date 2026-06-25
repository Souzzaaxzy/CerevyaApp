package com.cerevya.domain

object MemoryCategorizer {

    enum class Category(val displayName: String) {
        IDEIAS("Ideias"),
        ESTUDOS("Estudos"),
        TRABALHO("Trabalho"),
        PESSOAL("Pessoal"),
        OUTROS("Outros")
    }

    private val categoryKeywords = mapOf(
        Category.IDEIAS to listOf(
            "ideia", "ideias", "app", "projeto", "criar", "desenvolver",
            "inovar", "conceito", "brainstorm", "conceito", "startup"
        ),
        Category.ESTUDOS to listOf(
            "estudar", "estudo", "curso", "aprender", "leitura", "livro",
            "matéria", "disciplina", "aula", "conhecimento", "kotlin",
            "programação", "java", "android", "room", "compose"
        ),
        Category.TRABALHO to listOf(
            "trabalho", "job", "projeto", "cliente", "reunião", "deadline",
            "entrega", "tarefa", "office", "excel", "ppt", "apresentação"
        ),
        Category.PESSOAL to listOf(
            "pessoal", "vida", "saúde", "família", "amigos", "viagem",
            "hobby", "lazer", "esporte", "filme", "música", "receita"
        )
    )

    private val stopWords = setOf(
        "o", "a", "os", "as", "um", "uma", "uns", "umas", "de", "da", "do",
        "em", "no", "na", "nos", "nas", "por", "para", "com", "sem", "sob",
        "sobre", "entre", "até", "ao", "aos", "à", "às", "e", "é", "ser",
        "que", "qual", "quando", "onde", "como", "porque", "porque", "mas",
        "ou", "se", "não", "sim", "muito", "mais", "menos", "todo", "toda",
        "isso", "essa", "este", "esta", "esse", "essa", "aquele", "aquela",
        "eu", "tu", "ele", "ela", "nós", "vós", "eles", "elas", "meu",
        "minha", "teu", "tua", "seu", "sua", "nosso", "nossa", "dele",
        "dela", "você", "vocês", "seu", "sua", "só", "já", "ainda", "tão",
        "grande", "pequeno", "bom", "ruim", "novo", "velho", "outro",
        "mesmo", "próprio", "qualquer", "cada", "algum", "alguma", "nenhum",
        "nenhuma", "vários", "várias", "pouco", "pouca", "demais", "mais",
        "melhor", "pior", "tanto", "tanta", "quanto", "quanta", "agora"
    )

    fun categorize(content: String): Category {
        val words = content.lowercase()
            .split(Regex("[\\s,.!?;:\"]+"))
            .filter { it.length > 2 }
        
        val categoryScores = mutableMapOf<Category, Int>()
        
        categoryKeywords.forEach { (category, keywords) ->
            var score = 0
            keywords.forEach { keyword ->
                if (words.any { it.contains(keyword) || keyword.contains(it) }) {
                    score++
                }
            }
            if (score > 0) {
                categoryScores[category] = score
            }
        }
        
        return categoryScores.maxByOrNull { it.value }?.key ?: Category.OUTROS
    }

    fun generateTags(content: String): List<String> {
        val words = content.lowercase()
            .split(Regex("[\\s,.!?;:\"]+"))
            .filter { it.length > 2 }
            .filter { it !in stopWords }
            .distinct()
        
        return words.take(5)
    }

    fun extractTitle(content: String): String {
        // Extract first meaningful words as title
        val words = content
            .split(Regex("[\\s,.!?;:\"]+"))
            .filter { it.length > 2 }
            .filter { it.lowercase() !in stopWords }
            .take(4)
        
        return if (words.isNotEmpty()) {
            words.joinToString(" ").replaceFirstChar { it.uppercase() }
        } else {
            content.take(50)
        }
    }
}
