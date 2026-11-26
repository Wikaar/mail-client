package com.mailtabs.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mailtabs.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.data.db.FolderEntity
import com.mailtabs.app.data.db.FolderRole
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.mailtabs.app.ui.components.Avatar
import com.mailtabs.app.ui.components.CountPill
import com.mailtabs.app.ui.components.SectionLabel
import com.mailtabs.app.ui.theme.InputShape
import com.mailtabs.app.ui.theme.Wikar

fun folderIcon(f: FolderEntity): ImageVector = when (f.role) {
    FolderRole.INBOX -> Icons.Default.Inbox
    FolderRole.SENT -> Icons.Default.Send
    FolderRole.DRAFTS -> Icons.Default.Drafts
    FolderRole.TRASH -> Icons.Default.Delete
    FolderRole.SPAM -> Icons.Default.Report
    FolderRole.ARCHIVE -> Icons.Default.Archive
    FolderRole.OTHER -> Icons.Default.Folder
}

private sealed interface FolderDialog {
    data class Create(val parent: String?) : FolderDialog
    data class Rename(val folder: FolderEntity) : FolderDialog
    data class Delete(val folder: FolderEntity) : FolderDialog
}

/** Папки ТЕКУЩЕГО ящика, загруженные с сервера (IMAP LIST): сначала системные, затем свои. */
@Composable
fun FolderDrawer(
    accountName: String,
    vm: AccountViewModel,
    onSelect: (String) -> Unit,
    onManageAccounts: () -> Unit,
) {
    val folders by vm.folders.collectAsStateWithLifecycle()
    val current by vm.folderName.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<FolderDialog?>(null) }
    val system = folders.filter { it.role != FolderRole.OTHER }
    val custom = folders.filter { it.role == FolderRole.OTHER }

    Column(Modifier.fillMaxWidth()) {
        // Шапка: бренд и текущий ящик.
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(accountName, accountName, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    accountName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Wikar.colors.divider)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (folders.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.folders_loading),
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(system, key = { it.fullName }) { f ->
                FolderItem(
                    f = f,
                    selected = f.fullName == current,
                    onClick = { if (f.selectable) onSelect(f.fullName) },
                    onCreateSub = { dialog = FolderDialog.Create(f.fullName) },
                    onRename = { dialog = FolderDialog.Rename(f) },
                    onDelete = { dialog = FolderDialog.Delete(f) },
                )
            }
            if (custom.isNotEmpty()) {
                item(key = "custom_header") { SectionLabel(stringResource(R.string.folders_custom)) }
            }
            items(custom, key = { it.fullName }) { f ->
                FolderItem(
                    f = f,
                    selected = f.fullName == current,
                    onClick = { if (f.selectable) onSelect(f.fullName) },
                    onCreateSub = { dialog = FolderDialog.Create(f.fullName) },
                    onRename = { dialog = FolderDialog.Rename(f) },
                    onDelete = { dialog = FolderDialog.Delete(f) },
                )
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Wikar.colors.divider)
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.new_folder)) },
            icon = { Icon(Icons.Default.CreateNewFolder, null) },
            selected = false,
            onClick = { dialog = FolderDialog.Create(null) },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
        )
        NavigationDrawerItem(
            label = { Text(stringResource(R.string.accounts_title)) },
            icon = { Icon(Icons.Default.ManageAccounts, null) },
            selected = false,
            onClick = onManageAccounts,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        )
    }

    when (val d = dialog) {
        is FolderDialog.Create -> TextInputDialog(
            title = if (d.parent == null) stringResource(R.string.new_folder) else stringResource(R.string.new_folder_in, d.parent),
            initial = "",
            confirm = stringResource(R.string.create),
            onConfirm = { vm.createFolder(d.parent, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is FolderDialog.Rename -> TextInputDialog(
            title = stringResource(R.string.rename_folder),
            initial = d.folder.name,
            confirm = stringResource(R.string.rename),
            onConfirm = { vm.renameFolder(d.folder.fullName, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is FolderDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.delete_folder_title)) },
            text = { Text(stringResource(R.string.delete_folder_text, d.folder.fullName)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteFolder(d.folder.fullName); dialog = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancel)) } },
        )
        null -> Unit
    }
}

@Composable
private fun FolderItem(
    f: FolderEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onCreateSub: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val custom = f.role == FolderRole.OTHER
    NavigationDrawerItem(
        label = {
            Text(
                folderTitle(f, f.fullName),
                color = if (f.selectable) LocalContentColor.current else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected || f.unread > 0) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        icon = { Icon(folderIcon(f), null) },
        selected = selected,
        onClick = onClick,
        badge = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CountPill(f.unread, selected = selected)
                if (custom) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.folder_actions)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.create_subfolder)) }, onClick = { menu = false; onCreateSub() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onRename() })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onDelete() },
                            )
                        }
                    }
                }
            }
        },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
            unselectedContainerColor = Color.Transparent,
        ),
        modifier = Modifier.padding(start = (12 + f.depth * 16).dp, end = 12.dp),
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text(stringResource(R.string.name)) },
                shape = InputShape,
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
