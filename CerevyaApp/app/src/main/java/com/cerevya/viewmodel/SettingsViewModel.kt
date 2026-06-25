package com.cerevya.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val isSyncing: Boolean = false,
    val lastSyncTime: String = "Nunca"
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.themeMode.collect { themeMode ->
                _uiState.update { it.copy(currentTheme = themeMode) }
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

    class Factory(private val preferencesManager: PreferencesManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesManager) as T
        }
    }
}
