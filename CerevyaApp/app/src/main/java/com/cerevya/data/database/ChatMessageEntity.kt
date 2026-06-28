package com.cerevya.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["chatId"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chatId")]
)
data class ChatMessageEntity(
    @PrimaryKey
    val messageId: String,
    val chatId: String,
    val text: String,
    val role: String, // USER, ASSISTANT, SYSTEM
    val timestamp: Long,
    val userName: String,
    val userPhotoUrl: String
)
