package com.mailtabs.app.ui.calendar

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.R
import com.mailtabs.app.data.notes.NoteEntity
import com.mailtabs.app.ui.components.EmptyState
import com.mailtabs.app.ui.main.currentLocale
import com.mailtabs.app.ui.theme.CardShape
import com.mailtabs.app.ui.theme.InputShape
import com.mailtabs.app.ui.theme.Wikar
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    bottomBar: @Composable () -> Unit,
    vm: CalendarViewModel = hiltViewModel(),
) {
    val grid by vm.grid.collectAsStateWithLifecycle()
    val selected by vm.selectedDay.collectAsStateWithLifecycle()
    val notesByDay by vm.notesByDay.collectAsStateWithLifecycle()
    val dayNotes by vm.dayNotes.collectAsStateWithLifecycle()
    val locale = currentLocale()
    var editing by remember { mutableStateOf<NoteEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        monthTitle(grid.month, locale),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                actions = {
                    IconButton(onClick = vm::prevMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.calendar_prev_month))
                    }
                    IconButton(onClick = vm::nextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.calendar_next_month))
                    }
                    IconButton(onClick = vm::today) {
                        Icon(Icons.Default.Today, stringResource(R.string.calendar_today))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = NoteEntity(day = selected.toEpochDay(), title = "") },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(stringResource(R.string.note_add)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Wikar.dimens.maxContentWidth).fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item(key = "month") {
                    MonthCard(
                        grid = grid,
                        selected = selected,
                        notesByDay = notesByDay,
                        firstDayOfWeek = vm.firstDayOfWeek,
                        locale = locale,
                        onSelect = vm::select,
                        onSwipeNext = vm::nextMonth,
                        onSwipePrev = vm::prevMonth,
                    )
                }
                item(key = "dayHeader") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)).capitalized(locale),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected == LocalDate.now()) {
                            Text(
                                stringResource(R.string.calendar_today),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (dayNotes.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Default.EditNote,
                            title = stringResource(R.string.notes_empty_title),
                            text = stringResource(R.string.notes_empty_text),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                items(dayNotes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        locale = locale,
                        onClick = { editing = note },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    editing?.let { note ->
        NoteEditorSheet(
            initial = note,
            onDismiss = { editing = null },
            onSave = { vm.save(it); editing = null },
            onDelete = { vm.delete(note.id); editing = null },
        )
    }
}

@Composable
private fun MonthCard(
    grid: MonthGrid,
    selected: LocalDate,
    notesByDay: Map<LocalDate, List<NoteEntity>>,
    firstDayOfWeek: DayOfWeek,
    locale: Locale,
    onSelect: (LocalDate) -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrev: () -> Unit,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            // Свайп по сетке листает месяцы.
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (abs(drag) > 120f) { if (drag < 0) onSwipeNext() else onSwipePrev() }
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                    onHorizontalDrag = { _, dx -> drag += dx },
                )
            },
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                for (i in 0 until 7) {
                    val dow = firstDayOfWeek.plus(i.toLong())
                    Text(
                        dow.getDisplayName(TextStyle.SHORT, locale).trimEnd('.').capitalized(locale),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = grid,
                transitionSpec = {
                    val forward = targetState.month > initialState.month
                    (slideInHorizontally(tween(220)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(180)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(180)))
                },
                contentKey = { it.month },
                label = "month",
            ) { g ->
                Column {
                    g.days.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                DayCell(
                                    day = day,
                                    inMonth = YearMonth.from(day) == g.month,
                                    selected = day == selected,
                                    today = day == LocalDate.now(),
                                    notes = notesByDay[day].orEmpty(),
                                    locale = locale,
                                    onClick = { onSelect(day) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    today: Boolean,
    notes: List<NoteEntity>,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tags = Wikar.colors.noteTags
    val description = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    Column(
        modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (selected) scheme.primaryContainer else Color.Transparent)
                .then(
                    if (today && !selected) Modifier.border(1.5.dp, scheme.primary, CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected || today) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    selected -> scheme.onPrimaryContainer
                    today -> scheme.primary
                    inMonth -> scheme.onSurface
                    else -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            notes.take(3).forEach { n ->
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(tags[n.color.coerceIn(0, tags.lastIndex)].copy(alpha = if (inMonth) 1f else 0.4f)),
                )
            }
        }
    }
}

@Composable
private fun NoteCard(note: NoteEntity, locale: Locale, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tags = Wikar.colors.noteTags
    val tag = tags[note.color.coerceIn(0, tags.lastIndex)]
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(tag),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                note.timeMinutes?.let { t ->
                    Text(
                        formatTime(t, locale),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    note.title.ifBlank { note.text.lineSequence().firstOrNull().orEmpty() },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (note.title.isNotBlank() && note.text.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        note.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteEditorSheet(
    initial: NoteEntity,
    onDismiss: () -> Unit,
    onSave: (NoteEntity) -> Unit,
    onDelete: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val locale = currentLocale()
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var text by remember { mutableStateOf(initial.text) }
    var time by remember { mutableStateOf(initial.timeMinutes) }
    var color by remember { mutableStateOf(initial.color) }
    var pickTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = initial.id == 0L
    val tags = Wikar.colors.noteTags

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (isNew) R.string.note_new else R.string.note_edit),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        LocalDate.ofEpochDay(initial.day)
                            .format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale)).capitalized(locale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!isNew) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.note_title)) },
                singleLine = true,
                shape = InputShape,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.note_text)) },
                minLines = 3,
                maxLines = 8,
                shape = InputShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val t = time
                if (t == null) {
                    FilterChip(
                        selected = false,
                        onClick = { pickTime = true },
                        label = { Text(stringResource(R.string.note_add_time)) },
                        leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)) },
                    )
                } else {
                    InputChip(
                        selected = true,
                        onClick = { pickTime = true },
                        label = { Text(formatTime(t, locale)) },
                        leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.note_remove_time),
                                Modifier.size(18.dp).clickable { time = null },
                            )
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                tags.forEachIndexed { i, c ->
                    val chosen = i == color
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(c)
                            .clickable(role = Role.RadioButton) { color = i }
                            .semantics { this.selected = chosen },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (chosen) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color.White)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        onSave(initial.copy(title = title.trim(), text = text.trim(), timeMinutes = time, color = color))
                    },
                    enabled = title.isNotBlank() || text.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }

    if (pickTime) {
        val now = LocalTime.now()
        val state = rememberTimePickerState(
            initialHour = time?.div(60) ?: now.hour,
            initialMinute = time?.rem(60) ?: 0,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = { time = state.hour * 60 + state.minute; pickTime = false }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = state) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.note_delete_title)) },
            text = { Text(stringResource(R.string.note_delete_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** «Ноябрь 2026»: именительный падеж (standalone), с запасным вариантом, если платформа вернёт число. */
private fun monthTitle(month: YearMonth, locale: Locale): String {
    val standalone = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    val name = if (standalone.any { it.isLetter() }) standalone else month.month.getDisplayName(TextStyle.FULL, locale)
    return "${name.capitalized(locale)} ${month.year}"
}

private fun formatTime(minutes: Int, locale: Locale): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

private fun String.capitalized(locale: Locale): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
