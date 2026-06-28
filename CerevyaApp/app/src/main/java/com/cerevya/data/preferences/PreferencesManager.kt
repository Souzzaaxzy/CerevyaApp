package com.cerevya.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFS_NAME = "cerevya_prefs"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_API_KEY = "groq_api_key"
    }
    
    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    
    fun getThemeMode(): ThemeMode {
        val ordinal = prefs.getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.ordinal)
        return ThemeMode.entries.getOrElse(ordinal) { ThemeMode.SYSTEM }
    }
    
    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode.ordinal).apply()
        _themeMode.value = mode
    }
    
    // API Key management
    fun getApiKey(): String? {
        return prefs.getString(KEY_API_KEY, null)
    }
    
    fun setApiKey(apiKey: String) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply()
    }
    
    fun hasApiKey(): Boolean {
        return getApiKey()?.isNotBlank() == true
    }
    
    fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }
}
