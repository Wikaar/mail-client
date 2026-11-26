package com.mailtabs.app.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.R
import com.mailtabs.app.data.db.AccountEntity
import com.mailtabs.app.data.db.Security
import com.mailtabs.app.ui.components.Avatar
import com.mailtabs.app.ui.components.EmptyState
import com.mailtabs.app.ui.components.SectionCard
import com.mailtabs.app.ui.components.SectionLabel
import com.mailtabs.app.ui.theme.CardShape
import com.mailtabs.app.ui.theme.InputShape
import com.mailtabs.app.ui.theme.PillShape
import com.mailtabs.app.ui.theme.Wikar

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.add_account)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Wikar.dimens.maxContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (accounts.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.MarkEmailUnread,
                            title = stringResource(R.string.no_accounts_title),
                            text = stringResource(R.string.no_accounts_text),
                        )
                    }
                }
                items(accounts, key = { it.id }) { a ->
                    Surface(
                        onClick = { onEdit(a.id) },
                        shape = CardShape,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(a.name, a.email)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    a.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    a.imapHost,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { confirmDelete = a }) {
                                Icon(Icons.Default.Delete, stringResource(R.string.delete_account), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { a ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = { Text(stringResource(R.string.delete_account_text, a.name)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(a.id); confirmDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
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
    fun upd(block: AccountForm.() -> AccountForm) {
        vm.form = vm.form.block()
        vm.resetTest()
    }
    var showPassword by remember { mutableStateOf(false) }
    val busy = vm.saving || vm.testing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (vm.isEdit) R.string.account_settings else R.string.new_account)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).imePadding().fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Wikar.dimens.maxContentWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                if (!vm.isEdit) {
                    SectionLabel(stringResource(R.string.provider))
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Preset.entries.forEach { p ->
                            FilterChip(
                                selected = f.preset == p,
                                onClick = { vm.applyPreset(p); vm.resetTest() },
                                label = { Text(stringResource(p.title)) },
                                shape = PillShape,
                            )
                        }
                    }
                    if (f.preset.hint != 0) {
                        Surface(
                            shape = CardShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                stringResource(f.preset.hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                    }
                }

                SectionLabel(stringResource(R.string.section_account))
                SectionCard {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field(f.name, { upd { copy(name = it) } }, stringResource(R.string.tab_name))
                        Field(f.email, { upd { copy(email = it) } }, stringResource(R.string.email_address), KeyboardType.Email)
                        Field(f.senderName, { upd { copy(senderName = it) } }, stringResource(R.string.sender_name))
                        Field(f.username, { upd { copy(username = it) } }, stringResource(R.string.login_hint))
                        OutlinedTextField(
                            f.password, { upd { copy(password = it) } },
                            label = { Text(stringResource(if (vm.isEdit) R.string.password_keep else R.string.password)) },
                            singleLine = true,
                            shape = InputShape,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        stringResource(if (showPassword) R.string.hide_password else R.string.show_password),
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                SectionLabel(stringResource(R.string.incoming_imap))
                SectionCard {
                    ServerFields(
                        host = f.imapHost, port = f.imapPort, security = f.imapSecurity,
                        onHost = { upd { copy(imapHost = it) } },
                        onPort = { upd { copy(imapPort = it) } },
                        onSecurity = { s -> upd { copy(imapSecurity = s, imapPort = defaultPort(imapPort, s, 993, 143)) } },
                    )
                }

                SectionLabel(stringResource(R.string.outgoing_smtp))
                SectionCard {
                    ServerFields(
                        host = f.smtpHost, port = f.smtpPort, security = f.smtpSecurity,
                        onHost = { upd { copy(smtpHost = it) } },
                        onPort = { upd { copy(smtpPort = it) } },
                        onSecurity = { s -> upd { copy(smtpSecurity = s, smtpPort = defaultPort(smtpPort, s, 465, 587)) } },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    ) {
                        Text(stringResource(R.string.save_sent_copy), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = f.saveSentCopy, onCheckedChange = { v -> upd { copy(saveSentCopy = v) } })
                    }
                }

                // Результат проверки / ошибка.
                val ok = vm.testOk
                val err = vm.error
                if (ok == true || err != null) {
                    Surface(
                        shape = CardShape,
                        color = if (ok == true) Wikar.colors.success.copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (ok == true) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                null,
                                tint = if (ok == true) Wikar.colors.success else MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (ok == true) stringResource(R.string.connection_ok) else err.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (ok == true) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = vm::test,
                        enabled = !busy,
                        shape = PillShape,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        if (vm.testing) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.NetworkCheck, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.test_connection), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Button(
                        onClick = vm::save,
                        enabled = !busy,
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    ) {
                        if (vm.saving) {
                            CircularProgressIndicator(
                                Modifier.size(18.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.checking_connection), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        } else {
                            Text(stringResource(R.string.check_and_save), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        shape = InputShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth(),
    )
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
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                host, onHost, label = { Text(stringResource(R.string.server)) }, singleLine = true,
                shape = InputShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                port, { onPort(it.filter(Char::isDigit).take(5)) }, label = { Text(stringResource(R.string.port)) }, singleLine = true,
                shape = InputShape,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(96.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Security.SSL to "SSL/TLS",
                Security.STARTTLS to "STARTTLS",
                Security.NONE to stringResource(R.string.security_none),
            ).forEach { (s, label) ->
                FilterChip(selected = security == s, onClick = { onSecurity(s) }, label = { Text(label) }, shape = PillShape)
            }
        }
    }
}
