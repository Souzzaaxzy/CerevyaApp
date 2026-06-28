package com.cerevya.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // Chat operations
    @Query("SELECT * FROM chats ORDER BY updatedAt DESC")
    fun getAllChats(): Flow<List<ChatEntity>>
    
    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChatById(chatId: String): ChatEntity?
    
    @Query("SELECT * FROM chats WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveChat(): ChatEntity?
    
    @Query("SELECT * FROM chats WHERE isActive = 1 LIMIT 1")
    fun getActiveChatFlow(): Flow<ChatEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)
    
    @Update
    suspend fun updateChat(chat: ChatEntity)
    
    @Delete
    suspend fun deleteChat(chat: ChatEntity)
    
    @Query("UPDATE chats SET isActive = 0 WHERE chatId != :chatId")
    suspend fun deactivateOtherChats(chatId: String)
    
    @Query("UPDATE chats SET isActive = 1 WHERE chatId = :chatId")
    suspend fun activateChat(chatId: String)
    
    @Query("UPDATE chats SET title = :title, updatedAt = :updatedAt WHERE chatId = :chatId")
    suspend fun updateChatTitle(chatId: String, title: String, updatedAt: Long)
    
    @Query("UPDATE chats SET updatedAt = :updatedAt, messageCount = messageCount + 1 WHERE chatId = :chatId")
    suspend fun incrementMessageCount(chatId: String, updatedAt: Long)
    
    // Message operations
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesForChat(chatId: String): Flow<List<ChatMessageEntity>>
    
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesForChatSync(chatId: String): List<ChatMessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)
    
    @Delete
    suspend fun deleteMessage(message: ChatMessageEntity)
    
    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: String)
    
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(chatId: String): ChatMessageEntity?
}
