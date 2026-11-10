package com.mailtabs.app.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.R
import com.mailtabs.app.data.db.FolderEntity
import com.mailtabs.app.data.db.MessageListItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs
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
    val onlyFlagged by vm.onlyFlagged.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var moveFor by remember { mutableStateOf<Long?>(null) }
    val deletedText = stringResource(R.string.message_deleted)
    val undoText = stringResource(R.string.undo)

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
                item(key = "empty") {
                    Box(
                        Modifier.fillMaxWidth().padding(48.dp).animateItem(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(
                                when {
                                    refreshing -> R.string.loading
                                    onlyFlagged -> R.string.no_important_messages
                                    else -> R.string.no_messages
                                }
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(messages, key = { it.id }) { m ->
                // Удалённая строка плавно исчезает, соседние плавно съезжают; «Отменить» возвращает её с fade-in.
                Column(
                    Modifier.animateItem(
                        fadeInSpec = tween(durationMillis = 280),
                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = tween(durationMillis = 280),
                    )
                ) {
                    SwipeableMessageRow(
                        m = m,
                        onClick = { onOpenMessage(m.id) },
                        onLongClick = { moveFor = m.id },
                        onToggleRead = { vm.toggleSeen(m.id) },
                        onToggleFlag = { vm.toggleFlagged(m.id) },
                        onDelete = {
                            vm.hide(m.id)
                            scope.launch {
                                // Предыдущий Snackbar закрываем — его удаление применится сразу.
                                snackbar.currentSnackbarData?.dismiss()
                                var undone = false
                                try {
                                    undone = snackbar.showSnackbar(
                                        message = deletedText,
                                        actionLabel = undoText,
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
    }

    moveFor?.let { id ->
        MoveToFolderDialog(
            folders = folders.filter { it.selectable && it.fullName != folderName },
            onPick = { target -> vm.move(id, target); moveFor = null },
            onDismiss = { moveFor = null },
        )
    }
}

/** Порог срабатывания свайпа — доля ширины строки. */
private const val SWIPE_THRESHOLD = 0.38f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableMessageRow(
    m: MessageListItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleFlag: () -> Unit,
    onDelete: () -> Unit,
) {
    val toggle by rememberUpdatedState(onToggleRead)
    val delete by rememberUpdatedState(onDelete)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var width by remember { mutableIntStateOf(0) }
    /** Небольшой «отскок» строки после возврата на место (прочитано/непрочитано). */
    val bounce = remember { Animatable(0f) }
    lateinit var state: SwipeToDismissBoxState
    state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                // Справа налево — в Корзину: строка уезжает и плавно исчезает из списка.
                SwipeToDismissBoxValue.EndToStart -> { delete(); true }
                // Слева направо — прочитано/непрочитано: строка пружинисто возвращается.
                SwipeToDismissBoxValue.StartToEnd -> {
                    toggle()
                    scope.launch {
                        snapshotFlow { state.offsetOrZero() }.first { abs(it) < 2f }
                        bounce.animateTo(
                            targetValue = 0f,
                            animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
                            initialVelocity = -1800f,
                        )
                    }
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { it * SWIPE_THRESHOLD },
    )
    SwipeToDismissBox(
        state = state,
        modifier = Modifier.onSizeChanged { width = it.width },
        backgroundContent = {
            val offset = state.offsetOrZero()
            val fraction = if (width > 0) (abs(offset) / width).coerceIn(0f, 1f) else 0f
            val reached = fraction >= SWIPE_THRESHOLD
            // Вибрация в момент достижения порога срабатывания.
            LaunchedEffect(reached) {
                if (reached) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            val iconScale by animateFloatAsState(
                targetValue = if (reached) 1.35f else 0.85f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "swipeIconScale",
            )
            // Фон проявляется по мере свайпа и становится полностью непрозрачным у порога.
            val reveal = (fraction / SWIPE_THRESHOLD).coerceIn(0f, 1f)
            val (color, icon, align) = when {
                offset < 0f -> Triple(Color(0xFFD32F2F), Icons.Default.Delete, Alignment.CenterEnd)
                offset > 0f -> Triple(
                    MaterialTheme.colorScheme.primary,
                    if (m.seen) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead,
                    Alignment.CenterStart,
                )
                else -> Triple(Color.Transparent, null, Alignment.Center)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(color.copy(alpha = color.alpha * reveal))
                    .padding(horizontal = 24.dp),
                contentAlignment = align,
            ) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (offset < 0f) Color.White else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                            alpha = reveal
                        },
                    )
                }
            }
        },
    ) {
        Box(Modifier.graphicsLayer { translationX = bounce.value }) {
            MessageRow(m, onClick, onLongClick, onToggleFlag)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun SwipeToDismissBoxState.offsetOrZero(): Float =
    runCatching { requireOffset() }.getOrDefault(0f).let { if (it.isNaN()) 0f else it }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(m: MessageListItem, onClick: () -> Unit, onLongClick: () -> Unit, onToggleFlag: () -> Unit) {
    val unread = !m.seen
    val name = m.fromName.ifBlank { m.fromAddress }.ifBlank { stringResource(R.string.no_sender) }
    val locale = currentLocale()
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
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
                        Icons.Default.Attachment, contentDescription = stringResource(R.string.attachments),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    formatTime(m.date, locale),
                    modifier = Modifier.padding(end = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                m.subject.ifBlank { stringResource(R.string.no_subject) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.preview,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StarToggle(flagged = m.flagged, onToggle = onToggleFlag)
            }
        }
    }
}

val StarColor = Color(0xFFF9A825)

/** Звёздочка «Важное»: тап переключает флаг \Flagged. */
@Composable
fun StarToggle(flagged: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(
        targetValue = if (flagged) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "starScale",
    )
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (flagged) Icons.Default.Star else Icons.Default.StarBorder,
            contentDescription = stringResource(if (flagged) R.string.unmark_important else R.string.mark_important),
            tint = if (flagged) StarColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        )
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

/** Локаль, выбранная в приложении (per-app language). */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

/** Время письма в списке: сегодня — время, в этом году — день и месяц, иначе — короткая дата. */
fun formatTime(epochMs: Long, locale: Locale): String {
    if (epochMs <= 0) return ""
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = LocalDate.now(zone)
    return when {
        dt.toLocalDate() == today -> dt.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
        dt.year == today.year -> dt.format(DateTimeFormatter.ofPattern("d MMM", locale))
        else -> dt.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
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
        title = { Text(stringResource(R.string.move_to_folder)) },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
