package com.carebuddy.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carebuddy.app.data.Reminder
import com.carebuddy.app.data.RepeatOption
import com.carebuddy.app.ui.theme.*
import com.carebuddy.app.viewmodel.MainViewModel
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditReminderScreen(
    viewModel: MainViewModel,
    existingReminderId: String?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val reminders by viewModel.reminders.collectAsState()
    val existing = reminders.find { it.id == existingReminderId }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Medication") }
    var repeat by remember { mutableStateOf(existing?.repeat ?: RepeatOption.DAILY) }

    var hour by remember { mutableStateOf(existing?.minutesOfDay?.div(60) ?: 10) }
    var minute by remember { mutableStateOf(existing?.minutesOfDay?.rem(60) ?: 0) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val formattedTime = remember(hour, minute) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
        val displayHour = if (cal.get(Calendar.HOUR) == 0) 12 else cal.get(Calendar.HOUR)
        String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, amPm)
    }

    val categories = listOf("Medication", "Water", "Activity", "Custom")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add Reminder" else "Edit Reminder", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDark)
            )
        },
        containerColor = SlateDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Title Input
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    validationError = null
                },
                label = { Text("Reminder Title", color = TextSecondary) },
                placeholder = { Text("e.g. Take Blood Pressure Pill") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealLight,
                    unfocusedBorderColor = SlateCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Category Selector
            Text("Category", style = MaterialTheme.typography.titleLarge, color = TextSecondary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = category == cat,
                        onClick = { category = cat },
                        label = { Text(cat) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealAccent,
                            selectedLabelColor = SlateDark,
                            containerColor = SlateCard,
                            labelColor = TextPrimary
                        )
                    )
                }
            }

            // Time Selector Card
            Text("Time", style = MaterialTheme.typography.titleLarge, color = TextSecondary)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                hour = h
                                minute = m
                            },
                            hour,
                            minute,
                            false
                        ).show()
                    },
                colors = CardDefaults.cardColors(containerColor = SlateCard),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = TealLight)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Scheduled Time", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                    }
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TealGlow,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Repeat Option Selector
            Text("Repeat Schedule", style = MaterialTheme.typography.titleLarge, color = TextSecondary)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                RepeatOption.values().forEach { opt ->
                    FilterChip(
                        selected = repeat == opt,
                        onClick = { repeat = opt },
                        label = { Text(opt.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = TealAccent,
                            selectedLabelColor = SlateDark,
                            containerColor = SlateCard,
                            labelColor = TextPrimary
                        )
                    )
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    color = RedEmergency,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Buttons
            Button(
                onClick = {
                    if (title.isBlank()) {
                        validationError = "Please enter a valid title for the reminder."
                        return@Button
                    }

                    val minutesOfDay = hour * 60 + minute
                    val newReminder = Reminder(
                        id = existing?.id ?: System.currentTimeMillis().toString().takeLast(8),
                        title = title.trim(),
                        time = formattedTime,
                        minutesOfDay = minutesOfDay,
                        repeat = repeat,
                        enabled = true,
                        category = category
                    )

                    if (existing == null) {
                        viewModel.addReminder(newReminder)
                    } else {
                        viewModel.updateReminder(newReminder)
                    }
                    onNavigateBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealLight),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Save Reminder", color = SlateDark, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}
