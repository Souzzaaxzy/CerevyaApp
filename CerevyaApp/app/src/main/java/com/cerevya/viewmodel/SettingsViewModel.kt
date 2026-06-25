package com.cerevya.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cerevya.auth.FirebaseAuthManager
import com.cerevya.data.firestore.FirestoreUserManager
import com.cerevya.data.preferences.PreferencesManager
import com.cerevya.data.preferences.ThemeMode
import com.cerevya.domain.models.UserEntity
import com.google.firebase.auth.FirebaseAuth
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
    val errorMessage: String? = null,
    val showLogoutDialog: Boolean = false
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val authManager: FirebaseAuthManager,
    private val firestoreUserManager: FirestoreUserManager? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.themeMode.collect { themeMode ->
                _uiState.update { it.copy(currentTheme = themeMode) }
            }
        }
        
        // Collect from FirestoreUserManager if available
        firestoreUserManager?.let { fm ->
            viewModelScope.launch {
                fm.currentUser.collect { user ->
                    user?.let {
                        _uiState.update { state ->
                            state.copy(
                                isLoggedIn = true,
                                userName = it.getEffectiveName(),
                                userEmail = FirebaseAuth.getInstance().currentUser?.email ?: it.email,
                                userPhotoUrl = it.profilePhotoPath ?: it.photoUrl
                            )
                        }
                    } ?: run {
                        // No user in Firestore
                        val firebaseUser = FirebaseAuth.getInstance().currentUser
                        if (firebaseUser != null) {
                            _uiState.update { state ->
                                state.copy(
                                    isLoggedIn = true,
                                    userName = firebaseUser.displayName ?: "",
                                    userEmail = firebaseUser.email ?: "",
                                    userPhotoUrl = firebaseUser.photoUrl?.toString()
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Listen to Firebase Auth state changes
        val authListener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null) {
                _uiState.update { state ->
                    state.copy(
                        isLoggedIn = true,
                        userEmail = user.email ?: state.userEmail,
                        userPhotoUrl = user.photoUrl?.toString() ?: state.userPhotoUrl
                    )
                }
            }
        }
        FirebaseAuth.getInstance().addAuthStateListener(authListener)
        
        // Update initial state
        updateAuthState()
    }
    
    private fun updateAuthState() {
        val user = authManager.getCurrentUser()
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (user != null || firebaseUser != null) {
            _uiState.update {
                it.copy(
                    isLoggedIn = true,
                    userName = firebaseUser?.displayName ?: user?.displayName ?: "",
                    userEmail = firebaseUser?.email ?: user?.email ?: "",
                    userPhotoUrl = firebaseUser?.photoUrl?.toString() ?: user?.photoUrl ?: ""
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
        // Sign-in is handled in MainActivity
        _uiState.update { it.copy(isSigningIn = true, errorMessage = null) }
    }
    
    fun showLogoutDialog() {
        _uiState.update { it.copy(showLogoutDialog = true) }
    }
    
    fun hideLogoutDialog() {
        _uiState.update { it.copy(showLogoutDialog = false) }
    }
    
    fun signOut() {
        // Only disconnect Firestore manager, don't delete data
        firestoreUserManager?.disconnect()
        // Sign out from Firebase (caller should handle navigation)
        _uiState.update {
            it.copy(
                isLoggedIn = false,
                userName = "",
                userEmail = "",
                userPhotoUrl = null,
                showLogoutDialog = false
            )
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val preferencesManager: PreferencesManager,
        private val authManager: FirebaseAuthManager,
        private val firestoreUserManager: FirestoreUserManager? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesManager, authManager, firestoreUserManager) as T
        }
    }
}
