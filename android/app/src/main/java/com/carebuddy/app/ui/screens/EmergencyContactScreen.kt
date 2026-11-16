package com.carebuddy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carebuddy.app.data.EmergencyContact
import com.carebuddy.app.ui.theme.*
import com.carebuddy.app.viewmodel.MainViewModel

fun isValidPhoneNumber(phone: String): Boolean {
    val digits = phone.filter { it.isDigit() }
    val validChars = phone.all { it.isDigit() || it in "+- ()" }
    return validChars && digits.length in 7..15
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactScreen(viewModel: MainViewModel) {
    val contacts by viewModel.emergencyContacts.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var editingContact by remember { mutableStateOf<EmergencyContact?>(null) }
    var inputName by remember { mutableStateOf("") }
    var inputPhone by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emergency Contacts", color = TextPrimary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDark)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingContact = null
                    inputName = ""
                    inputPhone = ""
                    showDialog = true
                },
                containerColor = RedEmergency,
                contentColor = TextPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Contact")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Contact", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = SlateDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Emergency Notification Receivers",
                style = MaterialTheme.typography.titleLarge,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (contacts.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SlateCard),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AmberWarning)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = "No Contacts Configured",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CareBuddy needs at least one emergency contact to dispatch SMS and phone calls when SOS is activated.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(contacts) { contact ->
                        ContactCardItem(
                            contact = contact,
                            onSetPrimary = {
                                val updated = contacts.map { it.copy(isPrimary = (it.id == contact.id)) }
                                viewModel.updateEmergencyContacts(updated)
                            },
                            onEdit = {
                                editingContact = contact
                                inputName = contact.name
                                inputPhone = contact.phoneNumber
                                showDialog = true
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

        if (showDialog) {
            val isPhoneValid = inputPhone.isBlank() || isValidPhoneNumber(inputPhone)
            val isFormValid = inputName.isNotBlank() && inputPhone.isNotBlank() && isValidPhoneNumber(inputPhone)

            AlertDialog(
                onDismissRequest = { showDialog = false },
                containerColor = SlateCard,
                title = {
                    Text(
                        if (editingContact == null) "Add Emergency Contact" else "Edit Contact",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = inputName,
                            onValueChange = { inputName = it },
                            label = { Text("Contact Name", color = TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealLight,
                                unfocusedBorderColor = SlateCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        OutlinedTextField(
                            value = inputPhone,
                            onValueChange = { inputPhone = it },
                            label = { Text("Phone Number", color = TextSecondary) },
                            singleLine = true,
                            isError = !isPhoneValid,
                            supportingText = if (!isPhoneValid) {
                                { Text("Enter a valid phone number (7-15 digits)", color = RedEmergency) }
                            } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealLight,
                                unfocusedBorderColor = SlateCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                errorBorderColor = RedEmergency
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (isFormValid) {
                                val newContact = EmergencyContact(
                                    id = editingContact?.id ?: System.currentTimeMillis().toString(),
                                    name = inputName.trim(),
                                    phoneNumber = inputPhone.trim(),
                                    isPrimary = editingContact?.isPrimary ?: (contacts.isEmpty() || !contacts.any { it.isPrimary })
                                )
                                val updated = if (editingContact == null) {
                                    contacts + newContact
                                } else {
                                    contacts.map { if (it.id == editingContact!!.id) newContact else it }
                                }
                                viewModel.updateEmergencyContacts(updated)
                                showDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealLight),
                        enabled = isFormValid
                    ) {
                        Text("Save", color = if (isFormValid) SlateDark else TextMuted, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
fun ContactCardItem(
    contact: EmergencyContact,
    onSetPrimary: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (contact.isPrimary) TealLight else SlateCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (contact.isPrimary) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = TealAccent.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "PRIMARY",
                                color = TealLight,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = contact.phoneNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSetPrimary) {
                    Icon(
                        imageVector = if (contact.isPrimary) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Set Primary",
                        tint = if (contact.isPrimary) AmberWarning else TextSecondary
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Contact",
                        tint = TealLight
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Contact",
                        tint = RedEmergency
                    )
                }
            }
        }
    }
}
