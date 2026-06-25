package com.cerevya.data.firestore

import android.content.Context
import android.net.Uri
import com.cerevya.domain.models.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

/**
 * FirestoreUserManager - Gerencia dados do usuário no Firebase Firestore
 * 
 * Firestore Collection: users/{uid}
 * 
 * Campos do documento:
 * - uid: String (Firebase Auth UID)
 * - name: String
 * - email: String
 * - photoUrl: String
 * - createdAt: Long (timestamp)
 * - hasCompletedSetup: Boolean
 */
class FirestoreUserManager(private val context: Context) {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _needsSetup = MutableStateFlow(false)
    val needsSetup: StateFlow<Boolean> = _needsSetup.asStateFlow()

    private val usersCollection = firestore.collection("users")

    /**
     * Escuta alterações em tempo real do usuário logado
     */
    fun observeUser(): Flow<UserEntity?> = callbackFlow {
        val user = auth.currentUser
        if (user == null) {
            _currentUser.value = null
            trySend(null)
            close()
            return@callbackFlow
        }

        val docRef = usersCollection.document(user.uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(null)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val userEntity = snapshot.toUserEntity()
                _currentUser.value = userEntity
                _needsSetup.value = userEntity?.hasCompletedSetup != true
                trySend(userEntity)
            } else {
                // Documento não existe, precisa criar
                _needsSetup.value = true
                trySend(null)
            }
        }

        awaitClose { listener.remove() }
    }

    /**
     * Busca usuário do Firestore (uma única vez)
     */
    suspend fun getUser(): UserEntity? {
        val user = auth.currentUser ?: return null
        
        try {
            _isLoading.value = true
            val docRef = usersCollection.document(user.uid)
            val snapshot = docRef.get().await()
            
            return if (snapshot.exists()) {
                snapshot.toUserEntity()
            } else {
                null
            }
        } catch (e: Exception) {
            return null
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Cria documento inicial do usuário no Firestore
     */
    suspend fun createUserIfNotExists(): UserEntity? {
        val googleUser = auth.currentUser ?: return null
        
        try {
            _isLoading.value = true
            val docRef = usersCollection.document(googleUser.uid)
            val snapshot = docRef.get().await()
            
            if (!snapshot.exists()) {
                val newUser = UserEntity(
                    userId = googleUser.uid,
                    name = googleUser.displayName ?: "",
                    email = googleUser.email ?: "",
                    photoUrl = googleUser.photoUrl?.toString() ?: "",
                    createdAt = System.currentTimeMillis(),
                    hasCompletedSetup = false,
                    isProfileSetup = false
                )
                
                docRef.set(newUser.toMap()).await()
                _currentUser.value = newUser
                _needsSetup.value = true
                return newUser
            } else {
                val userEntity = snapshot.toUserEntity()
                _currentUser.value = userEntity
                _needsSetup.value = userEntity?.hasCompletedSetup != true
                return userEntity
            }
        } catch (e: Exception) {
            return null
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Salva nome de exibição no Firestore
     */
    suspend fun saveDisplayName(name: String): Boolean {
        val user = auth.currentUser ?: return false
        
        return try {
            val updates = mapOf(
                "name" to name,
                "hasCompletedSetup" to true,
                "isProfileSetup" to true
            )
            usersCollection.document(user.uid).update(updates).await()
            
            // Atualizar cache local
            _currentUser.value = _currentUser.value?.copy(
                name = name,
                hasCompletedSetup = true,
                isProfileSetup = true
            )
            _needsSetup.value = false
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Salva foto de perfil no Firebase Storage e atualiza Firestore
     */
    suspend fun saveProfilePhoto(uri: Uri): String? {
        val user = auth.currentUser ?: return null
        
        return try {
            _isLoading.value = true
            
            // Upload para Firebase Storage
            val photoRef = storage.reference.child("users/${user.uid}/profile_photo.jpg")
            val uploadTask = photoRef.putFile(uri).await()
            val downloadUrl = uploadTask.storage.downloadUrl.await().toString()
            
            // Atualizar Firestore
            val updates = mapOf("photoUrl" to downloadUrl)
            usersCollection.document(user.uid).update(updates).await()
            
            // Atualizar cache local
            _currentUser.value = _currentUser.value?.copy(photoUrl = downloadUrl)
            
            downloadUrl
        } catch (e: Exception) {
            null
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Salva foto de perfil local (copia para app data)
     */
    suspend fun saveProfilePhotoLocal(uri: Uri): String? {
        return try {
            val userDir = File(context.filesDir, "profile")
            if (!userDir.exists()) userDir.mkdirs()
            
            val photoFile = File(userDir, "profile_photo.jpg")
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(photoFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            val localPath = photoFile.absolutePath
            
            // Atualizar Firestore com caminho local
            usersCollection.document(auth.currentUser?.uid ?: return null)
                .update("profilePhotoPath", localPath)
                .await()
            
            _currentUser.value = _currentUser.value?.copy(profilePhotoPath = localPath)
            
            localPath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Atualiza perfil completo no Firestore
     */
    suspend fun updateProfile(name: String, photoUrl: String?): Boolean {
        val user = auth.currentUser ?: return false
        
        return try {
            val updates = mutableMapOf<String, Any>(
                "name" to name
            )
            photoUrl?.let { updates["photoUrl"] = it }
            
            usersCollection.document(user.uid).update(updates).await()
            
            // Atualizar cache local
            _currentUser.value = _currentUser.value?.copy(
                name = name,
                photoUrl = photoUrl ?: _currentUser.value?.photoUrl ?: ""
            )
            
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Completa setup do perfil
     */
    suspend fun completeProfileSetup(name: String): Boolean {
        return saveDisplayName(name)
    }

    /**
     * Verifica se usuário precisa de setup
     */
    suspend fun checkNeedsSetup(): Boolean {
        val user = getUser()
        return user?.hasCompletedSetup != true
    }

    /**
     * Desconecta o observer
     */
    fun disconnect() {
        _currentUser.value = null
        _needsSetup.value = false
    }

    /**
     * Verifica se há usuário logado
     */
    fun isLoggedIn(): Boolean = auth.currentUser != null

    /**
     * Obtém usuário atual do Firebase Auth
     */
    fun getFirebaseUser() = auth.currentUser

    /**
     * Logout - apenas remove sessão local, não apaga dados do Firestore
     */
    fun logout() {
        disconnect()
    }
}

// Extension functions para conversão
private fun com.google.firebase.firestore.DocumentSnapshot.toUserEntity(): UserEntity? {
    return try {
        UserEntity(
            userId = getString("uid") ?: "",
            name = getString("name") ?: "",
            email = getString("email") ?: "",
            photoUrl = getString("photoUrl") ?: "",
            profilePhotoPath = getString("profilePhotoPath"),
            displayName = getString("displayName"),
            givenName = getString("givenName"),
            familyName = getString("familyName"),
            isEmailVerified = getBoolean("isEmailVerified") ?: false,
            createdAt = getLong("createdAt") ?: System.currentTimeMillis(),
            hasCompletedSetup = getBoolean("hasCompletedSetup") ?: false,
            isProfileSetup = getBoolean("isProfileSetup") ?: false
        )
    } catch (e: Exception) {
        null
    }
}

private fun UserEntity.toMap(): Map<String, Any?> = mapOf(
    "uid" to userId,
    "name" to name,
    "email" to email,
    "photoUrl" to photoUrl,
    "profilePhotoPath" to profilePhotoPath,
    "displayName" to displayName,
    "givenName" to givenName,
    "familyName" to familyName,
    "isEmailVerified" to isEmailVerified,
    "createdAt" to createdAt,
    "hasCompletedSetup" to hasCompletedSetup,
    "isProfileSetup" to isProfileSetup
)
