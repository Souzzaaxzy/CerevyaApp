package com.cerevya.data.chat

/**
 * ChatEntity - Modelo para representar uma conversa no sistema de chats múltiplos
 * 
 * Estrutura Firestore: chats/{chatId}
 * Sub-coleção: chats/{chatId}/messages/{messageId}
 */
data class ChatEntity(
    val chatId: String = "",
    val uid: String = "",
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val isActive: Boolean = false
)

/**
 * Extension para converter de/para Map (Firestore)
 */
fun ChatEntity.toMap(): Map<String, Any?> = mapOf(
    "chatId" to chatId,
    "uid" to uid,
    "title" to title,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "messageCount" to messageCount,
    "isActive" to isActive
)

fun Map<String, Any?>.toChatEntity(): ChatEntity {
    return ChatEntity(
        chatId = this["chatId"] as? String ?: "",
        uid = this["uid"] as? String ?: "",
        title = this["title"] as? String ?: "",
        createdAt = this["createdAt"] as? Long ?: System.currentTimeMillis(),
        updatedAt = this["updatedAt"] as? Long ?: System.currentTimeMillis(),
        messageCount = (this["messageCount"] as? Number)?.toInt() ?: 0,
        isActive = this["isActive"] as? Boolean ?: false
    )
}
