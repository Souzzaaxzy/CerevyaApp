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
    
    val firebaseAuth = FirebaseAuth.getInstance()
    val currentFirebaseUser by remember { mutableStateOf(firebaseAuth.currentUser) }
    
    val firestoreUser by app.firestoreUserManager.currentUser.collectAsState()
    val needsSetup by app.firestoreUserManager.needsSetup.collectAsState()
    val memoryCount by app.memoryRepository.getAllMemories().collectAsState(initial = emptyList())
    val chats by app.chatRepository.chats.collectAsState()
    val activeChat by app.chatRepository.activeChat.collectAsState()
    
    var isSignInLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Inicializar chat repository
        // Os chats já são carregados automaticamente pelo ChatRepository
    }

    BackHandler(enabled = currentRoute != Screen.Welcome.route && currentRoute != Screen.ProfileSetup.route) {
        val canPop = navController.previousBackStackEntry != null
        if (canPop) {
            navController.popBackStack()
            currentRoute = navController.currentDestination?.route
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                (context as? ComponentActivity)?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, exitMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Navigate function - preserves backstack
    val navigateTo = { route: String ->
        if (currentRoute != route) {
            currentRoute = route
            navController.navigate(route) {
                popUpTo(Screen.Chat.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Navigate back function
    val navigateBack = {
        val canPop = navController.previousBackStackEntry != null
        if (canPop) {
            navController.popBackStack()
            currentRoute = navController.currentDestination?.route ?: Screen.Chat.route
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
    fun handleNewChatClick() {
        scope.launch {
            app.chatRepository.createChat()
        }
    }

    // Start destination - goes directly to Chat after login
    val startDestination = remember(currentFirebaseUser, firestoreUser, needsSetup) {
        when {
            currentFirebaseUser == null -> Screen.Welcome.route
            firestoreUser == null -> Screen.Welcome.route
            needsSetup -> Screen.ProfileSetup.route
            else -> Screen.Chat.route
        }
    }

    val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isSignInLoading = false
        
        app.firebaseAuthManager.handleGoogleSignInResult(result.data) { authResult ->
            authResult.fold(
                onSuccess = { user ->
                    scope.launch {
                        app.firestoreUserManager.createUserIfNotExists()
                        val needsSetupNow = app.firestoreUserManager.checkNeedsSetup()
                        if (needsSetupNow) {
                            navigateAndClearBackstack(Screen.ProfileSetup.route)
                        } else {
                            navigateAndClearBackstack(Screen.Chat.route)
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
                chats = chats,
                activeChatId = activeChat?.chatId,
                onNavigate = { route ->
                    navigateTo(route)
                },
                onProfileClick = {
                    navigateTo(Screen.Profile.route)
                },
                onNewChatClick = { handleNewChatClick() },
                onChatClick = { chat ->
                    scope.launch {
                        app.chatRepository.setActiveChat(chat.chatId)
                        app.chatRepository.observeMessages(chat.chatId)
                    }
                }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onNavigateToChat = {
                        when {
                            currentFirebaseUser == null -> navigateTo(Screen.Welcome.route)
                            needsSetup -> navigateTo(Screen.ProfileSetup.route)
                            else -> navigateTo(Screen.Chat.route)
                        }
                    }
                )
            }

            composable(Screen.Welcome.route) {
                WelcomeScreen(
                    isLoading = isSignInLoading,
                    onSignInClick = {
                        isSignInLoading = true
                        signInLauncher.launch(googleSignInClient.signInIntent)
                    }
                )
            }

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
                            navigateTo(Screen.Chat.route)
                        }
                    }
                )
            }

            // Chat Screen - main screen after login
            composable(Screen.Chat.route) {
                val chatViewModel: ChatViewModel = viewModel(
                    factory = ChatViewModel.Factory(app.memoryRepository, app.chatRepository, app.aiChatManager)
                )
                
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
                    onSignInClick = { },
                    onLogoutComplete = {
                        FirebaseAuth.getInstance().signOut()
                        navigateTo(Screen.Welcome.route)
                    }
                )
            }
        }
    }
}
