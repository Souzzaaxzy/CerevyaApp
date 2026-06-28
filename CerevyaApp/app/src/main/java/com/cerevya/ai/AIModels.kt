package com.cerevya.ai

/**
 * Modelos de IA disponíveis no Groq
 */
enum class AIModel(
    val displayName: String,
    val description: String,
    val modelId: String
) {
    LLAMA_3_3_70B(
        displayName = "Llama 3.3 70B",
        description = "Modelo rápido e eficiente para conversas gerais",
        modelId = "llama-3.3-70b-versatile"
    ),
    LLAMA_4_SCOUT(
        displayName = "Llama 4 Scout",
        description = "Modelo multimodal com excelente performance",
        modelId = "llama-4-scout"
    ),
    QWEN_3_32B(
        displayName = "Qwen 3 32B",
        description = "Modelo em português otimizado",
        modelId = "qwen-3-32b"
    ),
    QWEN_3_6_27B(
        displayName = "Qwen 3.6 27B",
        description = "Modelo rápido para respostas instantâneas",
        modelId = "qwen-3.6-27b"
    )
}

/**
 * Provedores de IA suportados
 */
enum class AIProvider {
    GROQ,
    OPENAI,
    DEEPSEEK,
    GEMINI
}

/**
 * Configurações globais da IA
 */
data class AIConfig(
    val provider: AIProvider = AIProvider.GROQ,
    val model: AIModel = AIModel.LLAMA_3_3_70B,
    val apiKey: String = "",
    val baseUrl: String = "https://api.groq.com/openai/v1",
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val streamingEnabled: Boolean = true
)
