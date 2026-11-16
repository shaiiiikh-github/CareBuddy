package com.carebuddy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebuddy.app.data.Reminder
import com.carebuddy.app.data.RepeatOption
import com.carebuddy.app.ui.components.*
import com.carebuddy.app.ui.theme.CareBuddyTheme
import com.carebuddy.app.ui.theme.pressScale
import com.carebuddy.app.viewmodel.MainViewModel
import java.util.*

@Composable
fun AddEditReminderScreen(
    viewModel: MainViewModel,
    existingReminderId: String?,
    onNavigateBack: () -> Unit
) {
    val reminders by viewModel.reminders.collectAsState()
    val existing = reminders.find { it.id == existingReminderId }

    var title by remember { mutableStateOf(existing?.title ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "Medication") }
    var repeat by remember { mutableStateOf(existing?.repeat ?: RepeatOption.DAILY) }

    var hour by remember { mutableIntStateOf(existing?.minutesOfDay?.div(60) ?: 9) }
    var minute by remember { mutableIntStateOf(existing?.minutesOfDay?.rem(60) ?: 0) }
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
    val repeatOptions = listOf(RepeatOption.DAILY, RepeatOption.WEEKDAYS, RepeatOption.CUSTOM)

    val colors = CareBuddyTheme.colors

    LargeTitleScaffold(
        title = if (existing == null) "New Reminder" else "Edit Reminder",
        subtitle = "Acoustic chimes and eye alerts on CareBuddy",
        trailingAction = {
            CapsuleButton(
                text = "Cancel",
                onClick = onNavigateBack,
                style = ButtonStyle.Plain,
                minHeight = 36.dp
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Native-feeling Time Picker Glass Card
            SectionHeader(text = "Schedule Time")
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = colors.accent
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hour and Minute Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeStepper(
                            label = "Hour",
                            value = if (hour % 12 == 0) 12 else hour % 12,
                            onIncrement = { hour = (hour + 1) % 24 },
                            onDecrement = { hour = if (hour == 0) 23 else hour - 1 }
                        )

                        TimeStepper(
                            label = "Minute",
                            value = minute,
                            onIncrement = { minute = (minute + 5) % 60 },
                            onDecrement = { minute = if (minute < 5) 55 else minute - 5 }
                        )

                        // AM/PM Toggle
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Period",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(fullPillRadius))
                                    .background(colors.switchTrackOff)
                                    .padding(2.dp)
                            ) {
                                val isAm = hour < 12
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(fullPillRadius))
                                        .background(if (isAm) colors.accent else Color.Transparent)
                                        .clickable { if (!isAm) hour -= 12 }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "AM",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isAm) Color.White else colors.textSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(fullPillRadius))
                                        .background(if (!isAm) colors.accent else Color.Transparent)
                                        .clickable { if (isAm) hour += 12 }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "PM",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (!isAm) Color.White else colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Title Input
            SectionHeader(text = "Reminder Details")
            IosTextField(
                value = title,
                onValueChange = {
                    title = it
                    validationError = null
                },
                placeholder = "Title (e.g. Heart Medication)",
                leadingIcon = Icons.Outlined.Edit,
                errorMessage = validationError
            )

            // Category Chips
            SectionHeader(text = "Category")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSelected = category.equals(cat, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(fullPillRadius))
                            .background(if (isSelected) colors.accent else colors.switchTrackOff.copy(alpha = 0.6f))
                            .pressScale(onClick = { category = cat })
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            color = if (isSelected) Color.White else colors.textPrimary
                        )
                    }
                }
            }

            // Repeat Option
            SectionHeader(text = "Repeat Schedule")
            val selectedRepeatIndex = repeatOptions.indexOf(repeat).coerceAtLeast(0)
            SegmentedControl(
                items = repeatOptions,
                selectedIndex = selectedRepeatIndex,
                onItemSelected = { index -> repeat = repeatOptions[index] },
                itemLabel = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Primary Action
            CapsuleButton(
                text = if (existing == null) "Create Reminder" else "Save Changes",
                onClick = {
                    if (title.isBlank()) {
                        validationError = "Please enter a reminder title"
                        return@CapsuleButton
                    }
                    val reminder = Reminder(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        title = title.trim(),
                        time = formattedTime,
                        minutesOfDay = hour * 60 + minute,
                        repeat = repeat,
                        category = category,
                        enabled = true
                    )
                    if (existing != null) {
                        viewModel.updateReminder(reminder)
                    } else {
                        viewModel.addReminder(reminder)
                    }
                    onNavigateBack()
                },
                style = ButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TimeStepper(
    label: String,
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val colors = CareBuddyTheme.colors

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(fullPillRadius))
                .background(colors.switchTrackOff)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDecrement, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Remove,
                    contentDescription = "Decrease $label",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = String.format(Locale.getDefault(), "%02d", value),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            IconButton(onClick = onIncrement, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Increase $label",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
