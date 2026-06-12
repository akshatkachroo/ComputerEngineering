package com.scribsync.scribsync.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scribsync.scribsync.ui.screens.HomeScreen
import com.scribsync.scribsync.ui.screens.MeetingDetailScreen
import com.scribsync.scribsync.ui.screens.RecordingScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Recording : Screen("recording")
    object MeetingDetail : Screen("meeting_detail/{meetingId}") {
        fun route(meetingId: String) = "meeting_detail/$meetingId"
    }
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartRecording = { navController.navigate(Screen.Recording.route) },
                onMeetingClick = { meetingId -> navController.navigate(Screen.MeetingDetail.route(meetingId)) }
            )
        }
        composable(Screen.Recording.route) {
            RecordingScreen(onStopRecording = { navController.popBackStack() })
        }
        composable(
            route = Screen.MeetingDetail.route,
            arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: return@composable
            MeetingDetailScreen(
                meetingId = meetingId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
