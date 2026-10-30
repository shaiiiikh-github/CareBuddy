package com.carebuddy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carebuddy.app.ble.BleConnectionState
import com.carebuddy.app.ui.theme.*
import com.carebuddy.app.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val currentSosMsg by viewModel.sosCustomMessage.collectAsState()

    var sosMessageText by remember { mutableStateOf(currentSosMsg) }
    var minTouchMs by remember { mutableFloatStateOf(80f) }
    var sosHoldMs by remember { mutableFloatStateOf(3000f) }

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

            // Custom SOS Alert Message
            Text("Emergency Message Template", style = MaterialTheme.typography.titleLarge, color = TextSecondary)
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

                    // Explanation Note regarding Physical TTP223 Hardware Sensitivity
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
                            text = "Note: Software thresholds tune debouncing and press timers. Physical capacitive sensitivity is determined by the hardware capacitor (C3) on your TTP223 module.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
