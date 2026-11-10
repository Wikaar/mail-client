package com.mailtabs.app.ui.accounts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mailtabs.app.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.data.db.AccountEntity
import com.mailtabs.app.data.db.Security
import com.mailtabs.app.ui.main.Avatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    vm: AccountsViewModel = hiltViewModel(),
) {
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<AccountEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, stringResource(R.string.add_account)) }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(accounts, key = { it.id }) { a ->
                ListItem(
                    headlineContent = { Text(a.name) },
                    supportingContent = { Text("${a.email} · ${a.imapHost}") },
                    leadingContent = { Avatar(a.name, a.email) },
                    trailingContent = {
                        IconButton(onClick = { confirmDelete = a }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_account)) }
                    },
                    modifier = Modifier.clickable { onEdit(a.id) },
                )
                HorizontalDivider()
            }
        }
    }

    confirmDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = { Text(stringResource(R.string.delete_account_text, a.name)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(a.id); confirmDelete = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(onDone: () -> Unit, vm: AccountEditViewModel = hiltViewModel()) {
    LaunchedEffect(vm.done) { if (vm.done) onDone() }
    val f = vm.form
    fun upd(block: AccountForm.() -> AccountForm) { vm.form = vm.form.block() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isEdit) R.string.account_settings else R.string.new_account)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!vm.isEdit) {
                Text(stringResource(R.string.provider), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Preset.entries.forEach { p ->
                        FilterChip(
                            selected = f.preset == p,
                            onClick = { vm.applyPreset(p) },
                            label = { Text(stringResource(p.title)) },
                        )
                    }
                }
                if (f.preset.hint != 0) {
                    Text(stringResource(f.preset.hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(f.name, { upd { copy(name = it) } }, label = { Text(stringResource(R.string.tab_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.email, { upd { copy(email = it) } }, label = { Text(stringResource(R.string.email_address)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.senderName, { upd { copy(senderName = it) } }, label = { Text(stringResource(R.string.sender_name)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.username, { upd { copy(username = it) } },
                label = { Text(stringResource(R.string.login_hint)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.password, { upd { copy(password = it) } },
                label = { Text(stringResource(if (vm.isEdit) R.string.password_keep else R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.incoming_imap), style = MaterialTheme.typography.titleSmall)
            ServerFields(
                host = f.imapHost, port = f.imapPort, security = f.imapSecurity,
                onHost = { upd { copy(imapHost = it) } },
                onPort = { upd { copy(imapPort = it) } },
                onSecurity = { s -> upd { copy(imapSecurity = s, imapPort = defaultPort(imapPort, s, 993, 143)) } },
            )

            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.outgoing_smtp), style = MaterialTheme.typography.titleSmall)
            ServerFields(
                host = f.smtpHost, port = f.smtpPort, security = f.smtpSecurity,
                onHost = { upd { copy(smtpHost = it) } },
                onPort = { upd { copy(smtpPort = it) } },
                onSecurity = { s -> upd { copy(smtpSecurity = s, smtpPort = defaultPort(smtpPort, s, 465, 587)) } },
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_sent_copy), modifier = Modifier.weight(1f))
                Switch(checked = f.saveSentCopy, onCheckedChange = { v -> upd { copy(saveSentCopy = v) } })
            }

            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Button(
                onClick = vm::save,
                enabled = !vm.saving,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                if (vm.saving) {
                    CircularProgressIndicator(Modifier.height(20.dp).width(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.checking_connection))
                } else {
                    Text(stringResource(R.string.check_and_save))
                }
            }
        }
    }
}

private fun defaultPort(current: String, s: Security, ssl: Int, plain: Int): String {
    val known = setOf("", "993", "143", "465", "587", "25")
    if (current !in known) return current
    return if (s == Security.SSL) ssl.toString() else plain.toString()
}

@Composable
private fun ServerFields(
    host: String,
    port: String,
    security: Security,
    onHost: (String) -> Unit,
    onPort: (String) -> Unit,
    onSecurity: (Security) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(host, onHost, label = { Text(stringResource(R.string.server)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f))
        OutlinedTextField(port, { onPort(it.filter(Char::isDigit).take(5)) }, label = { Text(stringResource(R.string.port)) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(100.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(Security.SSL to "SSL/TLS", Security.STARTTLS to "STARTTLS", Security.NONE to stringResource(R.string.security_none)).forEach { (s, label) ->
            FilterChip(selected = security == s, onClick = { onSecurity(s) }, label = { Text(label) })
        }
    }
}
