package com.carebuddy.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebuddy.app.sos.SmsDeliveryStatus
import com.carebuddy.app.ui.components.*
import com.carebuddy.app.ui.theme.CareBuddyTheme
import com.carebuddy.app.viewmodel.MainViewModel

@Composable
fun SosAlertScreen(viewModel: MainViewModel) {
    val dispatchState by viewModel.sosDispatchState.collectAsState()
    val contacts by viewModel.emergencyContacts.collectAsState()

    if (!dispatchState.isTriggered) return

    val isTest = dispatchState.isTestMode
    val isLive = !isTest

    // Solid high-legibility container
    val alertBg = if (isLive) Color(0xFF140202) else Color(0xFF120E02)
    val cardBg = if (isLive) Color(0xFF240A0A) else Color(0xFF241C0A)
    val accentRed = if (isLive) Color(0xFFFF453A) else Color(0xFFFF9F0A)

    // Calm, slow breathing pulse for the warning icon
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos_scale"
    )

    val primaryNumber = dispatchState.callTargetNumber
        ?: contacts.firstOrNull { it.isPrimary }?.phoneNumber
        ?: contacts.firstOrNull()?.phoneNumber

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(alertBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Calm Pulser Icon
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(accentRed.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLive) Icons.Outlined.Emergency else Icons.Outlined.Science,
                    contentDescription = "Alert",
                    tint = accentRed,
                    modifier = Modifier.size((44 * pulseScale).dp)
                )
            }

            Text(
                text = if (isLive) "Emergency SOS Triggered" else "Hardware SOS Test Active",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isLive)
                    "CareBuddy has activated emergency procedures. Automated alerts and notifications are in progress."
                else
                    "Device sound alarm and display testing active. Automated calls and text messages are suppressed.",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )

            // General Error Banner (if any)
            if (!dispatchState.generalError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Red.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = dispatchState.generalError ?: "",
                        color = Color(0xFFFFB4AB),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.padding(14.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Per-Contact Status Section
            if (isLive && dispatchState.contactsStatus.isNotEmpty()) {
                SectionHeader(text = "Notification Dispatch Status")
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    dispatchState.contactsStatus.forEach { item ->
                        val (statusText, dotColor) = when (item.status) {
                            SmsDeliveryStatus.IDLE -> "Queued" to Color.Gray
                            SmsDeliveryStatus.SENDING -> "Sending…" to Color(0xFFFF9F0A)
                            SmsDeliveryStatus.SENT -> "Sent" to Color(0xFF64D2FF)
                            SmsDeliveryStatus.DELIVERED -> "Delivered" to Color(0xFF30D158)
                            SmsDeliveryStatus.FAILED -> "Failed" to Color(0xFFFF453A)
                        }

                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = cardBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.contactName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        text = item.phoneNumber,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                        color = Color.White.copy(alpha = 0.6f)
                                    )
                                    if (!item.failureReason.isNullOrBlank()) {
                                        Text(
                                            text = item.failureReason,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = Color(0xFFFF453A)
                                        )
                                    }
                                }
                                StatusPill(
                                    label = statusText,
                                    dotColor = dotColor,
                                    backgroundColor = Color.Black.copy(alpha = 0.4f),
                                    textColor = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action: Fallback manual SMS app launch if automatic send fails
            if (isLive && (dispatchState.fallbackRequired || dispatchState.contactsStatus.any { it.status == SmsDeliveryStatus.FAILED })) {
                CapsuleButton(
                    text = "Open Messaging App",
                    icon = Icons.Outlined.Sms,
                    onClick = {
                        val target = primaryNumber ?: ""
                        viewModel.openSmsAppFallback(
                            phoneNumber = target,
                            text = "CareBuddy Emergency Alert: SOS triggered. Please check immediately."
                        )
                    },
                    style = ButtonStyle.Tinted,
                    minHeight = 56.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Action: Direct Call Button (>= 56dp)
            if (isLive && !primaryNumber.isNullOrBlank()) {
                CapsuleButton(
                    text = "Call Primary Contact",
                    icon = Icons.Outlined.Phone,
                    onClick = { viewModel.initiatePhoneCall(primaryNumber) },
                    style = ButtonStyle.Filled,
                    minHeight = 56.dp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Action: Dismiss / Cancel
            CapsuleButton(
                text = if (isLive) "Cancel Emergency Alarm" else "Dismiss Test",
                onClick = { viewModel.dismissSosAlert() },
                style = ButtonStyle.Plain,
                minHeight = 56.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
