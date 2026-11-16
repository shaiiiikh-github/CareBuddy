package com.carebuddy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.carebuddy.app.sos.SmsDeliveryStatus
import com.carebuddy.app.ui.theme.*
import com.carebuddy.app.viewmodel.MainViewModel

@Composable
fun SosAlertScreen(viewModel: MainViewModel) {
    val dispatchState by viewModel.sosDispatchState.collectAsState()

    if (!dispatchState.isTriggered) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = SlateCard),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Icon & Title
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alert",
                    tint = if (dispatchState.isTestMode) AmberWarning else RedEmergency,
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = if (dispatchState.isTestMode) "🧪 TEST SOS ALERT" else "🚨 EMERGENCY SOS ACTIVATED!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (dispatchState.isTestMode) AmberWarning else RedEmergency,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (dispatchState.isTestMode)
                        "Test mode active — Simulated hardware alarm. No emergency calls or SMS will be sent automatically."
                    else
                        "CareBuddy SOS triggered! Emergency notifications are being dispatched to all contacts below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = SlateCardBorder)

                // Error / Warning Banner
                if (!dispatchState.generalError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = RedEmergencyDark.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = dispatchState.generalError ?: "",
                            color = RedEmergency,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Per-Contact Status List
                Text(
                    text = "Emergency Contacts Notification Status:",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.Start)
                )

                if (dispatchState.contactsStatus.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SlateDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ No emergency contacts configured!\nPlease add contacts in Settings or Contacts tab.",
                            color = AmberWarning,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(dispatchState.contactsStatus) { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SlateDark,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = item.contactName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = TextPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (item.isPrimary) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = TealAccent.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        text = "PRIMARY",
                                                        color = TealLight,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = item.phoneNumber,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextMuted
                                        )
                                    }

                                    // Status Badge
                                    val (badgeText, badgeColor) = when (item.status) {
                                        SmsDeliveryStatus.IDLE -> Pair("IDLE", TextMuted)
                                        SmsDeliveryStatus.SENDING -> Pair("Sending...", TealLight)
                                        SmsDeliveryStatus.SENT -> Pair("Sent ✓", GreenSuccess)
                                        SmsDeliveryStatus.DELIVERED -> Pair("Delivered ✓✓", GreenSuccess)
                                        SmsDeliveryStatus.FAILED -> Pair(
                                            "Failed: ${item.failureReason ?: "Error"}",
                                            RedEmergency
                                        )
                                    }

                                    Text(
                                        text = badgeText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Fallback Intent Button if SMS failed or permissions denied
                if (dispatchState.fallbackRequired && dispatchState.contactsStatus.isNotEmpty()) {
                    val firstContact = dispatchState.contactsStatus.firstOrNull { it.isPrimary }
                        ?: dispatchState.contactsStatus.first()
                    Button(
                        onClick = {
                            viewModel.openSmsAppFallback(
                                firstContact.phoneNumber,
                                "SOS from CareBuddy: Emergency alert! Please check immediately."
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null, tint = SlateDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Open Messages App Fallback",
                            color = SlateDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val primaryNumber = dispatchState.callTargetNumber
                        ?: dispatchState.contactsStatus.firstOrNull { it.isPrimary }?.phoneNumber
                        ?: dispatchState.contactsStatus.firstOrNull()?.phoneNumber

                    Button(
                        onClick = {
                            if (!primaryNumber.isNullOrBlank()) {
                                viewModel.initiatePhoneCall(primaryNumber)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedEmergency),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !primaryNumber.isNullOrBlank()
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, tint = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Call Primary", color = TextPrimary, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.dismissSosAlert() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}
