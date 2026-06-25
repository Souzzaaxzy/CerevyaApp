package com.cerevya

import android.app.Application
import com.cerevya.auth.FirebaseAuthManager
import com.cerevya.cloud.CloudMemoryManager
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
    
    // Cloud and sync (prepared for when Firebase is fully configured)
    val cloudMemoryManager: CloudMemoryManager by lazy { CloudMemoryManager() }
    val syncManager: SyncManager by lazy { 
        SyncManager(memoryRepository, cloudMemoryManager, firebaseAuthManager) 
    }
}
