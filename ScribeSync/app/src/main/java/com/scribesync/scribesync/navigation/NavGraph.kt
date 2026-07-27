package com.scribesync.scribesync.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scribesync.scribesync.ui.screens.CalendarScreen
import com.scribesync.scribesync.ui.screens.ChatScreen
import com.scribesync.scribesync.ui.screens.ContactsScreen
import com.scribesync.scribesync.ui.screens.HomeScreen
import com.scribesync.scribesync.ui.screens.MeetingDetailScreen
import com.scribesync.scribesync.ui.screens.RecordingScreen
import com.scribesync.scribesync.ui.screens.SettingsScreen
import com.scribesync.scribesync.ui.viewmodel.AuthViewModel
import com.scribesync.scribesync.ui.viewmodel.ChatViewModel
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
    object Chat : Screen("chat/{meetingId}") {
        fun createRoute(meetingId: String) = "chat/$meetingId"
    }
    object Calendar : Screen("calendar")
    object TodoList : Screen("todo?meetingId={meetingId}") {
        fun createRoute(meetingId: String? = null) = if (meetingId != null) "todo?meetingId=$meetingId" else "todo"
    }
    object Contacts : Screen("contacts")
    object Settings : Screen("settings")
}

private data class BottomNavTab(val screen: Screen, val label: String, val icon: ImageVector)

private val bottomNavTabs = listOf(
    BottomNavTab(Screen.Home, "Meetings", Icons.Default.Mic),
    BottomNavTab(Screen.TodoList, "Tasks", Icons.Default.CheckCircle),
    BottomNavTab(Screen.Calendar, "Calendar", Icons.Default.CalendarToday),
    BottomNavTab(Screen.Contacts, "Contacts", Icons.Default.People),
    BottomNavTab(Screen.Settings, "Settings", Icons.Default.Settings)
)

@Composable
fun NavGraph(viewModel: MeetingViewModel, authViewModel: AuthViewModel) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomBar = bottomNavTabs.any { it.screen.route == currentRoute }
    val pendingInviteCount by authViewModel.pendingAttendeeRequestCount.collectAsState()

    Scaffold(
        // Each screen owns its own TopAppBar/status-bar inset handling; if this
        // outer Scaffold also reserved safeDrawing insets, the status-bar gap
        // would be applied twice, stacking into a large empty band up top.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.screen.route,
                            onClick = {
                                navController.navigate(tab.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab.screen == Screen.Contacts && pendingInviteCount > 0) {
                                    BadgedBox(badge = { Badge { Text(pendingInviteCount.toString()) } }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onStartRecording = { title ->
                        navController.navigate(Screen.Recording.createRoute(title))
                    },
                    onMeetingClick = { meetingId ->
                        navController.navigate(Screen.MeetingDetail.createRoute(meetingId))
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(authViewModel = authViewModel)
            }
            composable(Screen.Contacts.route) {
                ContactsScreen()
            }
            composable(
                route = Screen.TodoList.route,
                arguments = listOf(navArgument("meetingId") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                })
            ) { backStackEntry ->
                val meetingId = backStackEntry.arguments?.getString("meetingId")
                com.scribesync.scribesync.ui.screens.TodoScreen(
                    viewModel = viewModel,
                    meetingId = meetingId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    viewModel = viewModel,
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
                    onBack = { navController.popBackStack() },
                    authViewModel = authViewModel,
                    onAskAboutMeeting = { id ->
                        navController.navigate(Screen.Chat.createRoute(id))
                    },
                    onSeeTasks = { id ->
                        navController.navigate(Screen.TodoList.createRoute(id))
                    }
                )
            }
            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
            ) { backStackEntry ->
                val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
                val chatViewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    factory = ChatViewModel.factory(meetingId)
                )
                ChatScreen(
                    viewModel = chatViewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
