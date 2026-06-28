package com.cerevya.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.cerevya.domain.models.MemoryEntity

@Database(
    entities = [MemoryEntity::class, ChatEntity::class, ChatMessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class CerevyaDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: CerevyaDatabase? = null

        fun getDatabase(context: Context): CerevyaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CerevyaDatabase::class.java,
                    "cerevya_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
