package com.carebuddy.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carebuddy.app.data.EmergencyContact
import com.carebuddy.app.ui.theme.*
import com.carebuddy.app.viewmodel.MainViewModel

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

        if (showDialog) {
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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealLight,
                                unfocusedBorderColor = SlateCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (inputName.isNotBlank() && inputPhone.isNotBlank()) {
                                val newContact = EmergencyContact(
                                    id = editingContact?.id ?: System.currentTimeMillis().toString(),
                                    name = inputName.trim(),
                                    phoneNumber = inputPhone.trim(),
                                    isPrimary = editingContact?.isPrimary ?: (contacts.isEmpty())
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
                        colors = ButtonDefaults.buttonColors(containerColor = TealLight)
                    ) {
                        Text("Save", color = SlateDark, fontWeight = FontWeight.Bold)
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
        border = if (contact.isPrimary) BorderStroke(1.dp, AmberWarning) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSetPrimary) {
                    Icon(
                        imageVector = if (contact.isPrimary) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Primary",
                        tint = if (contact.isPrimary) AmberWarning else TextMuted
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
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
                                color = AmberWarning.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "PRIMARY",
                                    color = AmberWarning,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = contact.phoneNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }

            Row {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedEmergency)
                }
            }
        }
    }
}
