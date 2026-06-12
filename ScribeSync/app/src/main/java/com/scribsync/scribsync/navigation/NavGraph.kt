package com.scribsync.scribsync.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scribsync.scribsync.ui.screens.HomeScreen
import com.scribsync.scribsync.ui.screens.RecordingScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Recording : Screen("recording")
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(onStartRecording = { navController.navigate(Screen.Recording.route) })
        }
        composable(Screen.Recording.route) {
            RecordingScreen(onStopRecording = { navController.popBackStack() })
        }
    }
}
