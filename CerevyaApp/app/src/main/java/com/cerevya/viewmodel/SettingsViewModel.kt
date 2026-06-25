package com.cerevya.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cerevya.auth.FirebaseAuthManager
import com.cerevya.data.preferences.PreferencesManager
import com.cerevya.data.preferences.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val appName: String = "Cerevya",
    val appVersion: String = "1.0.0",
    val appDescription: String = "Seu segundo cérebro digital",
    val currentTheme: ThemeMode = ThemeMode.SYSTEM,
    val showThemeDialog: Boolean = false,
    val isLoggedIn: Boolean = false,
    val userName: String = "",
    val userEmail: String = "",
    val userPhotoUrl: String? = null,
    val isSyncing: Boolean = false,
    val lastSyncTime: String = "Nunca",
    val isSigningIn: Boolean = false,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val authManager: FirebaseAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.themeMode.collect { themeMode ->
                _uiState.update { it.copy(currentTheme = themeMode) }
            }
        }
        
        viewModelScope.launch {
            authManager.session.collect { session ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = session.isLoggedIn,
                        userName = session.user?.name ?: "",
                        userEmail = session.user?.email ?: "",
                        userPhotoUrl = session.user?.photoUrl,
                        isSyncing = session.isSyncing
                    )
                }
            }
        }
        
        // Update initial state
        updateAuthState()
    }
    
    private fun updateAuthState() {
        val user = authManager.getCurrentUser()
        if (user != null) {
            _uiState.update {
                it.copy(
                    isLoggedIn = true,
                    userName = user.displayName ?: "",
                    userEmail = user.email ?: "",
                    userPhotoUrl = user.photoUrl?.toString()
                )
            }
        }
    }

    fun showThemeDialog() {
        _uiState.update { it.copy(showThemeDialog = true) }
    }

    fun hideThemeDialog() {
        _uiState.update { it.copy(showThemeDialog = false) }
    }

    fun setTheme(themeMode: ThemeMode) {
        preferencesManager.setThemeMode(themeMode)
        hideThemeDialog()
    }

    fun getThemeDisplayName(themeMode: ThemeMode): String {
        return when (themeMode) {
            ThemeMode.LIGHT -> "Claro"
            ThemeMode.DARK -> "Escuro"
            ThemeMode.SYSTEM -> "Automático"
        }
    }
    
    fun signInWithGoogle(activity: Activity) {
        _uiState.update { it.copy(isSigningIn = true, errorMessage = null) }
        
        authManager.signInWithGoogle(activity) { result ->
            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            isSigningIn = false,
                            isLoggedIn = true,
                            userName = user.name,
                            userEmail = user.email,
                            userPhotoUrl = user.photoUrl
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSigningIn = false,
                            errorMessage = error.message ?: "Erro ao fazer login"
                        )
                    }
                }
            )
        }
    }
    
    fun signOut() {
        authManager.signOut()
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                userName = "",
                userEmail = "",
                userPhotoUrl = null
            )
        }
    }
    
    fun handleGoogleSignInResult(data: android.content.Intent?) {
        authManager.handleGoogleSignInResult(data) { result ->
            result.fold(
                onSuccess = { user ->
                    _uiState.update {
                        it.copy(
                            isSigningIn = false,
                            isLoggedIn = true,
                            userName = user.name,
                            userEmail = user.email,
                            userPhotoUrl = user.photoUrl
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSigningIn = false,
                            errorMessage = error.message ?: "Erro ao fazer login"
                        )
                    }
                }
            )
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val preferencesManager: PreferencesManager,
        private val authManager: FirebaseAuthManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesManager, authManager) as T
        }
    }
}
