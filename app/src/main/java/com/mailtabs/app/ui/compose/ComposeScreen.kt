package com.mailtabs.app.ui.compose

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.R
import com.mailtabs.app.data.ComposeAttachment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(onBack: () -> Unit, vm: ComposeViewModel = hiltViewModel()) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Storage Access Framework: разрешения на хранилище не нужны, файл сразу копируется к себе.
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        vm.addAttachments(uris)
    }

    BackHandler(enabled = !vm.closed) { vm.close() }

    LaunchedEffect(vm.closed) {
        if (vm.closed) {
            if (vm.draftSaved) Toast.makeText(context, R.string.draft_saved, Toast.LENGTH_SHORT).show()
            onBack()
        }
    }
    LaunchedEffect(vm.error) {
        vm.error?.let { snackbar.showSnackbar(it); vm.error = null }
    }
    val discardLabel = stringResource(R.string.exit_without_saving)
    LaunchedEffect(vm.draftError) {
        vm.draftError?.let {
            val r = snackbar.showSnackbar(it, actionLabel = discardLabel, withDismissAction = true)
            vm.draftError = null
            if (r == SnackbarResult.ActionPerformed) vm.discard()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(vm.title)) },
                navigationIcon = {
                    IconButton(onClick = vm::close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (vm.sending || vm.savingDraft) {
                        CircularProgressIndicator(Modifier.padding(12.dp))
                    } else {
                        IconButton(onClick = { pickFiles.launch("*/*") }) {
                            Icon(Icons.Default.AttachFile, stringResource(R.string.attach))
                        }
                        IconButton(onClick = vm::send, enabled = !vm.preparing) {
                            Icon(Icons.Default.Send, stringResource(R.string.send))
                        }
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
            Attachments(
                list = vm.attachments,
                total = vm.totalSize,
                preparing = vm.preparing,
                onRemove = vm::removeAttachment,
            )
        }
    }

    if (vm.confirmLarge) {
        AlertDialog(
            onDismissRequest = vm::cancelSendLarge,
            icon = { Icon(Icons.Default.Warning, null) },
            title = { Text(stringResource(R.string.attachments_too_big_title)) },
            text = { Text(stringResource(R.string.attachments_too_big_text, Formatter.formatShortFileSize(context, vm.totalSize))) },
            confirmButton = { TextButton(onClick = vm::confirmSendLarge) { Text(stringResource(R.string.send_anyway)) } },
            dismissButton = { TextButton(onClick = vm::cancelSendLarge) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Attachments(
    list: List<ComposeAttachment>,
    total: Long,
    preparing: Boolean,
    onRemove: (ComposeAttachment) -> Unit,
) {
    val context = LocalContext.current
    if (list.isEmpty() && !preparing) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        list.forEach { a ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, Modifier.padding(end = 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        Formatter.formatShortFileSize(context, a.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onRemove(a) }) {
                    Icon(Icons.Default.Close, stringResource(R.string.attachment_remove))
                }
            }
        }
        if (preparing) {
            Text(stringResource(R.string.attachments_loading), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        if (list.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val over = total > ATTACHMENTS_WARN_BYTES
            Text(
                stringResource(R.string.attachments_total, list.size, Formatter.formatShortFileSize(context, total)),
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
