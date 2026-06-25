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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Google Sign-In client with correct WEB_CLIENT_ID
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
    
    // Collect profile state
    val userProfile by app.profileManager.userProfile.collectAsState()
    val memoryCount by app.memoryRepository.getAllMemories().collectAsState(initial = emptyList())
    
    // Auth state
    var isSignInLoading by remember { mutableStateOf(false) }
    var pendingGoogleUser by remember { mutableStateOf<com.cerevya.domain.models.UserEntity?>(null) }

    // Intercept all back presses for double-back-to-exit
    BackHandler(enabled = currentRoute != Screen.Welcome.route && currentRoute != Screen.ProfileSetup.route) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            (context as? ComponentActivity)?.finish()
        } else {
            lastBackPressTime = currentTime
            Toast.makeText(context, exitMessage, Toast.LENGTH_SHORT).show()
        }
    }

    // Helper function for clean navigation (no back stack)
    val navigateTo = { route: String ->
        currentRoute = route
        navController.navigate(route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }

    // Determine start destination based on auth state
    val startDestination = remember(userProfile) {
        when {
            userProfile == null -> Screen.Welcome.route
            userProfile?.isProfileSetup == false -> Screen.ProfileSetup.route
            else -> Screen.Chat.route
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
                    // Save basic profile
                    app.profileManager.saveBasicProfile(user)
                    
                    // Check if profile is already setup
                    if (app.profileManager.isProfileSetup()) {
                        navigateTo(Screen.Chat.route)
                    } else {
                        navigateTo(Screen.ProfileSetup.route)
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
                user = userProfile,
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
            // Splash - redirects based on auth state
            composable(Screen.Splash.route) {
                SplashScreen(
                    onNavigateToChat = {
                        if (userProfile?.isProfileSetup == true) {
                            navigateTo(Screen.Chat.route)
                        } else if (userProfile != null) {
                            navigateTo(Screen.ProfileSetup.route)
                        } else {
                            navigateTo(Screen.Welcome.route)
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
                val user = userProfile
                ProfileSetupScreen(
                    user = user ?: com.cerevya.domain.models.UserEntity(
                        userId = "",
                        name = "",
                        email = ""
                    ),
                    onPhotoSelected = { uri ->
                        app.profileManager.saveProfilePhoto(uri)
                    },
                    onNameSelected = { name ->
                        app.profileManager.saveDisplayName(name)
                    },
                    onComplete = {
                        app.profileManager.completeProfileSetup()
                        navigateTo(Screen.Chat.route)
                    }
                )
            }

            // Chat Screen
            composable(Screen.Chat.route) {
                val chatViewModel: ChatViewModel = viewModel(
                    factory = ChatViewModel.Factory(app.memoryRepository)
                )
                ChatScreen(
                    viewModel = chatViewModel,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onMemoryClick = { memoryId ->
                        navigateTo("${Screen.Memory.route}?id=$memoryId")
                    }
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
                    user = userProfile,
                    memoryCount = memoryCount.size,
                    onPhotoSelected = { uri ->
                        app.profileManager.saveProfilePhoto(uri)
                    },
                    onNameUpdated = { name ->
                        app.profileManager.saveDisplayName(name)
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            // Settings Screen
            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(app.preferencesManager, app.firebaseAuthManager, app.profileManager)
                )
                
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onSignInClick = {
                        // Already logged in, no need for sign-in
                    },
                    onLogoutComplete = {
                        navigateTo(Screen.Welcome.route)
                    }
                )
            }
        }
    }
}
