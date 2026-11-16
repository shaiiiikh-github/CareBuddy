package com.carebuddy.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carebuddy.app.ble.BleConnectionState
import com.carebuddy.app.data.EmergencyContact
import com.carebuddy.app.data.Reminder
import com.carebuddy.app.data.RepeatOption
import com.carebuddy.app.ui.components.*
import com.carebuddy.app.ui.screens.*
import com.carebuddy.app.ui.theme.CareBuddyTheme

private val sampleReminders = listOf(
    Reminder(
        id = "1",
        title = "Morning Blood Pressure Pill",
        time = "08:30 AM",
        minutesOfDay = 510,
        repeat = RepeatOption.DAILY,
        category = "Medication",
        enabled = true,
        isAcknowledgedToday = true
    ),
    Reminder(
        id = "2",
        title = "Drink Water",
        time = "01:00 PM",
        minutesOfDay = 780,
        repeat = RepeatOption.DAILY,
        category = "Water",
        enabled = true,
        isAcknowledgedToday = false
    ),
    Reminder(
        id = "3",
        title = "Evening Walk in Park",
        time = "06:00 PM",
        minutesOfDay = 1080,
        repeat = RepeatOption.DAILY,
        category = "Activity",
        enabled = false,
        isAcknowledgedToday = false
    )
)

private val sampleContacts = listOf(
    EmergencyContact(
        id = "1",
        name = "Sarah Miller",
        phoneNumber = "+1 555 234 5678",
        isPrimary = true,
        enabled = true
    ),
    EmergencyContact(
        id = "2",
        name = "Dr. James Wilson",
        phoneNumber = "+1 555 876 5432",
        isPrimary = false,
        enabled = true
    )
)

@Preview(name = "Dashboard - Light", showBackground = true)
@Composable
fun DashboardLightPreview() {
    CareBuddyTheme(darkTheme = false) {
        DashboardContent(
            connectionState = BleConnectionState.CONNECTED,
            deviceName = "CareBuddy-A14F",
            reminders = sampleReminders,
            contactsCount = sampleContacts.size,
            primaryContactName = "Sarah Miller",
            ringingReminder = null,
            currentDate = "Monday, November 16",
            onConnectClick = {},
            onSyncTimeClick = {},
            onTestSosClick = {},
            onNavigateToReminders = {},
            onNavigateToEmergency = {}
        )
    }
}

@Preview(name = "Dashboard - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun DashboardDarkPreview() {
    CareBuddyTheme(darkTheme = true) {
        DashboardContent(
            connectionState = BleConnectionState.CONNECTED,
            deviceName = "CareBuddy-A14F",
            reminders = sampleReminders,
            contactsCount = sampleContacts.size,
            primaryContactName = "Sarah Miller",
            ringingReminder = null,
            currentDate = "Monday, November 16",
            onConnectClick = {},
            onSyncTimeClick = {},
            onTestSosClick = {},
            onNavigateToReminders = {},
            onNavigateToEmergency = {}
        )
    }
}

@Preview(name = "Reminders - Light", showBackground = true)
@Composable
fun RemindersLightPreview() {
    CareBuddyTheme(darkTheme = false) {
        RemindersContent(
            reminders = sampleReminders,
            onToggle = { _, _ -> },
            onAddClick = {},
            onEditClick = {},
            onDeleteClick = {}
        )
    }
}

@Preview(name = "Reminders - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun RemindersDarkPreview() {
    CareBuddyTheme(darkTheme = true) {
        RemindersContent(
            reminders = sampleReminders,
            onToggle = { _, _ -> },
            onAddClick = {},
            onEditClick = {},
            onDeleteClick = {}
        )
    }
}

@Preview(name = "Components - Light", showBackground = true)
@Composable
fun ComponentsLightPreview() {
    CareBuddyTheme(darkTheme = false) {
        ComponentsShowcase()
    }
}

@Preview(name = "Components - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
fun ComponentsDarkPreview() {
    CareBuddyTheme(darkTheme = true) {
        ComponentsShowcase()
    }
}

@Composable
private fun ComponentsShowcase() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(text = "Capsule Buttons")
        CapsuleButton(text = "Primary Action", onClick = {}, style = ButtonStyle.Filled)
        CapsuleButton(text = "Secondary Tinted", onClick = {}, style = ButtonStyle.Tinted)
        CapsuleButton(text = "Danger Action", onClick = {}, style = ButtonStyle.Danger)

        SectionHeader(text = "Interactive Toggles & Controls")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IosSwitch(checked = true, onCheckedChange = {})
            IosSwitch(checked = false, onCheckedChange = {})
            StatusPill(label = "Connected", dotColor = CareBuddyTheme.colors.success)
            StatusPill(label = "Pending", dotColor = CareBuddyTheme.colors.warning)
        }

        SectionHeader(text = "Progress Ring")
        ProgressRing(progress = 0.67f)

        SectionHeader(text = "Segmented Control")
        SegmentedControl(
            items = listOf("Daily", "Weekdays", "Custom"),
            selectedIndex = 0,
            onItemSelected = {}
        )
    }
}
