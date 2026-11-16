package com.carebuddy.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.carebuddy.app.ui.screens.*
import com.carebuddy.app.ui.theme.CareBuddyTheme
import com.carebuddy.app.viewmodel.MainViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Today", Icons.Outlined.Home)
    object Reminders : Screen("reminders", "Reminders", Icons.Outlined.Alarm)
    object Contacts : Screen("contacts", "Emergency", Icons.Outlined.Phone)
    object Settings : Screen("settings", "Settings", Icons.Outlined.Settings)
    object AddEditReminder : Screen("add_edit_reminder?id={id}", "Edit Reminder", Icons.Outlined.Alarm)
}

@Composable
fun CareBuddyAppNavigation(
    viewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
) {
    val tabScreens = listOf(
        Screen.Dashboard,
        Screen.Reminders,
        Screen.Contacts,
        Screen.Settings
    )

    val tabItems = tabScreens.map { screen ->
        TabItem(route = screen.route, title = screen.title, icon = screen.icon)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CareBuddyTheme.colors.background)
            .imePadding()
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToReminders = { navController.navigate(Screen.Reminders.route) },
                    onNavigateToEmergency = { navController.navigate(Screen.Contacts.route) }
                )
            }
            composable(Screen.Reminders.route) {
                RemindersScreen(
                    viewModel = viewModel,
                    onAddReminderClick = { navController.navigate("add_edit_reminder?id=new") },
                    onEditReminderClick = { reminder -> navController.navigate("add_edit_reminder?id=${reminder.id}") }
                )
            }
            composable(Screen.Contacts.route) {
                EmergencyContactScreen(viewModel = viewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }
            composable("add_edit_reminder?id={id}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("id")
                val actualId = if (id == "new") null else id
                AddEditReminderScreen(
                    viewModel = viewModel,
                    existingReminderId = actualId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }

        // Floating Glass Tab Bar (visible on top-level destinations)
        AnimatedVisibility(
            visible = currentRoute in tabScreens.map { it.route },
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            FloatingTabBar(
                items = tabItems,
                currentRoute = currentRoute,
                onItemSelected = { selectedTab ->
                    navController.navigate(selectedTab.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }

        // Overlay SOS Alert Modal Screen whenever SOS is active
        SosAlertScreen(viewModel = viewModel)
    }
}
