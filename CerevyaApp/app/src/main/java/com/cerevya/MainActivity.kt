package com.cerevya

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import com.cerevya.navigation.NavigationDrawerContent
import com.cerevya.navigation.Screen
import com.cerevya.theme.CerevyaTheme
import com.cerevya.ui.screens.auth.ProfileSetupScreen
import com.cerevya.ui.screens.auth.WelcomeScreen
import com.cerevya.ui.screens.chat.ChatListScreen
import com.cerevya.ui.screens.chat.ChatScreen
import com.cerevya.ui.screens.memory.MemoryScreen
import com.cerevya.ui.screens.profile.ProfileScreen
import com.cerevya.ui.screens.settings.SettingsScreen
import com.cerevya.ui.screens.splash.SplashScreen
import com.cerevya.viewmodel.ChatViewModel
import com.cerevya.viewmodel.MemoryViewModel
import com.cerevya.viewmodel.SettingsViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Google Sign-In client
        val webClientId = "197213311795-j212a6dgjeab9fs2ibqhlfortjiqo8rt.apps.googleusercontent.com"
        val googleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
        )
        
        setContent {
            CerevyaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CerevyaAppContent(
                        googleSignInClient = googleSignInClient
                    )
                }
            }
        }
    }
}

@Composable
fun CerevyaAppContent(
    googleSignInClient: com.google.android.gms.auth.api.signin.GoogleSignInClient
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentRoute by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val exitMessage = "Pressione novamente para sair"
    
    val app = context.applicationContext as CerevyaApplication
    
    // Firebase Auth state
    val firebaseAuth = FirebaseAuth.getInstance()
    val currentFirebaseUser by remember { mutableStateOf(firebaseAuth.currentUser) }
    
    // Collect Firestore user state
    val firestoreUser by app.firestoreUserManager.currentUser.collectAsState()
    val needsSetup by app.firestoreUserManager.needsSetup.collectAsState()
    val memoryCount by app.memoryRepository.getAllMemories().collectAsState(initial = emptyList())
    
    // Collect chats state
    val chats by app.chatManager.chats.collectAsState()
    val activeChat by app.chatManager.activeChat.collectAsState()
    
    // Auth state
    var isSignInLoading by remember { mutableStateOf(false) }

    // Setup observer para alterações em tempo real do Firestore
    LaunchedEffect(currentFirebaseUser) {
        if (currentFirebaseUser != null) {
            // Criar usuário no Firestore se não existir
            app.firestoreUserManager.createUserIfNotExists()
            // Iniciar observação de chats
            app.chatManager.observeChats()
        } else {
            app.firestoreUserManager.disconnect()
            app.chatManager.clearMessages()
        }
    }

    // Intercept back presses - proper app navigation
    BackHandler(enabled = currentRoute != Screen.Welcome.route && currentRoute != Screen.ProfileSetup.route) {
        val canPop = navController.previousBackStackEntry != null
        if (canPop) {
            navController.popBackStack()
            currentRoute = navController.currentDestination?.route
        } else {
            // At root, exit app
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                (context as? ComponentActivity)?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, exitMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Navigate function - preserves backstack like a proper app
    val navigateTo = { route: String ->
        if (currentRoute != route) {
            currentRoute = route
            navController.navigate(route) {
                // Don't pop everything, just avoid duplicate destinations
                popUpTo(Screen.ChatList.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Navigate to chat with specific chatId
    val navigateToChat = { chatId: String ->
        currentRoute = Screen.Chat.route
        navController.navigate(Screen.Chat.route) {
            popUpTo(Screen.ChatList.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Navigate back function - proper app navigation
    val navigateBack = {
        val canPop = navController.previousBackStackEntry != null
        if (canPop) {
            navController.popBackStack()
            currentRoute = navController.currentDestination?.route ?: Screen.ChatList.route
        }
    }

    // Navigate and clear backstack (for auth flows)
    val navigateAndClearBackstack = { route: String ->
        currentRoute = route
        navController.navigate(route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Create new chat and navigate
    val createNewChat = {
        scope.launch {
            val chat = app.chatManager.createChat()
            chat?.let {
                navigateToChat(it.chatId)
            }
        }
    }

    // Determine start destination based on Firebase Auth + Firestore
    val startDestination = remember(currentFirebaseUser, firestoreUser, needsSetup) {
        when {
            currentFirebaseUser == null -> Screen.Welcome.route
            firestoreUser == null -> Screen.Welcome.route // Aguardando Firestore
            needsSetup -> Screen.ProfileSetup.route
            else -> Screen.ChatList.route
        }
    }

    // Google Sign-In launcher
    val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSignInLoading = false
        
        app.firebaseAuthManager.handleGoogleSignInResult(result.data) { authResult ->
            authResult.fold(
                onSuccess = { user ->
                    // Criar documento no Firestore
                    scope.launch {
                        app.firestoreUserManager.createUserIfNotExists()
                        // Redirecionar baseado no setup - clear backstack for auth flows
                        val needsSetupNow = app.firestoreUserManager.checkNeedsSetup()
                        if (needsSetupNow) {
                            navigateAndClearBackstack(Screen.ProfileSetup.route)
                        } else {
                            navigateAndClearBackstack(Screen.ChatList.route)
                        }
                    }
                },
                onFailure = { error ->
                    Toast.makeText(context, "Erro no login: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                drawerState = drawerState,
                scope = scope,
                currentRoute = currentRoute,
                user = firestoreUser,
                userEmail = currentFirebaseUser?.email,
                memoryCount = memoryCount.size,
                onNavigate = { route ->
                    navigateTo(route)
                },
                onProfileClick = {
                    navigateTo(Screen.Profile.route)
                }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            // Splash
            composable(Screen.Splash.route) {
                SplashScreen(
                    onNavigateToChat = {
                        when {
                            currentFirebaseUser == null -> navigateTo(Screen.Welcome.route)
                            needsSetup -> navigateTo(Screen.ProfileSetup.route)
                            else -> navigateTo(Screen.ChatList.route)
                        }
                    }
                )
            }

            // Welcome Screen (Login)
            composable(Screen.Welcome.route) {
                WelcomeScreen(
                    isLoading = isSignInLoading,
                    onSignInClick = {
                        isSignInLoading = true
                        signInLauncher.launch(googleSignInClient.signInIntent)
                    }
                )
            }

            // Profile Setup Screen
            composable(Screen.ProfileSetup.route) {
                ProfileSetupScreen(
                    user = firestoreUser,
                    onPhotoSelected = { uri ->
                        app.firestoreUserManager.saveProfilePhotoLocal(uri)
                    },
                    onNameSelected = { name ->
                        scope.launch {
                            app.firestoreUserManager.saveDisplayName(name)
                        }
                    },
                    onComplete = { name ->
                        scope.launch {
                            app.firestoreUserManager.completeProfileSetup(name)
                            navigateTo(Screen.ChatList.route)
                        }
                    }
                )
            }

            // Chat List Screen
            composable(Screen.ChatList.route) {
                ChatListScreen(
                    chats = chats,
                    activeChatId = activeChat?.chatId,
                    onChatClick = { chat ->
                        scope.launch {
                            app.chatManager.setActiveChat(chat.chatId)
                            navigateToChat(chat.chatId)
                        }
                    },
                    onNewChatClick = {
                        createNewChat()
                    },
                    onDeleteChat = { chat ->
                        scope.launch {
                            app.chatManager.deleteChat(chat.chatId)
                        }
                    },
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    }
                )
            }

            // Chat Screen
            composable(Screen.Chat.route) {
                val chatViewModel: ChatViewModel = viewModel(
                    factory = ChatViewModel.Factory(app.memoryRepository, app.chatManager)
                )
                
                // Carregar chat ativo se existir
                LaunchedEffect(activeChat) {
                    activeChat?.let { chat ->
                        chatViewModel.loadChat(chat.chatId)
                    }
                }
                
                val uiState by chatViewModel.uiState.collectAsState()
                
                ChatScreen(
                    viewModel = chatViewModel,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onMemoryClick = { memoryId ->
                        navigateTo("${Screen.Memory.route}?id=$memoryId")
                    },
                    chatTitle = uiState.chatTitle
                )
            }

            // Memory Screen
            composable(
                route = Screen.Memory.route + "?id={id}",
                arguments = listOf(
                    navArgument("id") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val memoryId = backStackEntry.arguments?.getString("id")
                val memoryViewModel: MemoryViewModel = viewModel(
                    factory = MemoryViewModel.Factory(app.memoryRepository)
                )
                
                LaunchedEffect(memoryId) {
                    memoryId?.let { id ->
                        memoryViewModel.selectMemory(id)
                    }
                }
                
                MemoryScreen(
                    viewModel = memoryViewModel,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    }
                )
            }

            // Profile Screen
            composable(Screen.Profile.route) {
                ProfileScreen(
                    user = firestoreUser,
                    memoryCount = memoryCount.size,
                    onPhotoSelected = { uri ->
                        app.firestoreUserManager.saveProfilePhotoLocal(uri)
                    },
                    onNameUpdated = { name ->
                        scope.launch {
                            app.firestoreUserManager.updateProfile(name, null)
                        }
                    },
                    onBackClick = {
                        navigateBack()
                    }
                )
            }

            // Settings Screen
            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(
                        app.preferencesManager, 
                        app.firebaseAuthManager, 
                        app.firestoreUserManager
                    )
                )
                
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onSignInClick = {
                        // Já logado
                    },
                    onLogoutComplete = {
                        FirebaseAuth.getInstance().signOut()
                        navigateTo(Screen.Welcome.route)
                    }
                )
            }
        }
    }
}
