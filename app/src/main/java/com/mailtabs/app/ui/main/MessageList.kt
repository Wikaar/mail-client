package com.mailtabs.app.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.data.db.FolderEntity
import com.mailtabs.app.data.db.MessageListItem
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPage(
    vm: AccountViewModel,
    snackbar: SnackbarHostState,
    onOpenMessage: (Long) -> Unit,
) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val folderName by vm.folderName.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var moveFor by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(error) {
        error?.let {
            vm.clearError()
            snackbar.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long)
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = vm::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (refreshing) "Загрузка…" else "Писем нет",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(messages, key = { it.id }) { m ->
                SwipeableMessageRow(
                    m = m,
                    onClick = { onOpenMessage(m.id) },
                    onLongClick = { moveFor = m.id },
                    onToggleRead = { vm.toggleSeen(m.id) },
                    onDelete = {
                        vm.hide(m.id)
                        scope.launch {
                            var undone = false
                            try {
                                undone = snackbar.showSnackbar(
                                    message = "Письмо удалено",
                                    actionLabel = "Отменить",
                                    duration = SnackbarDuration.Short,
                                ) == SnackbarResult.ActionPerformed
                            } finally {
                                if (undone) vm.undoHide(m.id) else vm.commitDelete(m.id)
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    moveFor?.let { id ->
        MoveToFolderDialog(
            folders = folders.filter { it.selectable && it.fullName != folderName },
            onPick = { target -> vm.move(id, target); moveFor = null },
            onDismiss = { moveFor = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableMessageRow(
    m: MessageListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val toggle by rememberUpdatedState(onToggleRead)
    val delete by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { delete(); true }
                SwipeToDismissBoxValue.StartToEnd -> { toggle(); false }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val dir = state.dismissDirection
            val (color, icon, align) = when (dir) {
                SwipeToDismissBoxValue.EndToStart -> Triple(Color(0xFFD32F2F), Icons.Default.Delete, Alignment.CenterEnd)
                SwipeToDismissBoxValue.StartToEnd -> Triple(
                    MaterialTheme.colorScheme.primary,
                    if (m.seen) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead,
                    Alignment.CenterStart,
                )
                SwipeToDismissBoxValue.Settled -> Triple(Color.Transparent, null, Alignment.Center)
            }
            Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
                contentAlignment = align,
            ) {
                if (icon != null) Icon(icon, contentDescription = null, tint = Color.White)
            }
        },
    ) {
        MessageRow(m, onClick, onLongClick)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(m: MessageListItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val unread = !m.seen
    val name = m.fromName.ifBlank { m.fromAddress }.ifBlank { "(без отправителя)" }
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(name, m.fromAddress)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (m.hasAttachments) {
                    Icon(
                        Icons.Default.Attachment, contentDescription = "Вложения",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    formatTime(m.date),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                m.subject.ifBlank { "(без темы)" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                m.preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val avatarColors = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFE53935), Color(0xFF8E24AA),
    Color(0xFFF4511E), Color(0xFF00897B), Color(0xFF3949AB), Color(0xFF6D4C41),
)

@Composable
fun Avatar(name: String, address: String, size: Int = 40) {
    val letter = name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
    val color = avatarColors[(address.ifBlank { name }.lowercase().hashCode().absoluteValue) % avatarColors.size]
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter.toString(), color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
private val dayFmt = DateTimeFormatter.ofPattern("d MMM")
private val yearFmt = DateTimeFormatter.ofPattern("dd.MM.yy")

fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        dt.toLocalDate() == today -> dt.format(timeFmt)
        dt.year == today.year -> dt.format(dayFmt)
        else -> dt.format(yearFmt)
    }
}

@Composable
fun MoveToFolderDialog(
    folders: List<FolderEntity>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переместить в папку") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(folders, key = { it.fullName }) { f ->
                    ListItem(
                        headlineContent = { Text(folderTitle(f, f.fullName)) },
                        leadingContent = { Icon(folderIcon(f), null) },
                        modifier = Modifier
                            .clickable { onPick(f.fullName) }
                            .padding(start = (f.depth * 12).dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Suppress("unused")
private val defaultFolderIcon = Icons.Default.Folder
