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
import com.cerevya.ui.screens.chat.ChatScreen
import com.cerevya.ui.screens.memory.MemoryScreen
import com.cerevya.ui.screens.settings.SettingsScreen
import com.cerevya.ui.screens.splash.SplashScreen
import com.cerevya.viewmodel.ChatViewModel
import com.cerevya.viewmodel.MemoryViewModel
import com.cerevya.viewmodel.SettingsViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Google Sign-In client reference
        val googleSignInClient = GoogleSignIn.getClient(
            this,
            com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
            )
                .requestIdToken("123456789012-abc123def456.apps.googleusercontent.com")
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
    
    // State for handling Google Sign-In result
    var signInResultHandler by remember { mutableStateOf<((android.content.Intent?) -> Unit)?>(null) }

    // Intercept all back presses for double-back-to-exit
    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            // Second press within 2 seconds - exit app
            (context as? ComponentActivity)?.finish()
        } else {
            // First press - show message and reset timer
            lastBackPressTime = currentTime
            Toast.makeText(context, exitMessage, Toast.LENGTH_SHORT).show()
        }
    }

    val app = LocalContext.current.applicationContext as CerevyaApplication

    // Helper function for clean navigation (no back stack)
    val navigateTo = { route: String ->
        currentRoute = route
        navController.navigate(route) {
            // Clear entire back stack - no return to previous screens
            popUpTo(0) { inclusive = true }
            // Prevent multiple instances
            launchSingleTop = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                drawerState = drawerState,
                scope = scope,
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navigateTo(route)
                }
            )
        }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route
        ) {
            composable(Screen.Splash.route) {
                SplashScreen(
                    onNavigateToChat = {
                        navigateTo(Screen.Chat.route)
                    }
                )
            }

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
                
                // Select memory if ID provided
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

            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(app.preferencesManager, app.firebaseAuthManager)
                )
                
                // Create sign-in launcher for this composable
                val activity = context as? ComponentActivity
                val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val data = result.data
                    // Handle the result
                    app.firebaseAuthManager.handleGoogleSignInResult(data) { handleResult ->
                        handleResult.fold(
                            onSuccess = { user ->
                                Toast.makeText(context, "Bem-vindo, ${user.name}!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { error ->
                                Toast.makeText(context, "Erro no login: ${error.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
                
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onSignInClick = {
                        signInLauncher.launch(googleSignInClient.signInIntent)
                    }
                )
            }
        }
    }
}
