package com.carebuddy.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carebuddy.app.data.EmergencyContact
import com.carebuddy.app.ui.components.*
import com.carebuddy.app.ui.theme.CareBuddyTheme
import com.carebuddy.app.ui.theme.pressScale
import com.carebuddy.app.viewmodel.MainViewModel
import java.util.UUID

fun isValidPhoneNumber(phone: String): Boolean {
    val digits = phone.filter { it.isDigit() }
    val validChars = phone.all { it.isDigit() || it in "+- ()" }
    return validChars && digits.length in 7..15
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactScreen(viewModel: MainViewModel) {
    val contacts by viewModel.emergencyContacts.collectAsState()

    var showSheet by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var inputName by remember { mutableStateOf("") }
    var inputPhone by remember { mutableStateOf("") }
    var inputIsPrimary by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val colors = CareBuddyTheme.colors

    LargeTitleScaffold(
        title = "Emergency",
        subtitle = "Recipients of automated SOS alerts and phone calls",
        trailingAction = {
            CapsuleButton(
                text = "Add",
                icon = Icons.Outlined.Add,
                onClick = {
                    editingContact = null
                    inputName = ""
                    inputPhone = ""
                    inputIsPrimary = contacts.isEmpty()
                    validationError = null
                    showSheet = true
                },
                style = ButtonStyle.Filled,
                minHeight = 36.dp
            )
        }
    ) {
        if (contacts.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.Shield,
                title = "No emergency contacts",
                description = "Add trusted family members or caregivers to receive SMS alerts and phone calls when SOS is triggered.",
                actionText = "Add Contact",
                onActionClick = {
                    editingContact = null
                    inputName = ""
                    inputPhone = ""
                    inputIsPrimary = true
                    validationError = null
                    showSheet = true
                },
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
                SectionHeader(text = "Emergency Receivers (${contacts.size})")

                InsetGroupedList {
                    contacts.forEachIndexed { index, contact ->
                        ContactRowItem(
                            contact = contact,
                            isLast = index == contacts.lastIndex,
                            onToggleEnabled = {
                                val updated = contacts.map {
                                    if (it.id == contact.id) it.copy(enabled = !it.enabled) else it
                                }
                                viewModel.updateEmergencyContacts(updated)
                            },
                            onSetPrimary = {
                                val updated = contacts.map {
                                    it.copy(isPrimary = it.id == contact.id)
                                }
                                viewModel.updateEmergencyContacts(updated)
                            },
                            onEdit = {
                                editingContact = contact
                                inputName = contact.name
                                inputPhone = contact.phoneNumber
                                inputIsPrimary = contact.isPrimary
                                validationError = null
                                showSheet = true
                            },
                            onDelete = {
                                val updated = contacts.filter { it.id != contact.id }
                                viewModel.updateEmergencyContacts(updated)
                            }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Contact Modal Sheet
    if (showSheet) {
        GlassBottomSheet(
            onDismissRequest = { showSheet = false }
        ) {
            Text(
                text = if (editingContact == null) "New Emergency Contact" else "Edit Contact",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SectionHeader(text = "Contact Name")
            IosTextField(
                value = inputName,
                onValueChange = { inputName = it; validationError = null },
                placeholder = "Full Name (e.g. Sarah Miller)",
                leadingIcon = Icons.Outlined.Person
            )

            Spacer(modifier = Modifier.height(12.dp))

            SectionHeader(text = "Phone Number")
            IosTextField(
                value = inputPhone,
                onValueChange = { inputPhone = it; validationError = null },
                placeholder = "+1 555 123 4567",
                leadingIcon = Icons.Outlined.Phone,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                errorMessage = validationError
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Contact Switch
            InsetGroupedList {
                ListRow(
                    title = "Primary emergency contact",
                    subtitle = "First recipient called and texted during an emergency",
                    icon = Icons.Outlined.Star,
                    iconBackgroundColor = colors.warning,
                    showDivider = false,
                    trailing = {
                        IosSwitch(
                            checked = inputIsPrimary,
                            onCheckedChange = { inputIsPrimary = it }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            CapsuleButton(
                text = if (editingContact == null) "Save Contact" else "Update Contact",
                onClick = {
                    if (inputName.isBlank()) {
                        validationError = "Please enter contact name"
                        return@CapsuleButton
                    }
                    if (!isValidPhoneNumber(inputPhone)) {
                        validationError = "Enter a valid phone number (7–15 digits)"
                        return@CapsuleButton
                    }

                    val newContact = EmergencyContact(
                        id = editingContact?.id ?: UUID.randomUUID().toString(),
                        name = inputName.trim(),
                        phoneNumber = inputPhone.trim(),
                        isPrimary = inputIsPrimary,
                        enabled = editingContact?.enabled ?: true
                    )

                    val updatedList = if (editingContact != null) {
                        contacts.map { if (it.id == newContact.id) newContact else it }
                    } else {
                        contacts + newContact
                    }

                    val finalList = if (inputIsPrimary) {
                        updatedList.map { it.copy(isPrimary = it.id == newContact.id) }
                    } else {
                        updatedList
                    }

                    viewModel.updateEmergencyContacts(finalList)
                    showSheet = false
                },
                style = ButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ContactRowItem(
    contact: EmergencyContact,
    isLast: Boolean,
    onToggleEnabled: () -> Unit,
    onSetPrimary: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = CareBuddyTheme.colors
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val initials = contact.name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .uppercase()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar (Tap to set as primary)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (contact.isPrimary) colors.accent else colors.switchTrackOff)
                    .pressScale(onClick = { if (!contact.isPrimary) onSetPrimary() }),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (initials.isNotEmpty()) initials else "C",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (contact.isPrimary) Color.White else colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (contact.enabled) colors.textPrimary else colors.textTertiary
                    )

                    if (contact.isPrimary) {
                        StatusPill(label = "Primary", dotColor = colors.accent)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = contact.phoneNumber,
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

            Spacer(modifier = Modifier.width(6.dp))

            // Enabled Toggle
            IosSwitch(
                checked = contact.enabled,
                onCheckedChange = { onToggleEnabled() }
            )
        }

        if (!isLast) {
            HorizontalDivider(
                color = colors.separator,
                thickness = 0.5.dp,
                modifier = Modifier.padding(start = 70.dp)
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Contact", style = MaterialTheme.typography.titleMedium) },
            text = { Text("Remove '${contact.name}' from your emergency notification list?") },
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
