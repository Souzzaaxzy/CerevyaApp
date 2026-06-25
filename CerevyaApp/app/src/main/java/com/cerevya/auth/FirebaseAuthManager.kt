package com.cerevya.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.cerevya.domain.models.UserEntity
import com.cerevya.domain.models.UserSession
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FirebaseAuthManager - Gerencia autenticação com Firebase + Google Sign-In
 * 
 * Responsabilidades:
 * - Login com Google via Firebase Auth
 * - Logout
 * - Manutenção de sessão
 * - Listener de estado de autenticação
 */
class FirebaseAuthManager(context: Context) {
    
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<UserSession> = _session.asStateFlow()
    
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }
    
    init {
        // Adicionar listener para mudanças de autenticação
        firebaseAuth.addAuthStateListener { auth ->
            updateSession(auth.currentUser)
        }
        
        // Inicializar sessão com usuário atual se existir
        if (firebaseAuth.currentUser != null) {
            updateSession(firebaseAuth.currentUser)
        }
    }
    
    /**
     * Carrega sessão salva localmente
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
     * Atualiza sessão com usuário Firebase
     */
    private fun updateSession(firebaseUser: com.google.firebase.auth.FirebaseUser?) {
        if (firebaseUser != null) {
            val user = UserEntity(
                userId = firebaseUser.uid,
                name = firebaseUser.displayName ?: "",
                email = firebaseUser.email ?: "",
                photoUrl = firebaseUser.photoUrl?.toString()
            )
            saveSession(user)
        } else {
            clearSession()
        }
    }
    
    /**
     * Salva sessão localmente
     */
    private fun saveSession(user: UserEntity) {
        prefs.edit().apply {
            putString(KEY_USER_ID, user.userId)
            putString(KEY_USER_NAME, user.name)
            putString(KEY_USER_EMAIL, user.email)
            user.photoUrl?.let { putString(KEY_USER_PHOTO, it) } ?: remove(KEY_USER_PHOTO)
            apply()
        }
        _session.value = UserSession(user = user, isLoggedIn = true)
        Log.d(TAG, "Session saved for user: ${user.email}")
    }
    
    /**
     * Limpa sessão local
     */
    private fun clearSession() {
        prefs.edit().clear().apply()
        _session.value = UserSession(user = null, isLoggedIn = false)
        Log.d(TAG, "Session cleared")
    }
    
    /**
     * Realiza login com Google
     * @param activity Activity para o fluxo de sign-in
     * @param onComplete Callback com resultado
     */
    fun signInWithGoogle(
        activity: Activity,
        onComplete: (Result<UserEntity>) -> Unit
    ) {
        // Verificar sign-in existente primeiro
        val signInAccount = GoogleSignIn.getLastSignedInAccount(activity)
        
        if (signInAccount != null && signInAccount.idToken != null) {
            // Já logado com Google, autenticar no Firebase
            firebaseAuthWithGoogle(signInAccount.idToken!!, onComplete)
        } else {
            // Iniciar novo fluxo de sign-in
            activity.startActivityForResult(
                googleSignInClient.signInIntent,
                RC_SIGN_IN
            )
            // O resultado será tratado via ActivityResultLauncher no componente chamador
            // Por enquanto, chamamos o callback com pending status
            onComplete(Result.failure(Exception("Aguardando seleção de conta Google")))
        }
    }
    
    /**
     * Processa resultado do Google Sign-In
     * Deve ser chamado pela Activity
     */
    fun handleGoogleSignInResult(
        data: android.content.Intent?,
        onComplete: (Result<UserEntity>) -> Unit
    ) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account.idToken != null) {
                firebaseAuthWithGoogle(account.idToken!!, onComplete)
            } else {
                onComplete(Result.failure(Exception("Token Google não disponível")))
            }
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign in failed", e)
            onComplete(Result.failure(e))
        }
    }
    
    /**
     * Autentica no Firebase com token Google
     */
    private fun firebaseAuthWithGoogle(
        idToken: String,
        onComplete: (Result<UserEntity>) -> Unit
    ) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val firebaseUser = firebaseAuth.currentUser
                    if (firebaseUser != null) {
                        val user = UserEntity(
                            userId = firebaseUser.uid,
                            name = firebaseUser.displayName ?: "",
                            email = firebaseUser.email ?: "",
                            photoUrl = firebaseUser.photoUrl?.toString()
                        )
                        saveSession(user)
                        Log.d(TAG, "Firebase auth successful: ${user.email}")
                        onComplete(Result.success(user))
                    } else {
                        onComplete(Result.failure(Exception("Usuário Firebase não encontrado")))
                    }
                } else {
                    val exception = task.exception
                    Log.e(TAG, "Firebase auth failed", exception)
                    onComplete(Result.failure(exception ?: Exception("Falha na autenticação")))
                }
            }
    }
    
    /**
     * Realiza logout
     */
    fun signOut() {
        // Sign out do Google
        googleSignInClient.signOut()
            .addOnCompleteListener {
                // Sign out do Firebase
                firebaseAuth.signOut()
                clearSession()
                Log.d(TAG, "User signed out")
            }
    }
    
    /**
     * Retorna usuário atual do Firebase
     */
    fun getCurrentUser() = firebaseAuth.currentUser
    
    /**
     * Verifica se há usuário logado
     */
    fun isUserLoggedIn(): Boolean = firebaseAuth.currentUser != null
    
    /**
     * Retorna ID do usuário atual
     */
    fun getUserId(): String? = firebaseAuth.currentUser?.uid
    
    /**
     * Retorna email do usuário atual
     */
    fun getUserEmail(): String? = firebaseAuth.currentUser?.email
    
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
        private const val TAG = "FirebaseAuthManager"
        private const val PREFS_NAME = "cerevya_auth"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_PHOTO = "user_photo"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val RC_SIGN_IN = 9001
        
        // Web Client ID do google-services.json (client_type: 3)
        private const val WEB_CLIENT_ID = "197213311795-9bh76pjnq9pgh69k6ffphvkps6goq3ck.apps.googleusercontent.com"
    }
}
