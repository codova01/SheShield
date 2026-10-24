package com.example.sheshield.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sheshield.data.ContactsViewModel
import com.example.sheshield.data.TrustedContact

@Composable
fun ContactsScreen(vm: ContactsViewModel = viewModel()) {
    val contacts by vm.contacts.collectAsState()
    var editing by remember { mutableStateOf<TrustedContact?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Trusted contacts", style = MaterialTheme.typography.headlineMedium)
        Text(
            "These people get your live location by SMS when you trigger SOS. Up to ${ContactsViewModel.MAX}.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        LazyColumn(Modifier.weight(1f)) {
            items(contacts, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Text(c.phone, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { editing = c; showDialog = true }) { Text("Edit") }
                        TextButton(onClick = { vm.delete(c.id) }) { Text("Delete") }
                    }
                }
            }
        }

        Button(
            onClick = { editing = null; showDialog = true },
            enabled = vm.canAdd(),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(if (vm.canAdd()) "Add contact" else "Limit reached (5)") }
    }

    if (showDialog) {
        var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
        var phone by remember(editing) { mutableStateOf(editing?.phone ?: "") }
        val valid = name.isNotBlank() && phone.filter { it.isDigit() }.length >= 10

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editing == null) "Add contact" else "Edit contact") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        phone, { phone = it }, label = { Text("Phone number") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    vm.upsert(
                        (editing ?: TrustedContact(name = "", phone = ""))
                            .copy(name = name.trim(), phone = phone.trim())
                    )
                    showDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

