package com.carebuddy.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebuddy.app.ble.BleConnectionState
import com.carebuddy.app.ble.BleStatus
import com.carebuddy.app.data.Reminder
import com.carebuddy.app.ui.components.*
import com.carebuddy.app.ui.theme.CareBuddyTheme
import com.carebuddy.app.ui.theme.pressScale
import com.carebuddy.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToReminders: () -> Unit,
    onNavigateToEmergency: () -> Unit
) {
    val bleStatus by viewModel.bleStatus.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val reminders by viewModel.reminders.collectAsState()
    val contacts by viewModel.emergencyContacts.collectAsState()
    val ringingReminder by viewModel.ringingReminder.collectAsState()

    val currentDate = remember {
        val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        sdf.format(Date())
    }

    DashboardContent(
        bleStatus = bleStatus,
        deviceName = deviceName,
        reminders = reminders,
        contactsCount = contacts.size,
        primaryContactName = contacts.firstOrNull { it.isPrimary }?.name ?: contacts.firstOrNull()?.name,
        ringingReminder = ringingReminder,
        currentDate = currentDate,
        onConnectClick = {
            if (bleStatus is BleStatus.Connected) {
                viewModel.disconnectDevice()
            } else {
                viewModel.connectDevice()
            }
        },
        onSyncTimeClick = { viewModel.syncTimeToDevice() },
        onTestSosClick = { viewModel.testSosFromApp() },
        onNavigateToReminders = onNavigateToReminders,
        onNavigateToEmergency = onNavigateToEmergency
    )
}

@Composable
fun DashboardContent(
    bleStatus: BleStatus,
    deviceName: String?,
    reminders: List<Reminder>,
    contactsCount: Int,
    primaryContactName: String?,
    ringingReminder: Reminder?,
    currentDate: String,
    onConnectClick: () -> Unit,
    onSyncTimeClick: () -> Unit,
    onTestSosClick: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToEmergency: () -> Unit
) {
    val colors = CareBuddyTheme.colors

    val activeReminders = reminders.filter { it.enabled }
    val acknowledgedCount = activeReminders.count { it.isAcknowledgedToday }
    val totalCount = activeReminders.size
    val progress = if (totalCount > 0) acknowledgedCount.toFloat() / totalCount else 0f
    val nextReminder = activeReminders.firstOrNull { !it.isAcknowledgedToday }

    LargeTitleScaffold(
        title = "Today",
        subtitle = currentDate,
        trailingAction = {
            // Live Device State Pill with pulse indicator
            LiveConnectionPill(
                status = bleStatus,
                deviceName = deviceName,
                onClick = onConnectClick
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp), // Clear floating tab bar
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ringing Reminder Alert (if device is ringing)
            if (ringingReminder != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = colors.accentSubtle,
                    onClick = onNavigateToReminders
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.NotificationsActive,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reminder ringing on CareBuddy",
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 16.sp),
                                color = colors.textPrimary
                            )
                            Text(
                                text = "${ringingReminder.title} at ${ringingReminder.time}. Tap the top touch sensor on your device to acknowledge.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Warning if no emergency contacts exist
            if (contactsCount == 0) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = colors.dangerSubtle,
                    onClick = onNavigateToEmergency
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.danger),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "No emergency contact configured",
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 16.sp),
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Tap here to add a phone number so SOS alarms can notify your family.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Hero Glass Card: Next Reminder & Daily Progress
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToReminders
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Next reminder",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (nextReminder != null) {
                            Text(
                                text = nextReminder.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = nextReminder.time,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = colors.accent
                            )
                        } else {
                            Text(
                                text = if (totalCount > 0) "All done for today" else "No reminders scheduled",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (totalCount > 0) "$totalCount of $totalCount acknowledged" else "Tap to add your first reminder",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Progress Ring
                    ProgressRing(
                        progress = progress,
                        size = 62.dp,
                        strokeWidth = 6.dp,
                        ringColor = if (progress >= 1f && totalCount > 0) colors.success else colors.accent
                    ) {
                        Text(
                            text = "$acknowledgedCount/$totalCount",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = colors.textPrimary
                        )
                    }
                }
            }

            // Inset Grouped Section: Safety & Companion Status
            SectionHeader(text = "Assistant Overview")
            InsetGroupedList {
                ListRow(
                    title = "Emergency contact",
                    subtitle = if (contactsCount > 0) primaryContactName ?: "$contactsCount contacts" else "Action needed",
                    icon = Icons.Outlined.Shield,
                    iconBackgroundColor = if (contactsCount > 0) colors.success else colors.warning,
                    showChevron = true,
                    onClick = onNavigateToEmergency,
                    trailing = {
                        StatusPill(
                            label = if (contactsCount > 0) "Active" else "Empty",
                            dotColor = if (contactsCount > 0) colors.success else colors.warning
                        )
                    }
                )
                ListRow(
                    title = "Scheduled reminders",
                    subtitle = "$totalCount active (${totalCount - acknowledgedCount} pending)",
                    icon = Icons.Outlined.Alarm,
                    iconBackgroundColor = colors.accent,
                    showChevron = true,
                    showDivider = false,
                    onClick = onNavigateToReminders
                )
            }

            // Quick Actions: Calm and Quiet
            SectionHeader(text = "Quick Actions")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CapsuleButton(
                    text = "Sync Clock",
                    icon = Icons.Outlined.Schedule,
                    onClick = onSyncTimeClick,
                    style = ButtonStyle.Tinted,
                    modifier = Modifier.weight(1f)
                )

                CapsuleButton(
                    text = "Test SOS",
                    icon = Icons.Outlined.Emergency,
                    onClick = onTestSosClick,
                    style = ButtonStyle.Tinted,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Pill badge displaying live BLE connection state with a slow, pulsing status dot.
 */
@Composable
fun LiveConnectionPill(
    status: BleStatus,
    deviceName: String?,
    onClick: () -> Unit
) {
    val colors = CareBuddyTheme.colors

    val dotColor = when (status) {
        is BleStatus.Connected -> colors.success
        is BleStatus.Connecting, is BleStatus.SettingUp, is BleStatus.Scanning -> colors.warning
        is BleStatus.DeviceNotFound, is BleStatus.LocationOff -> colors.warning
        is BleStatus.PermissionNeeded -> colors.danger
        else -> colors.tintGray
    }

    val stateLabel = when (status) {
        is BleStatus.Connected -> deviceName ?: "Connected"
        is BleStatus.Connecting -> "Connecting…"
        is BleStatus.SettingUp -> "Setting up…"
        is BleStatus.Scanning -> "Searching…"
        is BleStatus.DeviceNotFound -> "Not found"
        is BleStatus.BluetoothOff -> "Bluetooth off"
        is BleStatus.PermissionNeeded -> "Permission needed"
        is BleStatus.LocationOff -> "Location off"
        is BleStatus.Disconnected -> "Offline"
        is BleStatus.Idle -> "Idle"
    }

    val isPulsing = status is BleStatus.Connecting || status is BleStatus.SettingUp || status is BleStatus.Scanning

    // Slow calm pulse for connecting / setting up / scanning states
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(fullPillRadius))
            .background(colors.glassSurfaceSubtle)
            .pressScale(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isPulsing) {
                        dotColor.copy(alpha = pulseAlpha)
                    } else dotColor
                )
        )
        Text(
            text = stateLabel,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            ),
            color = colors.textPrimary
        )
    }
}
