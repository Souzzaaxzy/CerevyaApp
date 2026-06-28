package com.cerevya.data.chat

import android.content.Context
import com.cerevya.data.database.ChatDao
import com.cerevya.data.database.ChatEntity
import com.cerevya.data.database.ChatMessageEntity
import com.cerevya.data.database.CerevyaDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatRepository(context: Context) {
    
    private val chatDao: ChatDao = CerevyaDatabase.getDatabase(context).chatDao()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    private val _chats = MutableStateFlow<List<ChatEntity>>(emptyList())
    val chats: StateFlow<List<ChatEntity>> = _chats.asStateFlow()
    
    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()
    
    private val _activeChat = MutableStateFlow<ChatEntity?>(null)
    val activeChat: StateFlow<ChatEntity?> = _activeChat.asStateFlow()
    
    init {
        loadChats()
    }
    
    private fun loadChats() {
        scope.launch {
            chatDao.getAllChats().collect { chatList ->
                _chats.value = chatList
            }
        }
    }
    
    fun observeMessages(chatId: String) {
        scope.launch {
            chatDao.getMessagesForChat(chatId).collect { messageList ->
                _messages.value = messageList.map { it.toMessageEntity() }
            }
        }
    }
    
    /**
     * Creates a new empty chat
     * Always starts fresh - never loads previous chat
     */
    suspend fun createNewChat(): ChatEntity = withContext(Dispatchers.IO) {
        // Clear any active chat state first
        _activeChat.value = null
        _messages.value = emptyList()
        
        val chatId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val chat = ChatEntity(
            chatId = chatId,
            title = "Nova conversa",
            createdAt = now,
            updatedAt = now,
            messageCount = 0,
            isActive = true
        )
        
        chatDao.insertChat(chat)
        chatDao.deactivateOtherChats(chatId)
        
        _activeChat.value = chat
        
        chat
    }
    
    /**
     * Backward compatibility alias
     */
    suspend fun createChat(): ChatEntity = createNewChat()
    
    /**
     * Opens an existing chat by ID
     */
    suspend fun openChat(chatId: String) = withContext(Dispatchers.IO) {
        val chat = chatDao.getChatById(chatId)
        if (chat != null) {
            chatDao.activateChat(chatId)
            _activeChat.value = chat.copy(isActive = true)
            
            // Load messages
            val msgs = chatDao.getMessagesForChatSync(chatId)
            _messages.value = msgs.map { it.toMessageEntity() }
        }
    }
    
    /**
     * Backward compatibility alias
     */
    suspend fun setActiveChat(chatId: String) = openChat(chatId)
    
    /**
     * Initializes a fresh chat when app opens
     * Does NOT restore previous chat
     */
    suspend fun initializeFreshChat(): ChatEntity = withContext(Dispatchers.IO) {
        // Always create new empty chat on app start
        createNewChat()
    }
    
    suspend fun sendMessage(text: String, userName: String = "Usuário"): Boolean = withContext(Dispatchers.IO) {
        var chat = _activeChat.value
        if (chat == null) {
            chat = createNewChat()
        }
        
        val messageId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val message = ChatMessageEntity(
            messageId = messageId,
            chatId = chat.chatId,
            text = text,
            role = MessageRole.USER.name,
            timestamp = now,
            userName = userName,
            userPhotoUrl = ""
        )
        
        chatDao.insertMessage(message)
        chatDao.incrementMessageCount(chat.chatId, now)
        
        // Update chat title if first message
        if (chat.title == "Nova conversa") {
            val title = if (text.length > 50) text.take(47) + "..." else text
            chatDao.updateChatTitle(chat.chatId, title, now)
            _activeChat.value = chat.copy(title = title, updatedAt = now, messageCount = 1)
        } else {
            _activeChat.value = chat.copy(updatedAt = now, messageCount = chat.messageCount + 1)
        }
        
        true
    }
    
    suspend fun saveAssistantMessage(text: String): Boolean = withContext(Dispatchers.IO) {
        val chat = _activeChat.value ?: return@withContext false
        
        val messageId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val message = ChatMessageEntity(
            messageId = messageId,
            chatId = chat.chatId,
            text = text,
            role = MessageRole.ASSISTANT.name,
            timestamp = now,
            userName = "Cerevya",
            userPhotoUrl = ""
        )
        
        chatDao.insertMessage(message)
        chatDao.incrementMessageCount(chat.chatId, now)
        
        _activeChat.value = chat.copy(updatedAt = now, messageCount = chat.messageCount + 1)
        
        true
    }
    
    suspend fun updateChatTitle(chatId: String, title: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        chatDao.updateChatTitle(chatId, title, now)
        
        if (_activeChat.value?.chatId == chatId) {
            _activeChat.value = _activeChat.value?.copy(title = title, updatedAt = now)
        }
    }
    
    suspend fun deleteChat(chatId: String) = withContext(Dispatchers.IO) {
        val chat = chatDao.getChatById(chatId)
        if (chat != null) {
            chatDao.deleteChat(chat)
            
            if (_activeChat.value?.chatId == chatId) {
                _activeChat.value = null
                _messages.value = emptyList()
            }
        }
    }
    
    fun clearActiveChat() {
        _activeChat.value = null
        _messages.value = emptyList()
    }
    
    private fun ChatMessageEntity.toMessageEntity(): MessageEntity {
        return MessageEntity(
            messageId = this.messageId,
            chatId = this.chatId,
            uid = "",
            text = this.text,
            role = MessageRole.valueOf(this.role),
            timestamp = this.timestamp,
            userName = this.userName,
            userPhotoUrl = this.userPhotoUrl
        )
    }
}
