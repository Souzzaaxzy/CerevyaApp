package com.cerevya.domain.models

/**
 * UserEntity - Modelo de usuário
 */
data class UserEntity(
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val displayName: String = "",
    val profilePhotoPath: String? = null,
    val isProfileSetup: Boolean = false,
    val hasCompletedSetup: Boolean = false,
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
