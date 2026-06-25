package com.cerevya.domain.models

/**
 * UserEntity - Modelo de usuário
 */
data class UserEntity(
    val userId: String,
    val name: String,
    val email: String,
    val photoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSync: Long = System.currentTimeMillis()
)

/**
 * UserSession - Sessão atual do usuário
 */
data class UserSession(
    val user: UserEntity?,
    val isLoggedIn: Boolean,
    val isSyncing: Boolean = false
)
