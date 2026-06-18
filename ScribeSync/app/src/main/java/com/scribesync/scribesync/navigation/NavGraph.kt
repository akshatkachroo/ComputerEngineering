package com.scribesync.scribesync.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scribesync.scribesync.ui.screens.HomeScreen
import com.scribesync.scribesync.ui.screens.RecordingScreen
import com.scribesync.scribesync.ui.viewmodel.MeetingViewModel

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Recording : Screen("recording")
}

@Composable
fun NavGraph(viewModel: MeetingViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onStartRecording = { navController.navigate(Screen.Recording.route) }
            )
        }
        composable(Screen.Recording.route) {
            RecordingScreen(
                viewModel = viewModel,
                onStopRecording = { navController.popBackStack() }
            )
        }
    }
}
