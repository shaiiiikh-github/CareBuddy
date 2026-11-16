package com.carebuddy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebuddy.app.data.Reminder
import com.carebuddy.app.ui.components.*
import com.carebuddy.app.ui.theme.CareBuddyTheme
import com.carebuddy.app.ui.theme.pressScale
import com.carebuddy.app.viewmodel.MainViewModel

@Composable
fun RemindersScreen(
    viewModel: MainViewModel,
    onAddReminderClick: () -> Unit,
    onEditReminderClick: (Reminder) -> Unit
) {
    val reminders by viewModel.reminders.collectAsState()

    RemindersContent(
        reminders = reminders,
        onToggle = { reminder, enabled -> viewModel.toggleReminderEnabled(reminder.id, enabled) },
        onAddClick = onAddReminderClick,
        onEditClick = onEditReminderClick,
        onDeleteClick = { reminder -> viewModel.deleteReminder(reminder.id) }
    )
}

@Composable
fun RemindersContent(
    reminders: List<Reminder>,
    onToggle: (Reminder, Boolean) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Reminder) -> Unit,
    onDeleteClick: (Reminder) -> Unit
) {
    LargeTitleScaffold(
        title = "Reminders",
        subtitle = "${reminders.count { it.enabled }} active schedules",
        trailingAction = {
            CapsuleButton(
                text = "Add",
                icon = Icons.Outlined.Add,
                onClick = onAddClick,
                style = ButtonStyle.Filled,
                minHeight = 36.dp
            )
        }
    ) {
        if (reminders.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Alarm,
                title = "No reminders scheduled",
                description = "CareBuddy sounds acoustic alert chimes and flashes eye reminders at scheduled times.",
                actionText = "Add Reminder",
                onActionClick = onAddClick,
                modifier = Modifier.fillMaxSize().padding(top = 40.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SectionHeader(text = "Scheduled Times")

                InsetGroupedList {
                    reminders.forEachIndexed { index, reminder ->
                        ReminderRowItem(
                            reminder = reminder,
                            isLast = index == reminders.lastIndex,
                            onToggle = { checked -> onToggle(reminder, checked) },
                            onEdit = { onEditClick(reminder) },
                            onDelete = { onDeleteClick(reminder) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderRowItem(
    reminder: Reminder,
    isLast: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = CareBuddyTheme.colors
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val categoryIcon = when (reminder.category.lowercase()) {
        "medication" -> Icons.Outlined.Medication
        "water" -> Icons.Outlined.WaterDrop
        "activity" -> Icons.AutoMirrored.Outlined.DirectionsWalk
        else -> Icons.Outlined.Alarm
    }

    val categoryColor = when (reminder.category.lowercase()) {
        "medication" -> colors.accent
        "water" -> colors.accent
        "activity" -> colors.success
        else -> colors.warning
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (reminder.enabled) categoryColor else colors.switchTrackOff),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = if (reminder.enabled) Color.White else colors.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Time & Title
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = reminder.time,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (reminder.enabled) colors.textPrimary else colors.textTertiary
                    )

                    // Status Pill
                    if (reminder.enabled) {
                        if (reminder.isAcknowledgedToday) {
                            StatusPill(label = "Done", dotColor = colors.success)
                        }
                    } else {
                        StatusPill(label = "Off", dotColor = colors.tintGray)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${reminder.title} • ${reminder.repeat.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = colors.textSecondary
                )
            }

            // Visible Delete Button
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // iOS Switch
            IosSwitch(
                checked = reminder.enabled,
                onCheckedChange = onToggle
            )
        }

        if (!isLast) {
            HorizontalDivider(
                color = colors.separator,
                thickness = 0.5.dp,
                modifier = Modifier.padding(start = 68.dp)
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Reminder", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Are you sure you want to delete '${reminder.title}'? This will remove it from CareBuddy.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = colors.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = colors.accent)
                }
            },
            containerColor = colors.backgroundSecondary,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
            shape = RoundedCornerShape(22.dp)
        )
    }
}
