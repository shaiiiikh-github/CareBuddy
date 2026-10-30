package com.carebuddy.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carebuddy.app.ui.theme.*
import com.carebuddy.app.viewmodel.MainViewModel

@Composable
fun SosAlertScreen(viewModel: MainViewModel) {
    val sosState by viewModel.sosAlertState.collectAsState()

    if (!sosState.isTriggered) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Icon & Banner
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alert",
                    tint = if (sosState.isTestMode) AmberWarning else RedEmergency,
                    modifier = Modifier.size(64.dp)
                )

                Text(
                    text = if (sosState.isTestMode) "🧪 TEST SOS ALERT" else "🚨 EMERGENCY SOS ACTIVATED!",
                    style = MaterialTheme.typography.headlineLarge,
                    color = if (sosState.isTestMode) AmberWarning else RedEmergency,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (sosState.isTestMode)
                        "Test mode active — No real emergency call or SMS will be dispatched automatically unless you tap below."
                    else
                        "CareBuddy user has held the SOS button! Take immediate action below.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Divider(color = SlateCardBorder)

                // Contact Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Primary Emergency Contact",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Text(
                        text = sosState.contactName.ifEmpty { "Default Contact" },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = sosState.contactPhone.ifEmpty { "N/A" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = TealGlow
                    )
                }

                // Action Status Badges
                if (sosState.smsStatus != "IDLE" || sosState.callStatus != "IDLE") {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SlateDark
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (sosState.smsStatus != "IDLE") {
                                Text(
                                    text = "SMS Status: ${sosState.smsStatus}",
                                    color = if (sosState.smsStatus == "FAILED") RedEmergency else GreenSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (sosState.callStatus != "IDLE") {
                                Text(
                                    text = "Call Status: ${sosState.callStatus}",
                                    color = if (sosState.callStatus == "FAILED") RedEmergency else GreenSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.sendSosSmsDirectly() },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealLight),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null, tint = SlateDark)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send SMS", color = SlateDark, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.initiatePhoneCall(sosState.contactPhone) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedEmergency),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.dismissSosAlert() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Dismiss / Cancel")
                }
            }
        }
    }
}
