package com.carebuddy.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebuddy.app.ble.BleConnectionState
import com.carebuddy.app.ui.components.*
import com.carebuddy.app.ui.theme.CareBuddyTheme
import com.carebuddy.app.viewmodel.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val currentSosMsg by viewModel.sosCustomMessage.collectAsState()
    val contacts by viewModel.emergencyContacts.collectAsState()

    var sosMessageText by remember { mutableStateOf(currentSosMsg) }
    var minTouchMs by remember { mutableFloatStateOf(80f) }
    var sosHoldMs by remember { mutableFloatStateOf(3000f) }
    var diagnostics by remember { mutableStateOf(viewModel.getDiagnosticsInfo()) }

    // Refresh diagnostics when screen is active
    LaunchedEffect(Unit) {
        diagnostics = viewModel.getDiagnosticsInfo()
    }

    val colors = CareBuddyTheme.colors

    LargeTitleScaffold(
        title = "Settings",
        subtitle = "Device preferences, emergency message, and system diagnostics"
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Device Connection
            SectionHeader(text = "CareBuddy Companion")
            InsetGroupedList {
                ListRow(
                    title = deviceName ?: "CareBuddy Device",
                    subtitle = when (connectionState) {
                        BleConnectionState.CONNECTED -> "Paired and transmitting"
                        BleConnectionState.CONNECTING,
                        BleConnectionState.RECONNECTING -> "Scanning for device…"
                        BleConnectionState.DISCONNECTED -> "Device disconnected"
                    },
                    icon = Icons.Outlined.Bluetooth,
                    iconBackgroundColor = colors.accent,
                    trailing = {
                        StatusPill(
                            label = when (connectionState) {
                                BleConnectionState.CONNECTED -> "Connected"
                                BleConnectionState.CONNECTING -> "Connecting"
                                BleConnectionState.RECONNECTING -> "Retrying"
                                BleConnectionState.DISCONNECTED -> "Offline"
                            },
                            dotColor = when (connectionState) {
                                BleConnectionState.CONNECTED -> colors.success
                                BleConnectionState.DISCONNECTED -> colors.tintGray
                                else -> colors.warning
                            }
                        )
                    }
                )

                ListRow(
                    title = if (connectionState == BleConnectionState.CONNECTED) "Disconnect device" else "Connect device",
                    subtitle = "Manage BLE GATT link",
                    icon = if (connectionState == BleConnectionState.CONNECTED) Icons.Outlined.BluetoothDisabled else Icons.AutoMirrored.Outlined.BluetoothSearching,
                    iconBackgroundColor = if (connectionState == BleConnectionState.CONNECTED) colors.tintGray else colors.accent,
                    showChevron = true,
                    showDivider = false,
                    onClick = {
                        if (connectionState == BleConnectionState.CONNECTED) {
                            viewModel.disconnectDevice()
                        } else {
                            viewModel.connectDevice()
                        }
                    }
                )
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

            // Section 3: Hardware Preferences
            SectionHeader(text = "Hardware Sensitivity")
            InsetGroupedList {
                ListRow(
                    title = "Minimum touch press",
                    subtitle = "${minTouchMs.toInt()} ms debounce window",
                    icon = Icons.Outlined.TouchApp,
                    iconBackgroundColor = colors.warning,
                    trailing = {
                        Text(
                            text = "${minTouchMs.toInt()} ms",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                )
                ListRow(
                    title = "SOS hold countdown",
                    subtitle = "${(sosHoldMs / 1000).toInt()} seconds hold to trigger alarm",
                    icon = Icons.Outlined.Timer,
                    iconBackgroundColor = colors.danger,
                    showDivider = false,
                    trailing = {
                        Text(
                            text = "${(sosHoldMs / 1000).toInt()} s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    }
                )
            }

            // Section 4: System Diagnostics
            SectionHeader(text = "Diagnostics & Permissions")
            InsetGroupedList {
                ListRow(
                    title = "Bluetooth link",
                    subtitle = "BLE radio state: ${diagnostics.bleState}",
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

            // Test SMS Action
            CapsuleButton(
                text = "Send Test SMS to Primary Contact",
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
                modifier = Modifier.fillMaxWidth()
            )

            // Section 5: About App
            SectionHeader(text = "About CareBuddy")
            InsetGroupedList {
                ListRow(
                    title = "Application version",
                    subtitle = "1.0.0 (Build 1)",
                    icon = Icons.Outlined.Info,
                    iconBackgroundColor = colors.tintGray,
                    showDivider = false,
                    trailing = {
                        Text(
                            text = "Release v1.0.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                )
            }
        }
    }
}
