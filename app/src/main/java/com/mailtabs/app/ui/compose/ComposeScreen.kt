package com.mailtabs.app.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mailtabs.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(onBack: () -> Unit, vm: ComposeViewModel = hiltViewModel()) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.sent) { if (vm.sent) onBack() }
    LaunchedEffect(vm.error) {
        vm.error?.let { snackbar.showSnackbar(it); vm.error = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(vm.title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (vm.sending) {
                        CircularProgressIndicator(Modifier.padding(12.dp))
                    } else {
                        IconButton(onClick = vm::send) { Icon(Icons.Default.Send, stringResource(R.string.send)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Ящик-отправитель: по умолчанию — ящик текущей вкладки.
            var expanded by remember { mutableStateOf(false) }
            val from = accounts.firstOrNull { it.id == vm.fromId }
            Box {
                OutlinedTextField(
                    value = from?.let { "${it.name} <${it.email}>" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text(stringResource(R.string.field_from)) },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    accounts.forEach { a ->
                        DropdownMenuItem(
                            text = { Text("${a.name} <${a.email}>") },
                            onClick = { vm.fromId = a.id; expanded = false },
                        )
                    }
                }
            }
            OutlinedTextField(
                value = vm.to, onValueChange = { vm.to = it },
                label = { Text(stringResource(R.string.field_to)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.cc, onValueChange = { vm.cc = it },
                label = { Text(stringResource(R.string.field_cc)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.subject, onValueChange = { vm.subject = it },
                label = { Text(stringResource(R.string.field_subject)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.body, onValueChange = { vm.body = it },
                label = { Text(stringResource(R.string.field_body)) },
                minLines = 10,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            if (vm.forwardNote) {
                Text(stringResource(R.string.forward_attachments_note), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
