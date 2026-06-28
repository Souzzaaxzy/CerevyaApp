package com.cerevya.data.chat

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * ChatManager - Gerencia o sistema de chats múltiplos no Firebase Firestore
 * 
 * Estrutura Firestore: 
 * - chats/{chatId} - Documentos de conversas
 * - chats/{chatId}/messages/{messageId} - Mensagens de cada conversa
 * 
 * Cada chat contém:
 * - chatId: ID único da conversa
 * - uid: UID do usuário Firebase Auth
 * - title: Título da conversa (gerado automaticamente)
 * - createdAt: Timestamp de criação
 * - updatedAt: Timestamp da última atividade
 * - messageCount: Contagem de mensagens
 * - isActive: Indica se é a conversa ativa
 */
class ChatManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val chatsCollection = firestore.collection("chats")

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatEntity>>(emptyList())
    val chats: StateFlow<List<ChatEntity>> = _chats.asStateFlow()

    private val _activeChat = MutableStateFlow<ChatEntity?>(null)
    val activeChat: StateFlow<ChatEntity?> = _activeChat.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Referência ao listener atual para poder remover
    private var currentMessagesListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var currentChatsListener: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Escuta a lista de chats do usuário em tempo real
     */
    fun observeChats(): Flow<List<ChatEntity>> = callbackFlow {
        val user = auth.currentUser
        if (user == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        _isLoading.value = true
        
        val query = chatsCollection
            .whereEqualTo("uid", user.uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            _isLoading.value = false
            
            if (error != null) {
                _error.value = error.message
                return@addSnapshotListener
            }

            _error.value = null
            
            val chatList = snapshot?.documents?.mapNotNull { doc ->
                try {
                    doc.data?.toChatEntity()
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()

            _chats.value = chatList
            trySend(chatList)
        }

        currentChatsListener = listener
        awaitClose { listener.remove() }
    }

    /**
     * Escuta mensagens de um chat específico em tempo real
     */
    fun observeMessages(chatId: String): Flow<List<MessageEntity>> = callbackFlow {
        val user = auth.currentUser
        if (user == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        _isLoading.value = true
        
        // Nova estrutura: sub-coleção dentro do chat
        val messagesRef = chatsCollection.document(chatId).collection("messages")
        val query = messagesRef.orderBy("timestamp", Query.Direction.ASCENDING)

        val listener = query.addSnapshotListener { snapshot, error ->
            _isLoading.value = false
            
            if (error != null) {
                _error.value = error.message
                return@addSnapshotListener
            }

            _error.value = null
            
            val messageList = snapshot?.documents?.mapNotNull { doc ->
                try {
                    doc.data?.toMessageEntity()
                } catch (e: Exception) {
                    null
                }
            } ?: emptyList()

            _messages.value = messageList
            trySend(messageList)
        }

        currentMessagesListener?.remove()
        currentMessagesListener = listener
        awaitClose { listener.remove() }
    }

    /**
     * Cria um novo chat
     */
    suspend fun createChat(): ChatEntity? {
        val user = auth.currentUser ?: return null

        return try {
            _isLoading.value = true
            
            val chatId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            
            val chat = ChatEntity(
                chatId = chatId,
                uid = user.uid,
                title = "Nova conversa",
                createdAt = now,
                updatedAt = now,
                messageCount = 0,
                isActive = true
            )

            chatsCollection.document(chatId)
                .set(chat.toMap())
                .await()

            // Desativar outros chats
            deactivateOtherChats(chatId)

            _activeChat.value = chat
            _isLoading.value = false
            chat
        } catch (e: Exception) {
            _isLoading.value = false
            _error.value = e.message
            null
        }
    }

    /**
     * Cria um chat com base na primeira mensagem do usuário
     */
    suspend fun createChatWithFirstMessage(firstMessage: String): ChatEntity? {
        val user = auth.currentUser ?: return null

        return try {
            _isLoading.value = true
            
            val chatId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            
            // Gerar título a partir da primeira mensagem (limitar a 50 caracteres)
            val title = if (firstMessage.length > 50) {
                firstMessage.take(47) + "..."
            } else {
                firstMessage
            }
            
            val chat = ChatEntity(
                chatId = chatId,
                uid = user.uid,
                title = title,
                createdAt = now,
                updatedAt = now,
                messageCount = 0,
                isActive = true
            )

            chatsCollection.document(chatId)
                .set(chat.toMap())
                .await()

            // Desativar outros chats
            deactivateOtherChats(chatId)

            _activeChat.value = chat
            _isLoading.value = false
            chat
        } catch (e: Exception) {
            _isLoading.value = false
            _error.value = e.message
            null
        }
    }

    /**
     * Define o chat ativo e carrega suas mensagens
     */
    suspend fun setActiveChat(chatId: String) {
        val user = auth.currentUser ?: return

        try {
            // Buscar o chat
            val chatDoc = chatsCollection.document(chatId).get().await()
            val chat = chatDoc.data?.toChatEntity()
            
            if (chat != null) {
                // Desativar outros chats e ativar este
                deactivateOtherChats(chatId)
                
                chatsCollection.document(chatId)
                    .update("isActive", true)
                    .await()
                
                _activeChat.value = chat.copy(isActive = true)
                
                // Iniciar observação de mensagens deste chat
                observeMessages(chatId)
            }
        } catch (e: Exception) {
            _error.value = e.message
        }
    }

    /**
     * Desativa todos os outros chats
     */
    private suspend fun deactivateOtherChats(activeChatId: String) {
        val user = auth.currentUser ?: return

        try {
            // Desativar todos os chats do usuário
            val query = chatsCollection
                .whereEqualTo("uid", user.uid)
                .whereEqualTo("isActive", true)
            
            val snapshot = query.get().await()
            
            for (doc in snapshot.documents) {
                if (doc.id != activeChatId) {
                    doc.reference.update("isActive", false).await()
                }
            }
        } catch (e: Exception) {
            // Ignorar erros de atualização
        }
    }

    /**
     * Atualiza o título de um chat
     */
    suspend fun updateChatTitle(chatId: String, title: String) {
        try {
            chatsCollection.document(chatId)
                .update("title", title)
                .await()
        } catch (e: Exception) {
            _error.value = e.message
        }
    }

    /**
     * Envia uma mensagem no chat ativo
     * Se não houver chat ativo, cria um novo
     */
    suspend fun sendMessage(text: String): Boolean {
        val user = auth.currentUser ?: return false
        
        if (text.isBlank()) return false

        // Garantir que existe um chat ativo
        var chat = _activeChat.value
        if (chat == null) {
            chat = createChatWithFirstMessage(text)
            if (chat == null) return false
        }

        return try {
            _isLoading.value = true
            
            val message = MessageEntity(
                messageId = UUID.randomUUID().toString(),
                chatId = chat.chatId,
                uid = user.uid,
                text = text.trim(),
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis(),
                userName = user.displayName ?: "Usuário",
                userPhotoUrl = user.photoUrl?.toString() ?: ""
            )

            // Salvar na sub-coleção do chat
            chatsCollection.document(chat.chatId)
                .collection("messages")
                .document(message.messageId)
                .set(message.toMap())
                .await()

            // Atualizar timestamp e contagem do chat
            chatsCollection.document(chat.chatId)
                .update(
                    mapOf(
                        "updatedAt" to System.currentTimeMillis(),
                        "messageCount" to (chat.messageCount + 1)
                    )
                )
                .await()

            _isLoading.value = false
            true
        } catch (e: Exception) {
            _isLoading.value = false
            _error.value = e.message
            false
        }
    }

    /**
     * Salva mensagem da IA no chat ativo
     */
    suspend fun saveAssistantMessage(text: String): Boolean {
        val user = auth.currentUser ?: return false
        val chat = _activeChat.value ?: return false
        
        if (text.isBlank()) return false

        return try {
            val message = MessageEntity(
                messageId = UUID.randomUUID().toString(),
                chatId = chat.chatId,
                uid = user.uid,
                text = text,
                role = MessageRole.ASSISTANT,
                timestamp = System.currentTimeMillis(),
                userName = "Cerevya",
                userPhotoUrl = ""
            )

            // Salvar na sub-coleção do chat
            chatsCollection.document(chat.chatId)
                .collection("messages")
                .document(message.messageId)
                .set(message.toMap())
                .await()

            // Atualizar timestamp do chat
            chatsCollection.document(chat.chatId)
                .update("updatedAt", System.currentTimeMillis())
                .await()

            true
        } catch (e: Exception) {
            _error.value = e.message
            false
        }
    }

    /**
     * Carrega mensagens de um chat específico (uma única vez)
     */
    suspend fun loadMessages(chatId: String): List<MessageEntity> {
        val user = auth.currentUser ?: return emptyList()

        return try {
            _isLoading.value = true
            
            val messagesRef = chatsCollection.document(chatId).collection("messages")
            val query = messagesRef.orderBy("timestamp", Query.Direction.ASCENDING)
            
            val snapshot = query.get().await()
            
            _isLoading.value = false
            
            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.data?.toMessageEntity()
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            _isLoading.value = false
            _error.value = e.message
            emptyList()
        }
    }

    /**
     * Apaga um chat e todas as suas mensagens
     */
    suspend fun deleteChat(chatId: String): Boolean {
        val user = auth.currentUser ?: return false

        return try {
            // Primeiro, deletar todas as mensagens do chat
            val messagesRef = chatsCollection.document(chatId).collection("messages")
            val messagesSnapshot = messagesRef.get().await()
            
            for (doc in messagesSnapshot.documents) {
                doc.reference.delete().await()
            }
            
            // Depois, deletar o chat
            chatsCollection.document(chatId).delete().await()
            
            // Se era o chat ativo, limpar
            if (_activeChat.value?.chatId == chatId) {
                _activeChat.value = null
                _messages.value = emptyList()
            }
            
            true
        } catch (e: Exception) {
            _error.value = e.message
            false
        }
    }

    /**
     * Limpa mensagens locais e chat ativo (ao deslogar)
     */
    fun clearMessages() {
        _messages.value = emptyList()
        _activeChat.value = null
        currentMessagesListener?.remove()
        currentChatsListener?.remove()
    }

    /**
     * Apaga mensagem pelo ID
     */
    suspend fun deleteMessage(messageId: String): Boolean {
        val chat = _activeChat.value ?: return false
        
        return try {
            chatsCollection.document(chat.chatId)
                .collection("messages")
                .document(messageId)
                .delete()
                .await()
            true
        } catch (e: Exception) {
            _error.value = e.message
            false
        }
    }

    /**
     * Apaga todas as mensagens do chat ativo
     */
    suspend fun clearAllMessages(): Boolean {
        val chat = _activeChat.value ?: return false

        return try {
            val messagesRef = chatsCollection.document(chat.chatId).collection("messages")
            val snapshot = messagesRef.get().await()
            
            for (doc in snapshot.documents) {
                doc.reference.delete().await()
            }
            
            clearMessages()
            true
        } catch (e: Exception) {
            _error.value = e.message
            false
        }
    }

    /**
     * Verifica se há usuário logado
     */
    fun isLoggedIn(): Boolean = auth.currentUser != null

    /**
     * Obtém usuário atual
     */
    fun getCurrentUser() = auth.currentUser
}
