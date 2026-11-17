package com.carebuddy.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebuddy.app.BuildConfig
import com.carebuddy.app.ble.BleScanTestResult
import com.carebuddy.app.ble.BleStatus
import com.carebuddy.app.ui.components.*
import com.carebuddy.app.ui.theme.CareBuddyTheme
import com.carebuddy.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val bleStatus by viewModel.bleStatus.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val currentSosMsg by viewModel.sosCustomMessage.collectAsState()
    val contacts by viewModel.emergencyContacts.collectAsState()

    var sosMessageText by remember(currentSosMsg) { mutableStateOf(currentSosMsg) }
    var diagnostics by remember { mutableStateOf(viewModel.getDiagnosticsInfo()) }

    var isRunningScanTest by remember { mutableStateOf(false) }
    var scanTestResults by remember { mutableStateOf<List<BleScanTestResult>>(emptyList()) }
    var showEventLog by remember { mutableStateOf(false) }
    var eventLogs by remember { mutableStateOf<List<String>>(emptyList()) }

    // Refresh diagnostics when screen is active
    LaunchedEffect(Unit) {
        diagnostics = viewModel.getDiagnosticsInfo()
    }

    val colors = CareBuddyTheme.colors

    LargeTitleScaffold(
        title = "Settings",
        subtitle = "Device link, emergency preferences, and real-time diagnostics"
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Real-Time BLE Connection Status
            SectionHeader(text = "CareBuddy Companion Link")
            InsetGroupedList {
                ListRow(
                    title = deviceName ?: "CareBuddy Device",
                    subtitle = when (bleStatus) {
                        is BleStatus.Connected -> "Paired and synchronized over BLE"
                        is BleStatus.Connecting -> "Establishing GATT link…"
                        is BleStatus.SettingUp -> (bleStatus as BleStatus.SettingUp).stage
                        is BleStatus.Scanning -> "Scanning for advertising CareBuddy devices…"
                        is BleStatus.DeviceNotFound -> "CareBuddy not found. Is it powered on and nearby?"
                        is BleStatus.BluetoothOff -> "Bluetooth adapter is turned off"
                        is BleStatus.PermissionNeeded -> "Bluetooth scan/connect permissions required"
                        is BleStatus.LocationOff -> "Location services must be enabled for BLE scanning"
                        is BleStatus.Disconnected -> (bleStatus as BleStatus.Disconnected).reason ?: "Disconnected"
                        is BleStatus.Idle -> "Idle"
                    },
                    icon = Icons.Outlined.Bluetooth,
                    iconBackgroundColor = if (bleStatus is BleStatus.Connected) colors.accent else colors.tintGray,
                    trailing = {
                        val (dotColor, label) = when (bleStatus) {
                            is BleStatus.Connected -> colors.success to "Connected"
                            is BleStatus.Connecting -> colors.warning to "Connecting"
                            is BleStatus.SettingUp -> colors.warning to "Setting up"
                            is BleStatus.Scanning -> colors.warning to "Searching"
                            is BleStatus.DeviceNotFound -> colors.warning to "Not found"
                            is BleStatus.BluetoothOff -> colors.tintGray to "BT off"
                            is BleStatus.PermissionNeeded -> colors.danger to "Permission"
                            is BleStatus.LocationOff -> colors.warning to "Location off"
                            is BleStatus.Disconnected -> colors.tintGray to "Offline"
                            is BleStatus.Idle -> colors.tintGray to "Idle"
                        }
                        StatusPill(label = label, dotColor = dotColor)
                    }
                )

                // Action row for connection recovery
                if (bleStatus.actionLabel != null || bleStatus is BleStatus.Connected) {
                    ListRow(
                        title = if (bleStatus is BleStatus.Connected) "Disconnect from device" else (bleStatus.actionLabel ?: "Connect"),
                        subtitle = if (bleStatus is BleStatus.Connected) "Terminate active Bluetooth link" else "Resolve status and reconnect",
                        icon = if (bleStatus is BleStatus.Connected) Icons.Outlined.BluetoothDisabled else Icons.AutoMirrored.Outlined.BluetoothSearching,
                        iconBackgroundColor = if (bleStatus is BleStatus.Connected) colors.tintGray else colors.accent,
                        showChevron = true,
                        showDivider = false,
                        onClick = {
                            when (bleStatus) {
                                is BleStatus.Connected -> viewModel.disconnectDevice()
                                is BleStatus.BluetoothOff -> {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                                is BleStatus.LocationOff -> {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                                is BleStatus.PermissionNeeded -> {
                                    try {
                                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = Uri.fromParts("package", context.packageName, null)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                                else -> viewModel.connectDevice()
                            }
                        }
                    )
                }
            }

            // Section 2: Custom Emergency Message
            SectionHeader(text = "Emergency SMS Message")
            IosTextField(
                value = sosMessageText,
                onValueChange = {
                    sosMessageText = it
                    viewModel.saveSosCustomMessage(it)
                },
                placeholder = "Custom SOS message text",
                leadingIcon = Icons.Outlined.Sms
            )

            // Section 3: System Diagnostics
            SectionHeader(text = "System Diagnostics")
            InsetGroupedList {
                ListRow(
                    title = "Bluetooth link",
                    subtitle = "Radio state: ${diagnostics.bleState}",
                    icon = Icons.Outlined.Bluetooth,
                    iconBackgroundColor = colors.accent,
                    trailing = {
                        StatusPill(
                            label = if (diagnostics.bluetoothGranted) "Ready" else "Disabled",
                            dotColor = if (diagnostics.bluetoothGranted) colors.success else colors.danger
                        )
                    }
                )

                ListRow(
                    title = "Telephony permissions",
                    subtitle = if (diagnostics.callPhoneGranted && diagnostics.sendSmsGranted) "Calls & SMS allowed" else "Action required",
                    icon = Icons.Outlined.PhoneInTalk,
                    iconBackgroundColor = colors.success,
                    trailing = {
                        val ok = diagnostics.callPhoneGranted && diagnostics.sendSmsGranted
                        StatusPill(
                            label = if (ok) "Granted" else "Denied",
                            dotColor = if (ok) colors.success else colors.danger
                        )
                    }
                )

                ListRow(
                    title = "Background battery optimization",
                    subtitle = if (diagnostics.batteryOptimizationExempt) "Unrestricted background runs" else "May be paused by Android OS",
                    icon = Icons.Outlined.BatterySaver,
                    iconBackgroundColor = if (diagnostics.batteryOptimizationExempt) colors.success else colors.warning,
                    showChevron = !diagnostics.batteryOptimizationExempt,
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    trailing = {
                        StatusPill(
                            label = if (diagnostics.batteryOptimizationExempt) "Whitelisted" else "Optimized",
                            dotColor = if (diagnostics.batteryOptimizationExempt) colors.success else colors.warning
                        )
                    }
                )

                ListRow(
                    title = "Last SOS dispatch",
                    subtitle = diagnostics.lastSosResult,
                    icon = Icons.Outlined.History,
                    iconBackgroundColor = colors.tintGray,
                    showDivider = false
                )
            }

            // Actions: Test SMS & BLE Diagnostics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CapsuleButton(
                    text = "Test SMS",
                    icon = Icons.AutoMirrored.Outlined.Send,
                    onClick = {
                        val primary = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()
                        if (primary == null) {
                            Toast.makeText(context, "Please add an emergency contact first", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.sendTestSmsToContact(primary) { success, msg ->
                                Toast.makeText(context, if (success) "Test SMS queued to ${primary.phoneNumber}" else "Failed: $msg", Toast.LENGTH_LONG).show()
                                diagnostics = viewModel.getDiagnosticsInfo()
                            }
                        }
                    },
                    style = ButtonStyle.Tinted,
                    modifier = Modifier.weight(1f)
                )

                CapsuleButton(
                    text = if (isRunningScanTest) "Scanning…" else "Scan Test",
                    icon = Icons.Outlined.Search,
                    onClick = {
                        if (!isRunningScanTest) {
                            isRunningScanTest = true
                            scanTestResults = emptyList()
                            viewModel.runBleScanTest { results ->
                                scanTestResults = results
                                isRunningScanTest = false
                            }
                        }
                    },
                    style = ButtonStyle.Tinted,
                    modifier = Modifier.weight(1f)
                )
            }

            // Scan Test Live Results
            AnimatedVisibility(visible = scanTestResults.isNotEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scan Test Results (${scanTestResults.size} nearby)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary
                            )
                            IconButton(onClick = { scanTestResults = emptyList() }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = colors.textTertiary)
                            }
                        }

                        scanTestResults.forEach { result ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (result.matchesCareBuddy) colors.accentSubtle else colors.glassSurfaceSubtle)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = result.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "${result.maskedAddress} • ${result.rssi} dBm",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary
                                    )
                                }
                                if (result.matchesCareBuddy) {
                                    StatusPill(label = "Match", dotColor = colors.success)
                                }
                            }
                        }
                    }
                }
            }

            // Real-Time In-App Event Log Viewer
            SectionHeader(text = "BLE Event Log")
            InsetGroupedList {
                ListRow(
                    title = if (showEventLog) "Hide Event Log" else "View Live BLE Log",
                    subtitle = "Last 200 tagged state changes, GATT status codes, and watchdogs",
                    icon = Icons.Outlined.Terminal,
                    iconBackgroundColor = colors.tintGray,
                    showChevron = true,
                    showDivider = false,
                    onClick = {
                        showEventLog = !showEventLog
                        if (showEventLog) {
                            eventLogs = viewModel.getBleEventLogs()
                        }
                    }
                )
            }

            AnimatedVisibility(visible = showEventLog) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Events (${eventLogs.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CapsuleButton(
                                    text = "Refresh",
                                    onClick = { eventLogs = viewModel.getBleEventLogs() },
                                    style = ButtonStyle.Plain,
                                    minHeight = 32.dp
                                )
                                CapsuleButton(
                                    text = "Copy",
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("CareBuddy BLE Log", eventLogs.joinToString("\n"))
                                        cm.setPrimaryClip(clip)
                                        Toast.makeText(context, "BLE log copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    style = ButtonStyle.Tinted,
                                    minHeight = 32.dp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (eventLogs.isEmpty()) {
                                Text(
                                    text = "No events logged yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            } else {
                                Text(
                                    text = eventLogs.joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    ),
                                    color = Color(0xFF64D2FF)
                                )
                            }
                        }
                    }
                }
            }

            // Section 4: About App
            SectionHeader(text = "About CareBuddy")
            InsetGroupedList {
                ListRow(
                    title = "Application version",
                    subtitle = "${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • Release",
                    icon = Icons.Outlined.Info,
                    iconBackgroundColor = colors.tintGray,
                    showDivider = false,
                    trailing = {
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                )
            }
        }
    }
}
