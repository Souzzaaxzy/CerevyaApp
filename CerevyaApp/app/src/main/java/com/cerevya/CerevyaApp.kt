package com.cerevya

import android.app.Application
import com.cerevya.data.database.CerevyaDatabase
import com.cerevya.data.repository.MemoryRepository

class CerevyaApplication : Application() {

    val database: CerevyaDatabase by lazy { CerevyaDatabase.getDatabase(this) }
    val memoryRepository: MemoryRepository by lazy { MemoryRepository(database.memoryDao()) }
}
