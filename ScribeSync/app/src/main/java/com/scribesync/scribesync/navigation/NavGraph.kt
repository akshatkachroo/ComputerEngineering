package com.scribesync.scribesync.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scribesync.scribesync.ui.screens.HomeScreen
import com.scribesync.scribesync.ui.screens.RecordingScreen
import com.scribesync.scribesync.ui.viewmodel.MeetingViewModel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Recording : Screen("recording/{title}") {
        fun createRoute(title: String): String {
            val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
            return "recording/$encodedTitle"
        }
    }
}

@Composable
fun NavGraph(viewModel: MeetingViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onStartRecording = { title -> 
                    navController.navigate(Screen.Recording.createRoute(title))
                }
            )
        }
        composable(
            route = Screen.Recording.route,
            arguments = listOf(navArgument("title") { type = NavType.StringType })
        ) { backStackEntry ->
            val title = backStackEntry.arguments?.getString("title") ?: "Untitled Meeting"
            RecordingScreen(
                viewModel = viewModel,
                meetingTitle = title,
                onStopRecording = { navController.popBackStack() }
            )
        }
    }
}
