package com.cerevya.auth

import android.content.Context
import android.content.SharedPreferences
import com.cerevya.domain.models.UserEntity
import com.cerevya.domain.models.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AuthManager - Gerencia autenticação e sessão do usuário
 * 
 * Suporta:
 * - Google Sign-In
 * - Modo offline (guest)
 * 
 * A implementação real com Firebase será adicionada quando configurado
 */
class AuthManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<UserSession> = _session.asStateFlow()
    
    /**
     * Carrega sessão salvo
     */
    private fun loadSession(): UserSession {
        val userId = prefs.getString(KEY_USER_ID, null)
        return if (userId != null) {
            UserSession(
                user = UserEntity(
                    userId = userId,
                    name = prefs.getString(KEY_USER_NAME, "") ?: "",
                    email = prefs.getString(KEY_USER_EMAIL, "") ?: "",
                    photoUrl = prefs.getString(KEY_USER_PHOTO, null)
                ),
                isLoggedIn = true
            )
        } else {
            UserSession(user = null, isLoggedIn = false)
        }
    }
    
    /**
     * Salva sessão do usuário
     */
    private fun saveSession(user: UserEntity) {
        prefs.edit().apply {
            putString(KEY_USER_ID, user.userId)
            putString(KEY_USER_NAME, user.name)
            putString(KEY_USER_EMAIL, user.email)
            user.photoUrl?.let { putString(KEY_USER_PHOTO, it) }
            apply()
        }
        _session.value = UserSession(user = user, isLoggedIn = true)
    }
    
    /**
     * Realiza login com Google (estrutura preparada)
     * Para ativar, configurar google-services.json e dependências
     */
    suspend fun signInWithGoogle(idToken: String): Result<UserEntity> {
        return try {
            // TODO: Implementar com Firebase Auth
            // Por enquanto, simula login
            val user = UserEntity(
                userId = "google_${idToken.take(20)}",
                name = "Usuário Google",
                email = "user@gmail.com"
            )
            saveSession(user)
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Realiza logout
     */
    fun signOut() {
        prefs.edit().clear().apply()
        _session.value = UserSession(user = null, isLoggedIn = false)
    }
    
    /**
     * Retorna usuário atual
     */
    fun getCurrentUser(): UserEntity? = _session.value.user
    
    /**
     * Verifica se está logado
     */
    fun isLoggedIn(): Boolean = _session.value.isLoggedIn
    
    /**
     * Atualiza último sync
     */
    fun updateLastSync() {
        prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
    }
    
    /**
     * Obtém timestamp do último sync
     */
    fun getLastSync(): Long = prefs.getLong(KEY_LAST_SYNC, 0)
    
    /**
     * Define estado de sincronização
     */
    fun setSyncing(isSyncing: Boolean) {
        _session.value = _session.value.copy(isSyncing = isSyncing)
    }
    
    companion object {
        private const val PREFS_NAME = "cerevya_auth"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHOTO = "user_photo"
        private const val KEY_LAST_SYNC = "last_sync"
    }
}
