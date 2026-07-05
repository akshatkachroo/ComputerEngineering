package com.scribesync.scribesync.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scribesync.scribesync.ui.screens.CalendarScreen
import com.scribesync.scribesync.ui.screens.HomeScreen
import com.scribesync.scribesync.ui.screens.MeetingDetailScreen
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
    object MeetingDetail : Screen("meeting_detail/{meetingId}") {
        fun createRoute(meetingId: String) = "meeting_detail/$meetingId"
    }
    object Calendar : Screen("calendar")
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
                },
                onMeetingClick = { meetingId ->
                    navController.navigate(Screen.MeetingDetail.createRoute(meetingId))
                },
                onNavigateToCalendar = {
                    navController.navigate(Screen.Calendar.route)
                }
            )
        }
        composable(Screen.Calendar.route) {
            CalendarScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onMeetingClick = { meetingId ->
                    navController.navigate(Screen.MeetingDetail.createRoute(meetingId))
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
        composable(
            route = Screen.MeetingDetail.route,
            arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
            MeetingDetailScreen(
                viewModel = viewModel,
                meetingId = meetingId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
