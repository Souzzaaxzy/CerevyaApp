package com.cerevya.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Serviço de IA centralizado para comunicação com provedores
 * 
 * Suporta:
 * - Groq API (padrão)
 * - OpenAI
 * - DeepSeek
 * - Gemini
 * 
 * Preparado para streaming de respostas
 */
class AIService(private var config: AIConfig) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    
    /**
     * Atualiza a configuração da API
     */
    fun updateConfig(newConfig: AIConfig) {
        config = newConfig
    }
    
    /**
     * Atualiza apenas a chave da API
     */
    fun updateApiKey(apiKey: String) {
        config = config.copy(apiKey = apiKey)
    }
    
    /**
     * Envia mensagem para a IA e retorna resposta completa
     */
    suspend fun sendMessage(messages: List<AIMessage>): Result<ChatResponse> = withContext(Dispatchers.IO) {
        try {
            val response = makeRequest(messages, stream = false)
            Result.success(response)
        } catch (e: SocketTimeoutException) {
            Result.failure(AIException(AIErrorType.TIMEOUT, "A resposta está demorando mais que o esperado"))
        } catch (e: IOException) {
            Result.failure(AIException(AIErrorType.NO_INTERNET, "Sem conexão com a internet"))
        } catch (e: Exception) {
            Result.failure(AIException(AIErrorType.API_ERROR, e.message ?: "Erro desconhecido"))
        }
    }
    
    /**
     * Envia mensagem e retorna fluxo de chunks para streaming
     */
    fun sendMessageStreaming(messages: List<AIMessage>): Flow<Result<StreamChunk>> = flow {
        try {
            val requestBody = buildString {
                append("{")
                append("\"model\": \"${config.model.modelId}\",")
                append("\"messages\": [")
                messages.forEachIndexed { index, msg ->
                    append("{")
                    append("\"role\": \"${msg.role.name.lowercase()}\",")
                    append("\"content\": ${escapeJson(msg.content)}")
                    append("}")
                    if (index < messages.size - 1) append(",")
                }
                append("],")
                append("\"temperature\": ${config.temperature},")
                append("\"max_tokens\": ${config.maxTokens},")
                append("\"stream\": true")
                append("}")
            }
            
            val request = Request.Builder()
                .url("${config.baseUrl}/chat/completions")
                .post(requestBody.toRequestBody(jsonMediaType))
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: "Erro desconhecido"
                    emit(Result.failure(AIException(AIErrorType.API_ERROR, "Erro da API: ${response.code} - $errorBody")))
                    return@flow
                }
                
                val body = response.body ?: throw AIException(AIErrorType.API_ERROR, "Resposta vazia")
                val source = body.source()
                
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)
                        if (data == "[DONE]") {
                            emit(Result.success(StreamChunk("", isComplete = true)))
                            break
                        }
                        
                        try {
                            val chunk = parseSSEChunk(data)
                            chunk?.let {
                                emit(Result.success(it))
                            }
                        } catch (e: Exception) {
                            // Ignora chunks inválidos
                        }
                    }
                }
            }
        } catch (e: SocketTimeoutException) {
            emit(Result.failure(AIException(AIErrorType.TIMEOUT, "A resposta está demorando mais que o esperado")))
        } catch (e: IOException) {
            emit(Result.failure(AIException(AIErrorType.NO_INTERNET, "Sem conexão com a internet")))
        } catch (e: Exception) {
            emit(Result.failure(AIException(AIErrorType.API_ERROR, e.message ?: "Erro desconhecido")))
        }
    }
    
    private suspend fun makeRequest(messages: List<AIMessage>, stream: Boolean): ChatResponse = withContext(Dispatchers.IO) {
        val requestBody = buildString {
            append("{")
            append("\"model\": \"${config.model.modelId}\",")
            append("\"messages\": [")
            messages.forEachIndexed { index, msg ->
                append("{")
                append("\"role\": \"${msg.role.name.lowercase()}\",")
                append("\"content\": ${escapeJson(msg.content)}")
                append("}")
                if (index < messages.size - 1) append(",")
            }
            append("],")
            append("\"temperature\": ${config.temperature},")
            append("\"max_tokens\": ${config.maxTokens},")
            append("\"stream\": $stream")
            append("}")
        }
        
        val request = Request.Builder()
            .url("${config.baseUrl}/chat/completions")
            .post(requestBody.toRequestBody(jsonMediaType))
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .build()
        
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Erro desconhecido"
                throw AIException(AIErrorType.API_ERROR, "Erro da API: ${response.code} - $errorBody")
            }
            
            val body = response.body?.string() ?: throw AIException(AIErrorType.API_ERROR, "Resposta vazia")
            parseChatResponse(body)
        }
    }
    
    private fun parseChatResponse(json: String): ChatResponse {
        // Parse simples do JSON (para evitar dependência de biblioteca extra)
        val content = json.extractJsonValue("content")
        val model = json.extractJsonValue("model")
        val id = json.extractJsonValue("id")
        
        return ChatResponse(
            id = id,
            model = model,
            content = content,
            finishReason = "stop"
        )
    }
    
    private fun parseSSEChunk(json: String): StreamChunk? {
        val content = json.extractJsonValue("content")
        val finishReason = json.extractJsonValue("finish_reason")
        
        return if (content.isNotEmpty()) {
            StreamChunk(content = content, isComplete = finishReason == "stop")
        } else null
    }
    
    private fun String.extractJsonValue(key: String): String {
        val pattern = """"$key"\s*:\s*"?([^",}\]]+)""?".toRegex()
        val match = pattern.find(this)
        return match?.groupValues?.getOrNull(1)?.trim() ?: ""
    }
    
    private fun escapeJson(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

/**
 * Tipos de erro da IA
 */
enum class AIErrorType {
    NO_INTERNET,
    TIMEOUT,
    API_ERROR,
    AUTH_ERROR,
    RATE_LIMIT,
    UNKNOWN
}

/**
 * Exceção da IA
 */
class AIException(
    val errorType: AIErrorType,
    override val message: String
) : Exception(message)
