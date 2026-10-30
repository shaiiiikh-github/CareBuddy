package com.carebuddy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.carebuddy.app.ble.BleConnectionState
import com.carebuddy.app.data.Reminder
import com.carebuddy.app.ui.theme.*
import com.carebuddy.app.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToReminders: () -> Unit,
    onNavigateToEmergency: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val reminders by viewModel.reminders.collectAsState()

    val nextReminder = reminders.firstOrNull { !it.isAcknowledgedToday && it.enabled }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDark)
            .padding(16.dp)
    ) {
        // App Bar Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "CareBuddy",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Text(
                    text = "Elderly Companion Assistant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Connection Badge
            ConnectionBadge(
                state = connectionState,
                deviceName = deviceName,
                onConnectClick = {
                    if (connectionState == BleConnectionState.DISCONNECTED) {
                        viewModel.connectDevice()
                    } else {
                        viewModel.disconnectDevice()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Test SOS Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SlateCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "SOS Test",
                        tint = RedEmergency,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Emergency SOS Test",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Test ESP32 <-> App link safely",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                Button(
                    onClick = { viewModel.testSosFromApp() },
                    colors = ButtonDefaults.buttonColors(containerColor = RedEmergencyDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Test SOS", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Next Reminder Section
        Text(
            text = "Next Reminder",
            style = MaterialTheme.typography.titleLarge,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (nextReminder != null) {
            NextReminderCard(reminder = nextReminder)
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎉 All reminders for today are completed!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = GreenSuccess
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Today's Reminders List Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Today's Schedule",
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary
            )
            TextButton(onClick = onNavigateToReminders) {
                Text("Manage All", color = TealLight, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(reminders) { reminder ->
                ReminderScheduleItem(reminder = reminder)
            }
        }
    }
}

@Composable
fun ConnectionBadge(
    state: BleConnectionState,
    deviceName: String?,
    onConnectClick: () -> Unit
) {
    val (statusColor, statusText) = when (state) {
        BleConnectionState.CONNECTED -> Pair(GreenSuccess, deviceName ?: "Connected")
        BleConnectionState.CONNECTING -> Pair(AmberWarning, "Connecting..")
        BleConnectionState.RECONNECTING -> Pair(AmberWarning, "Reconnecting..")
        BleConnectionState.DISCONNECTED -> Pair(TextMuted, "Disconnected")
    }

    Surface(
        onClick = onConnectClick,
        shape = RoundedCornerShape(20.dp),
        color = SlateCard,
        modifier = Modifier.border(1.dp, SlateCardBorder, RoundedCornerShape(20.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }
    }
}

@Composable
fun NextReminderCard(reminder: Reminder) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TealLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = TealAccent.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = TealLight,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = reminder.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = reminder.category,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            Text(
                text = reminder.time,
                style = MaterialTheme.typography.headlineMedium,
                color = TealGlow,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ReminderScheduleItem(reminder: Reminder) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (reminder.isAcknowledgedToday) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (reminder.isAcknowledgedToday) GreenSuccess else TextMuted,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (reminder.isAcknowledgedToday) TextMuted else TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = reminder.time,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
    }
}
