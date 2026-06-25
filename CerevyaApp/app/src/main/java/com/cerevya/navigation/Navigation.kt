package com.cerevya.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Chat : Screen("chat")
    data object Memory : Screen("memory")
    data object Settings : Screen("settings")
    
    companion object {
        fun memoryWithId(id: String) = "memory?id=$id"
    }
}
