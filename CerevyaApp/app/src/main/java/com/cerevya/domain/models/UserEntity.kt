package com.cerevya.domain.models

/**
 * UserEntity - Modelo de usuário
 */
data class UserEntity(
    val userId: String,
    val name: String = "",           // Nome do Google
    val email: String = "",
    val photoUrl: String? = null,    // URL do Google
    val displayName: String = "",    // Nome personalizado chosen by user
    val profilePhotoPath: String? = null, // Caminho local da foto
    val isProfileSetup: Boolean = false, // Se o perfil foi configurado
    val createdAt: Long = System.currentTimeMillis(),
    val lastSync: Long = System.currentTimeMillis()
) {
    fun getEffectiveName(): String = displayName.ifEmpty { name }
}

/**
 * UserSession - Sessão atual do usuário
 */
data class UserSession(
    val user: UserEntity?,
    val isLoggedIn: Boolean,
    val isSyncing: Boolean = false
)
