package com.carebuddy.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carebuddy.app.ble.BleConnectionState
import com.carebuddy.app.ui.theme.*
import com.carebuddy.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    // Refresh diagnostics when screen is opened
    LaunchedEffect(Unit) {
        diagnostics = viewModel.getDiagnosticsInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Settings", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDark)
            )
        },
        containerColor = SlateDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // BLE Device Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, tint = TealLight)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = deviceName ?: "CareBuddy Device",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Status: $connectionState",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (connectionState == BleConnectionState.CONNECTED) GreenSuccess else TextSecondary
                                )
                            }
                        }

                        Button(
                            onClick = {
                                if (connectionState == BleConnectionState.CONNECTED) {
                                    viewModel.disconnectDevice()
                                } else {
                                    viewModel.connectDevice()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (connectionState == BleConnectionState.CONNECTED) RedEmergencyDark else TealLight
                            )
                        ) {
                            Text(
                                if (connectionState == BleConnectionState.CONNECTED) "Disconnect" else "Connect",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.syncTimeToDevice() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SlateCardBorder)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Synchronize Time with ESP32", color = TextPrimary)
                    }
                }
            }

            // System Diagnostics Section
            Text("System Diagnostics & Permissions", style = MaterialTheme.typography.titleLarge, color = TextSecondary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DiagnosticItem("Send SMS Permission", diagnostics.sendSmsGranted)
                    DiagnosticItem("Call Phone Permission", diagnostics.callPhoneGranted)
                    DiagnosticItem("Post Notifications", diagnostics.postNotificationsGranted)
                    DiagnosticItem("Bluetooth / Scan Permissions", diagnostics.bluetoothGranted)
                    DiagnosticItem("Battery Optimization Exempt", diagnostics.batteryOptimizationExempt)
                    DiagnosticItem("Full-Screen Intent Allowed", diagnostics.fullScreenIntentAllowed)

                    HorizontalDivider(color = SlateCardBorder)

                    Text(
                        text = "Last SOS Result: ${diagnostics.lastSosResult}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )

                    // Battery Optimization Exemption Button if not exempt
                    if (!diagnostics.batteryOptimizationExempt) {
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.BatteryAlert, contentDescription = null, tint = SlateDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exempt from Battery Optimization", color = SlateDark, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Test SMS Transmission Button
                    val primaryContact = contacts.firstOrNull { it.isPrimary } ?: contacts.firstOrNull()
                    Button(
                        onClick = {
                            if (primaryContact != null) {
                                viewModel.sendTestSmsToContact(primaryContact) { _, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    diagnostics = viewModel.getDiagnosticsInfo()
                                }
                            } else {
                                Toast.makeText(context, "Please add an emergency contact first!", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealLight),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null, tint = SlateDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (primaryContact != null) "Send Test SMS to ${primaryContact.name}" else "Send Test SMS (No Contact)",
                            color = SlateDark,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Note: 'Test SOS' on the dashboard simulates the OLED/BLE alarm without sending SMS. The button above verifies your phone's carrier SMS dispatch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }
            }

            // Custom SOS Alert Message
            Text("Emergency Message Template (GSM-7)", style = MaterialTheme.typography.titleLarge, color = TextSecondary)
            OutlinedTextField(
                value = sosMessageText,
                onValueChange = {
                    sosMessageText = it
                    viewModel.saveSosCustomMessage(it)
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealLight,
                    unfocusedBorderColor = SlateCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Touch Sensitivity Threshold Settings
            Text("Software Touch Thresholds", style = MaterialTheme.typography.titleLarge, color = TextSecondary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Min Touch Duration: ${minTouchMs.toInt()} ms",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Slider(
                        value = minTouchMs,
                        onValueChange = { minTouchMs = it },
                        valueRange = 50f..300f,
                        colors = SliderDefaults.colors(thumbColor = TealLight, activeTrackColor = TealAccent)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "SOS Countdown Hold: ${sosHoldMs.toInt() / 1000} seconds",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Slider(
                        value = sosHoldMs,
                        onValueChange = { sosHoldMs = it },
                        valueRange = 2000f..5000f,
                        colors = SliderDefaults.colors(thumbColor = TealLight, activeTrackColor = TealAccent)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SlateDark, RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Note: Software thresholds tune debouncing and press timers. Physical capacitive sensitivity is determined by capacitor C3 on your TTP223 module.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }

            // OEM Background Help
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "OEM Background Optimization Notice",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "On devices with custom Android skins (Xiaomi MIUI/HyperOS, Oppo ColorOS, Vivo Funtouch, Samsung OneUI):\n\n" +
                                "1. Set CareBuddy's App Battery usage to 'Unrestricted'.\n" +
                                "2. Enable 'Autostart' or 'Allow background activity' in App Info.\n" +
                                "3. Lock CareBuddy in the Recent Apps overview so the BLE connection remains continuous when screen turns off.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticItem(title: String, granted: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                tint = if (granted) GreenSuccess else RedEmergency,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (granted) "OK" else "Missing",
                style = MaterialTheme.typography.bodySmall,
                color = if (granted) GreenSuccess else RedEmergency,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
