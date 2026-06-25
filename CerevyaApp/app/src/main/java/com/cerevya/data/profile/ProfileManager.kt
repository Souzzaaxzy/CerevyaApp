package com.cerevya.data.profile

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.cerevya.domain.models.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * ProfileManager - Gerencia perfil do usuário localmente
 */
class ProfileManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _userProfile = MutableStateFlow(loadProfile())
    val userProfile: StateFlow<UserEntity?> = _userProfile.asStateFlow()

    /**
     * Carrega perfil salvo
     */
    private fun loadProfile(): UserEntity? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        return UserEntity(
            userId = userId,
            name = prefs.getString(KEY_GOOGLE_NAME, "") ?: "",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            photoUrl = prefs.getString(KEY_GOOGLE_PHOTO, null),
            displayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: "",
            profilePhotoPath = prefs.getString(KEY_PROFILE_PHOTO_PATH, null),
            isProfileSetup = prefs.getBoolean(KEY_PROFILE_SETUP, false),
            createdAt = prefs.getLong(KEY_CREATED_AT, System.currentTimeMillis())
        )
    }

    /**
     * Salva perfil básico após login Google
     */
    fun saveBasicProfile(user: UserEntity) {
        prefs.edit().apply {
            putString(KEY_USER_ID, user.userId)
            putString(KEY_GOOGLE_NAME, user.name)
            putString(KEY_EMAIL, user.email)
            putString(KEY_GOOGLE_PHOTO, user.photoUrl)
            if (getLong(KEY_CREATED_AT, 0) == 0L) {
                putLong(KEY_CREATED_AT, System.currentTimeMillis())
            }
            apply()
        }
        _userProfile.value = loadProfile()
    }

    /**
     * Salva nome de exibição personalizado
     */
    fun saveDisplayName(displayName: String) {
        prefs.edit().putString(KEY_DISPLAY_NAME, displayName).apply()
        _userProfile.value = _userProfile.value?.copy(
            displayName = displayName,
            isProfileSetup = true
        )
        prefs.edit().putBoolean(KEY_PROFILE_SETUP, true).apply()
    }

    /**
     * Salva foto de perfil
     */
    suspend fun saveProfilePhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            // Salvar em arquivo local
            val profileDir = File(context.filesDir, PROFILE_DIR)
            if (!profileDir.exists()) profileDir.mkdirs()

            val photoFile = File(profileDir, "profile_photo.jpg")
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val path = photoFile.absolutePath
            prefs.edit().putString(KEY_PROFILE_PHOTO_PATH, path).apply()
            _userProfile.value = _userProfile.value?.copy(profilePhotoPath = path)
            path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Salva foto de perfil a partir de bitmap
     */
    suspend fun saveProfilePhotoFromBitmap(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            val profileDir = File(context.filesDir, PROFILE_DIR)
            if (!profileDir.exists()) profileDir.mkdirs()

            val photoFile = File(profileDir, "profile_photo.jpg")
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val path = photoFile.absolutePath
            prefs.edit().putString(KEY_PROFILE_PHOTO_PATH, path).apply()
            _userProfile.value = _userProfile.value?.copy(profilePhotoPath = path)
            path
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Finaliza configuração do perfil
     */
    fun completeProfileSetup() {
        prefs.edit().putBoolean(KEY_PROFILE_SETUP, true).apply()
        _userProfile.value = _userProfile.value?.copy(isProfileSetup = true)
    }

    /**
     * Verifica se perfil foi configurado
     */
    fun isProfileSetup(): Boolean = prefs.getBoolean(KEY_PROFILE_SETUP, false)

    /**
     * Limpa perfil (logout)
     */
    fun clearProfile() {
        // Remove foto local
        _userProfile.value?.profilePhotoPath?.let { path ->
            File(path).delete()
        }
        prefs.edit().clear().apply()
        _userProfile.value = null
    }

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

    companion object {
        private const val PREFS_NAME = "cerevya_profile"
        private const val PROFILE_DIR = "profile_photos"
        
        private const val KEY_USER_ID = "user_id"
        private const val KEY_GOOGLE_NAME = "google_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_GOOGLE_PHOTO = "google_photo"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PROFILE_PHOTO_PATH = "profile_photo_path"
        private const val KEY_PROFILE_SETUP = "profile_setup"
        private const val KEY_CREATED_AT = "created_at"
        private const val KEY_LAST_SYNC = "last_sync"
    }
}
