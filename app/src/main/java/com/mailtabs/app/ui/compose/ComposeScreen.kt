package com.mailtabs.app.ui.compose

import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.R
import com.mailtabs.app.data.ComposeAttachment
import com.mailtabs.app.ui.components.Avatar
import com.mailtabs.app.ui.message.mimeIcon
import com.mailtabs.app.ui.theme.CardShape
import com.mailtabs.app.ui.theme.Wikar

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
                    // «Закрыть»: изменения сохраняются в черновик, как и раньше по «Назад».
                    IconButton(onClick = vm::close) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                },
                actions = {
                    if (vm.sending || vm.savingDraft) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.5.dp)
                    } else {
                        IconButton(onClick = { pickFiles.launch("*/*") }) {
                            Icon(Icons.Default.AttachFile, stringResource(R.string.attach))
                        }
                        FilledIconButton(
                            onClick = vm::send,
                            enabled = !vm.preparing,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).imePadding().fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Wikar.dimens.maxContentWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Шапка письма — одна карточка с полями-строками.
                Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column {
                        // Ящик-отправитель: по умолчанию — ящик текущей вкладки.
                        var expanded by remember { mutableStateOf(false) }
                        val from = accounts.firstOrNull { it.id == vm.fromId }
                        Box {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .clickable { expanded = true }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FieldLabel(stringResource(R.string.field_from))
                                if (from != null) {
                                    Avatar(from.name, from.email, size = 28.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(from.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            from.email,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                                Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                accounts.forEach { a ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(a.name)
                                                Text(
                                                    a.email,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        leadingIcon = { Avatar(a.name, a.email, size = 28.dp) },
                                        onClick = { vm.fromId = a.id; expanded = false },
                                    )
                                }
                            }
                        }
                        FieldDivider()
                        FieldRow(stringResource(R.string.field_to), vm.to, { vm.to = it }, KeyboardType.Email)
                        FieldDivider()
                        FieldRow(stringResource(R.string.field_cc), vm.cc, { vm.cc = it }, KeyboardType.Email)
                        FieldDivider()
                        FieldRow(stringResource(R.string.field_subject), vm.subject, { vm.subject = it }, KeyboardType.Text, singleLine = true)
                    }
                }

                Attachments(
                    list = vm.attachments,
                    total = vm.totalSize,
                    preparing = vm.preparing,
                    onRemove = vm::removeAttachment,
                )

                Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                    TextField(
                        value = vm.body, onValueChange = { vm.body = it },
                        placeholder = { Text(stringResource(R.string.field_body)) },
                        minLines = 12,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = transparentFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }

    if (vm.confirmLarge) {
        AlertDialog(
            onDismissRequest = vm::cancelSendLarge,
            icon = { Icon(Icons.Default.Warning, null, tint = Wikar.colors.warning) },
            title = { Text(stringResource(R.string.attachments_too_big_title)) },
            text = { Text(stringResource(R.string.attachments_too_big_text, Formatter.formatShortFileSize(context, vm.totalSize))) },
            confirmButton = { TextButton(onClick = vm::confirmSendLarge) { Text(stringResource(R.string.send_anyway)) } },
            dismissButton = { TextButton(onClick = vm::cancelSendLarge) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(72.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FieldDivider() {
    HorizontalDivider(Modifier.padding(start = 16.dp), color = Wikar.colors.divider)
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType,
    singleLine: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FieldLabel(label)
        TextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 3,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = transparentFieldColors(),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun Attachments(
    list: List<ComposeAttachment>,
    total: Long,
    preparing: Boolean,
    onRemove: (ComposeAttachment) -> Unit,
) {
    val context = LocalContext.current
    if (list.isEmpty() && !preparing) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { a ->
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(shape = CardShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(40.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(mimeIcon(a.mimeType, a.name), null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Formatter.formatShortFileSize(context, a.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onRemove(a) }) {
                        Icon(Icons.Default.Close, stringResource(R.string.attachment_remove))
                    }
                }
            }
        }
        if (preparing) {
            Text(stringResource(R.string.attachments_loading), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (list.isNotEmpty()) {
            val over = total > ATTACHMENTS_WARN_BYTES
            Text(
                stringResource(R.string.attachments_total, list.size, Formatter.formatShortFileSize(context, total)),
                style = MaterialTheme.typography.labelMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
