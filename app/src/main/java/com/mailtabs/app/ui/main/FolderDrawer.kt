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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.data.db.FolderEntity
import com.mailtabs.app.data.db.FolderRole

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

/** Папки ТЕКУЩЕГО ящика, загруженные с сервера (IMAP LIST). */
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

    Column(Modifier.fillMaxWidth()) {
        Text(
            accountName,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
        )
        LazyColumn(Modifier.weight(1f)) {
            if (folders.isEmpty()) {
                item {
                    Text(
                        "Папки загружаются…",
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(folders, key = { it.fullName }) { f ->
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
        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text("Новая папка") },
            icon = { Icon(Icons.Default.CreateNewFolder, null) },
            selected = false,
            onClick = { dialog = FolderDialog.Create(null) },
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        NavigationDrawerItem(
            label = { Text("Почтовые ящики") },
            icon = { Icon(Icons.Default.ManageAccounts, null) },
            selected = false,
            onClick = onManageAccounts,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }

    when (val d = dialog) {
        is FolderDialog.Create -> TextInputDialog(
            title = if (d.parent == null) "Новая папка" else "Новая папка в «${d.parent}»",
            initial = "",
            confirm = "Создать",
            onConfirm = { vm.createFolder(d.parent, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is FolderDialog.Rename -> TextInputDialog(
            title = "Переименовать папку",
            initial = d.folder.name,
            confirm = "Переименовать",
            onConfirm = { vm.renameFolder(d.folder.fullName, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is FolderDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Удалить папку?") },
            text = { Text("Папка «${d.folder.fullName}» и все письма в ней будут удалены на сервере.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteFolder(d.folder.fullName); dialog = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
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
                color = if (f.selectable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        },
        icon = { Icon(folderIcon(f), null) },
        selected = selected,
        onClick = onClick,
        badge = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (f.unread > 0) Text(f.unread.toString(), style = MaterialTheme.typography.labelLarge)
                if (custom) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Действия с папкой") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Создать вложенную") }, onClick = { menu = false; onCreateSub() })
                            DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename() })
                            DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
            }
        },
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
            OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Название") })
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
