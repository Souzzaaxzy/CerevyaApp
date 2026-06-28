package com.cerevya

import android.app.Application
import com.cerevya.ai.AIChatManager
import com.cerevya.ai.AIConfig
import com.cerevya.ai.AIModel
import com.cerevya.ai.AIProvider
import com.cerevya.ai.AIService
import com.cerevya.auth.FirebaseAuthManager
import com.cerevya.cloud.CloudMemoryManager
import com.cerevya.data.chat.ChatManager
import com.cerevya.data.chat.ChatRepository
import com.cerevya.data.database.CerevyaDatabase
import com.cerevya.data.firestore.FirestoreUserManager
import com.cerevya.data.preferences.PreferencesManager
import com.cerevya.data.repository.MemoryRepository
import com.cerevya.sync.SyncManager

class CerevyaApplication : Application() {

    val database: CerevyaDatabase by lazy { CerevyaDatabase.getDatabase(this) }
    val memoryRepository: MemoryRepository by lazy { MemoryRepository(database.memoryDao()) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
    
    // Firebase Authentication
    val firebaseAuthManager: FirebaseAuthManager by lazy { FirebaseAuthManager(this) }
    
    // Firestore User Manager (única fonte de dados do usuário)
    val firestoreUserManager: FirestoreUserManager by lazy { FirestoreUserManager(this) }
    
    // Chat Manager (Firestore) - para sincronização futura
    val chatManager: ChatManager by lazy { ChatManager(this) }
    
    // Chat Repository (Room) - para funcionamento local/offline
    val chatRepository: ChatRepository by lazy { ChatRepository(this) }
    
    // AI Service - Groq API (API Key via BuildConfig - secrets.properties)
    val aiService: AIService by lazy {
        val config = AIConfig(
            provider = AIProvider.GROQ,
            model = AIModel.LLAMA_3_3_70B,
            apiKey = BuildConfig.GROQ_API_KEY,
            baseUrl = "https://api.groq.com/openai/v1",
            maxTokens = 4096,
            temperature = 0.7f,
            streamingEnabled = true
        )
        AIService(config)
    }
    
    // AI Chat Manager - Coordena chat com IA
    val aiChatManager: AIChatManager by lazy {
        AIChatManager(chatRepository, aiService)
    }
    
    // Cloud and sync (prepared for when Firebase is fully configured)
    val cloudMemoryManager: CloudMemoryManager by lazy { CloudMemoryManager() }
    val syncManager: SyncManager by lazy { 
        SyncManager(memoryRepository, cloudMemoryManager, firebaseAuthManager) 
    }
}
