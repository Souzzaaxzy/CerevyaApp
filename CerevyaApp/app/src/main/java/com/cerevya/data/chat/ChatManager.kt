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
 * ChatManager - Gerencia o chat no Firebase Firestore
 * 
 * Estrutura Firestore: messages/{messageId}
 * 
 * Cada mensagem contém:
 * - messageId: ID único da mensagem
 * - chatId: ID do chat (um único chat por usuário)
 * - uid: UID do usuário Firebase Auth
 * - text: Texto da mensagem
 * - role: USER | ASSISTANT | SYSTEM
 * - timestamp: Timestamp da mensagem
 * - userName: Nome do usuário (para exibição)
 * - userPhotoUrl: URL da foto do usuário
 */
class ChatManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val messagesCollection = firestore.collection("messages")

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Chat ID fixo para o usuário (um chat por usuário)
    private val userChatId: String
        get() = auth.currentUser?.uid ?: "anonymous"

    /**
     * Escuta mensagens em tempo real do Firestore
     */
    fun observeMessages(): Flow<List<MessageEntity>> = callbackFlow {
        val user = auth.currentUser
        if (user == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        _isLoading.value = true
        
        val query = messagesCollection
            .whereEqualTo("uid", user.uid)
            .orderBy("timestamp", Query.Direction.ASCENDING)

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

        awaitClose { listener.remove() }
    }

    /**
     * Envia uma mensagem do usuário
     */
    suspend fun sendMessage(text: String): Boolean {
        val user = auth.currentUser ?: return false
        
        if (text.isBlank()) return false

        return try {
            _isLoading.value = true
            
            val message = MessageEntity(
                messageId = UUID.randomUUID().toString(),
                chatId = userChatId,
                uid = user.uid,
                text = text.trim(),
                role = MessageRole.USER,
                timestamp = System.currentTimeMillis(),
                userName = user.displayName ?: "Usuário",
                userPhotoUrl = user.photoUrl?.toString() ?: ""
            )

            messagesCollection.document(message.messageId)
                .set(message.toMap())
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
     * Adiciona mensagem do sistema (sem enviar para o Firestore)
     * Usado para mensagens internas do app
     */
    fun addSystemMessage(text: String) {
        val user = auth.currentUser ?: return
        
        val systemMessage = MessageEntity(
            messageId = UUID.randomUUID().toString(),
            chatId = userChatId,
            uid = user.uid,
            text = text,
            role = MessageRole.SYSTEM,
            timestamp = System.currentTimeMillis(),
            userName = "Sistema",
            userPhotoUrl = ""
        )
        
        // Adiciona localmente (não salva no Firestore)
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(systemMessage)
        _messages.value = currentMessages
    }

    /**
     * Salva mensagem da IA (futuro)
     */
    suspend fun saveAssistantMessage(text: String): Boolean {
        val user = auth.currentUser ?: return false
        
        if (text.isBlank()) return false

        return try {
            val message = MessageEntity(
                messageId = UUID.randomUUID().toString(),
                chatId = userChatId,
                uid = user.uid,
                text = text,
                role = MessageRole.ASSISTANT,
                timestamp = System.currentTimeMillis(),
                userName = "Cerevya",
                userPhotoUrl = ""
            )

            messagesCollection.document(message.messageId)
                .set(message.toMap())
                .await()

            true
        } catch (e: Exception) {
            _error.value = e.message
            false
        }
    }

    /**
     * Carrega mensagens (uma única vez)
     */
    suspend fun loadMessages(): List<MessageEntity> {
        val user = auth.currentUser ?: return emptyList()

        return try {
            _isLoading.value = true
            
            val query = messagesCollection
                .whereEqualTo("uid", user.uid)
                .orderBy("timestamp", Query.Direction.ASCENDING)
            
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
     * Limpa mensagens locais (ao deslogar)
     */
    fun clearMessages() {
        _messages.value = emptyList()
    }

    /**
     * Apaga mensagem pelo ID
     */
    suspend fun deleteMessage(messageId: String): Boolean {
        return try {
            messagesCollection.document(messageId).delete().await()
            true
        } catch (e: Exception) {
            _error.value = e.message
            false
        }
    }

    /**
     * Apaga todas as mensagens do usuário
     */
    suspend fun clearAllMessages(): Boolean {
        val user = auth.currentUser ?: return false

        return try {
            val messages = loadMessages()
            messages.forEach { message ->
                messagesCollection.document(message.messageId).delete().await()
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
