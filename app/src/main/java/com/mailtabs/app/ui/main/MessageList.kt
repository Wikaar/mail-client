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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.R
import com.mailtabs.app.data.db.FolderEntity
import com.mailtabs.app.data.db.MessageListItem
import com.mailtabs.app.ui.components.Avatar
import com.mailtabs.app.ui.components.EmptyState
import com.mailtabs.app.ui.components.StarToggle
import com.mailtabs.app.ui.theme.CardShape
import com.mailtabs.app.ui.theme.Wikar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

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
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            Modifier.widthIn(max = Wikar.dimens.maxContentWidth).fillMaxSize(),
            // Снизу — место под FAB, чтобы он не закрывал последнее письмо.
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
        ) {
            if (messages.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = if (onlyFlagged) Icons.Outlined.StarOutline else Icons.Outlined.Inbox,
                        title = stringResource(
                            when {
                                refreshing -> R.string.loading
                                onlyFlagged -> R.string.no_important_messages
                                else -> R.string.no_messages
                            }
                        ),
                        text = if (refreshing) null else stringResource(R.string.no_messages_hint),
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            items(messages, key = { it.id }) { m ->
                // Удалённая строка плавно исчезает, соседние плавно съезжают; «Отменить» возвращает её с fade-in.
                Box(
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
    // TalkBack: действия свайпов доступны как пользовательские действия.
    val readLabel = stringResource(if (m.seen) R.string.mark_unread else R.string.mark_read)
    val deleteLabel = stringResource(R.string.delete)
    val ext = Wikar.colors
    val scheme = MaterialTheme.colorScheme
    SwipeToDismissBox(
        state = state,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(CardShape)
            .onSizeChanged { width = it.width }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(readLabel) { toggle(); true },
                    CustomAccessibilityAction(deleteLabel) { delete(); true },
                )
            },
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
                offset < 0f -> Triple(ext.swipeDelete, Icons.Default.Delete, Alignment.CenterEnd)
                offset > 0f -> Triple(
                    scheme.primaryContainer,
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
                        tint = if (offset < 0f) ext.onSwipeDelete else scheme.onPrimaryContainer,
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

/** Строка письма, плотность Comfortable: аватар, отправитель + время, тема, превью в 2 строки. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(m: MessageListItem, onClick: () -> Unit, onLongClick: () -> Unit, onToggleFlag: () -> Unit) {
    val unread = !m.seen
    val name = m.fromName.ifBlank { m.fromAddress }.ifBlank { stringResource(R.string.no_sender) }
    val locale = currentLocale()
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            // Непрочитанные — чуть более светлая карточка; цвет не единственный признак (ещё точка и жирный шрифт).
            .background(if (unread) scheme.surfaceContainerHigh else scheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 14.dp, end = 4.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(name, m.fromAddress)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (unread) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(scheme.primary))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    color = if (unread) scheme.onSurface else scheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (m.hasAttachments) {
                    Icon(
                        Icons.Default.Attachment, contentDescription = stringResource(R.string.attachments),
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    formatTime(m.date, locale),
                    modifier = Modifier.padding(end = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (unread) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(2.dp))
            Text(
                m.subject.ifBlank { stringResource(R.string.no_subject) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                color = if (unread) scheme.onSurface else scheme.onSurface.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 10.dp),
            )
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    m.preview,
                    modifier = Modifier.weight(1f).padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                StarToggle(flagged = m.flagged, onToggle = onToggleFlag, modifier = Modifier.padding(top = 0.dp))
            }
        }
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
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(folders, key = { it.fullName }) { f ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(CardShape)
                            .clickable { onPick(f.fullName) }
                            .heightIn(min = 48.dp)
                            .padding(start = (12 + f.depth * 16).dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(folderIcon(f), null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text(folderTitle(f, f.fullName), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
