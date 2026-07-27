package com.cerevya.data.chat

import com.cerevya.domain.models.ChatMessageRole

/**
 * MessageEntity - Modelo de mensagem para o chat
 * 
 * Estrutura Firestore: chats/{chatId}/messages/{messageId}
 */
data class MessageEntity(
    val messageId: String = "",
    val chatId: String = "",
    val uid: String = "",
    val text: String = "",
    val role: ChatMessageRole = ChatMessageRole.USER,
    val timestamp: Long = System.currentTimeMillis(),
    val userName: String = "",
    val userPhotoUrl: String = ""
)

/**
 * Extension para converter de/para Map (Firestore)
 */
fun MessageEntity.toMap(): Map<String, Any?> = mapOf(
    "messageId" to messageId,
    "chatId" to chatId,
    "uid" to uid,
    "text" to text,
    "role" to role.name,
    "timestamp" to timestamp,
    "userName" to userName,
    "userPhotoUrl" to userPhotoUrl
)

fun Map<String, Any?>.toMessageEntity(): MessageEntity {
    return MessageEntity(
        messageId = this["messageId"] as? String ?: "",
        chatId = this["chatId"] as? String ?: "",
        uid = this["uid"] as? String ?: "",
        text = this["text"] as? String ?: "",
        role = try {
            ChatMessageRole.valueOf(this["role"] as? String ?: "USER")
        } catch (e: Exception) {
            ChatMessageRole.USER
        },
        timestamp = this["timestamp"] as? Long ?: System.currentTimeMillis(),
        userName = this["userName"] as? String ?: "",
        userPhotoUrl = this["userPhotoUrl"] as? String ?: ""
    )
}
