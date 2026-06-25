package com.cerevya

import android.app.Application
import com.cerevya.auth.AuthManager
import com.cerevya.cloud.CloudMemoryManager
import com.cerevya.data.database.CerevyaDatabase
import com.cerevya.data.preferences.PreferencesManager
import com.cerevya.data.repository.MemoryRepository
import com.cerevya.sync.SyncManager

class CerevyaApplication : Application() {

    val database: CerevyaDatabase by lazy { CerevyaDatabase.getDatabase(this) }
    val memoryRepository: MemoryRepository by lazy { MemoryRepository(database.memoryDao()) }
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }
    
    // Cloud and sync
    val authManager: AuthManager by lazy { AuthManager(this) }
    val cloudMemoryManager: CloudMemoryManager by lazy { CloudMemoryManager() }
    val syncManager: SyncManager by lazy { 
        SyncManager(memoryRepository, cloudMemoryManager, authManager) 
    }
}
