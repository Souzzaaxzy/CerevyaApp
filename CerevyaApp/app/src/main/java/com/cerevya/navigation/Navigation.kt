package com.cerevya.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Welcome : Screen("welcome")
    data object ProfileSetup : Screen("profile_setup")
    data object ChatList : Screen("chat_list")
    data object Chat : Screen("chat")
    data object Memory : Screen("memory")
    data object Profile : Screen("profile")
    data object Settings : Screen("settings")
    
    companion object {
        fun memoryWithId(id: String) = "memory?id=$id"
        fun chatWithId(id: String) = "chat?id=$id"
    }
}
