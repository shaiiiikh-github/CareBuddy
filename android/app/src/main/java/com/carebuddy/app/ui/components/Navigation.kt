package com.carebuddy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.carebuddy.app.ui.screens.*
import com.carebuddy.app.ui.theme.SlateCard
import com.carebuddy.app.ui.theme.SlateDark
import com.carebuddy.app.ui.theme.TealLight
import com.carebuddy.app.ui.theme.TextMuted
import com.carebuddy.app.viewmodel.MainViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Reminders : Screen("reminders", "Reminders", Icons.Default.Notifications)
    object Contacts : Screen("contacts", "Emergency", Icons.Default.Phone)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object AddEditReminder : Screen("add_edit_reminder?id={id}", "Edit Reminder", Icons.Default.Notifications)
}

@Composable
fun CareBuddyAppNavigation(
    viewModel: MainViewModel,
    navController: NavHostController = rememberNavController()
) {
    val items = listOf(
        Screen.Dashboard,
        Screen.Reminders,
        Screen.Contacts,
        Screen.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in items.map { it.route }) {
                NavigationBar(
                    containerColor = SlateCard,
                    contentColor = TealLight
                ) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = SlateDark,
                                selectedTextColor = TealLight,
                                indicatorColor = TealLight,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        },
        containerColor = SlateDark
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(paddingValues)
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

        // Overlay SOS Alert Modal Screen whenever SOS is active
        SosAlertScreen(viewModel = viewModel)
    }
}
