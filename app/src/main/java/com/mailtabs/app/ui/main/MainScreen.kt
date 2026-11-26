package com.mailtabs.app.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.R
import com.mailtabs.app.ui.components.Avatar
import com.mailtabs.app.ui.components.CountPill
import com.mailtabs.app.ui.components.EmptyState
import com.mailtabs.app.ui.theme.PillShape
import com.mailtabs.app.ui.theme.Wikar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    openAccountId: Long?,
    onOpenAccountHandled: () -> Unit,
    onOpenMessage: (Long) -> Unit,
    onCompose: (Long) -> Unit,
    onManageAccounts: () -> Unit,
    onAddAccount: () -> Unit,
    onSettings: () -> Unit,
    bottomBar: @Composable () -> Unit,
    vm: MainViewModel = hiltViewModel(),
) {
    val tabsOrNull by vm.tabs.collectAsStateWithLifecycle()
    val tabs = tabsOrNull
    if (tabs == null) {
        Scaffold(bottomBar = bottomBar) { p ->
            Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
        return
    }
    if (tabs.isEmpty()) {
        NoAccounts(onAddAccount, onSettings, bottomBar)
        return
    }

    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val page = pagerState.currentPage.coerceIn(0, tabs.lastIndex)
    val current = tabs[page]
    val currentVm = accountViewModel(current.id)
    val currentFolder by currentVm.currentFolder.collectAsStateWithLifecycle()
    val currentFolderName by currentVm.folderName.collectAsStateWithLifecycle()
    val currentOnlyFlagged by currentVm.onlyFlagged.collectAsStateWithLifecycle()

    LaunchedEffect(openAccountId, tabs) {
        if (openAccountId != null) {
            val idx = tabs.indexOfFirst { it.id == openAccountId }
            if (idx >= 0) pagerState.scrollToPage(idx)
            onOpenAccountHandled()
        }
    }

    var menuForTab by remember { mutableStateOf<Long?>(null) }
    var topMenu by remember { mutableStateOf(false) }
    var deleteAllFor by remember { mutableStateOf<Long?>(null) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Жест открытия выключен, чтобы не мешать свайпу по строкам писем.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                FolderDrawer(
                    accountName = current.name,
                    vm = currentVm,
                    onSelect = {
                        currentVm.selectFolder(it)
                        scope.launch { drawerState.close() }
                    },
                    onManageAccounts = {
                        scope.launch { drawerState.close() }
                        onManageAccounts()
                    },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        folderTitle(currentFolder, currentFolderName),
                                        style = MaterialTheme.typography.titleLarge,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    val unread = currentFolder?.unread ?: 0
                                    if (unread > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        CountPill(unread, selected = true)
                                    }
                                }
                                if (currentOnlyFlagged) {
                                    Text(
                                        stringResource(R.string.subtitle_only_important, current.name),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.folders))
                            }
                        },
                        actions = {
                            IconButton(onClick = currentVm::toggleOnlyFlagged) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = stringResource(R.string.filter_only_important),
                                    tint = if (currentOnlyFlagged) Wikar.colors.star else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box {
                                IconButton(onClick = { topMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.account_menu))
                                }
                                AccountMenu(
                                    expanded = topMenu,
                                    accountId = current.id,
                                    onDismiss = { topMenu = false },
                                    onDeleteAll = { deleteAllFor = current.id },
                                    onManageAccounts = onManageAccounts,
                                    onSettings = onSettings,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                    AccountSwitcher(
                        tabs = tabs,
                        selectedIndex = page,
                        onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
                        onLongClick = { menuForTab = it },
                    ) { tab ->
                        AccountMenu(
                            expanded = menuForTab == tab.id,
                            accountId = tab.id,
                            onDismiss = { menuForTab = null },
                            onDeleteAll = { deleteAllFor = tab.id },
                            onManageAccounts = onManageAccounts,
                            onSettings = onSettings,
                        )
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { onCompose(current.id) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text(stringResource(R.string.compose)) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            },
            bottomBar = bottomBar,
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            // Свайп между ящиками выключен: горизонтальный свайп отдан строкам писем.
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.padding(padding).fillMaxSize(),
                key = { tabs[it].id },
            ) { p ->
                AccountPage(
                    vm = accountViewModel(tabs[p].id),
                    snackbar = snackbar,
                    onOpenMessage = onOpenMessage,
                )
            }
        }
    }

    deleteAllFor?.let { accountId ->
        val tab = tabs.firstOrNull { it.id == accountId }
        if (tab == null) {
            deleteAllFor = null
        } else {
            DeleteAllDialog(
                tab = tab,
                onDismiss = { deleteAllFor = null },
            )
        }
    }
}

/** Переключатель ящиков: горизонтальные «пилюли» с аватаром и счётчиком непрочитанного. */
@Composable
private fun AccountSwitcher(
    tabs: List<AccountTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onLongClick: (Long) -> Unit,
    menu: @Composable (AccountTab) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) { listState.animateScrollToItem(selectedIndex) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(tabs, key = { _, t -> t.id }) { i, tab ->
            Box {
                AccountChip(
                    tab = tab,
                    selected = i == selectedIndex,
                    onClick = { onSelect(i) },
                    onLongClick = { onLongClick(tab.id) },
                )
                menu(tab)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountChip(tab: AccountTab, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = PillShape,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainer,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .semantics {
                this.selected = selected
                role = Role.Tab
            },
    ) {
        Row(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(tab.name, tab.name, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                tab.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            if (tab.unread > 0) {
                Spacer(Modifier.width(8.dp))
                CountPill(tab.unread, selected = selected)
            }
        }
    }
}

/** Меню ящика и его текущей папки (долгое нажатие на ящик или «⋮»). */
@Composable
private fun AccountMenu(
    expanded: Boolean,
    accountId: Long,
    onDismiss: () -> Unit,
    onDeleteAll: () -> Unit,
    onManageAccounts: () -> Unit,
    onSettings: () -> Unit,
) {
    if (!expanded) return
    val vm = accountViewModel(accountId)
    val folder by vm.currentFolder.collectAsStateWithLifecycle()
    val folderName by vm.folderName.collectAsStateWithLifecycle()
    val onlyFlagged by vm.onlyFlagged.collectAsStateWithLifecycle()
    DropdownMenu(
        expanded = true,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            stringResource(R.string.menu_folder, folderTitle(folder, folderName)),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.filter_only_important)) },
            leadingIcon = {
                Icon(Icons.Default.Star, null, tint = if (onlyFlagged) Wikar.colors.star else LocalContentColor.current)
            },
            trailingIcon = { Checkbox(checked = onlyFlagged, onCheckedChange = null) },
            onClick = { vm.toggleOnlyFlagged(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.mark_all_read)) },
            leadingIcon = { Icon(Icons.Default.DoneAll, null) },
            onClick = { vm.markAllRead(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.delete_all), color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); onDeleteAll() },
        )
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Wikar.colors.divider)
        DropdownMenuItem(
            text = { Text(stringResource(R.string.manage_accounts)) },
            leadingIcon = { Icon(Icons.Default.ManageAccounts, null) },
            onClick = { onDismiss(); onManageAccounts() },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.settings_title)) },
            leadingIcon = { Icon(Icons.Default.Settings, null) },
            onClick = { onDismiss(); onSettings() },
        )
    }
}

@Composable
private fun DeleteAllDialog(tab: AccountTab, onDismiss: () -> Unit) {
    val vm = accountViewModel(tab.id)
    val folder by vm.currentFolder.collectAsStateWithLifecycle()
    val folderName by vm.folderName.collectAsStateWithLifecycle()
    var count by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(tab.id, folderName) { count = vm.countForDeleteAll() }
    val n = count
    val trash = vm.isTrash()
    val title = folderTitle(folder, folderName)
    val text = if (n == null) {
        stringResource(R.string.delete_all_counting)
    } else {
        pluralStringResource(R.plurals.delete_all_text, n, tab.name, title, n) + " " +
            stringResource(if (trash) R.string.delete_all_permanent else R.string.delete_all_to_trash)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.delete_all_title)) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                enabled = n != null && n > 0,
                onClick = { vm.deleteAll(); onDismiss() },
            ) {
                Text(
                    if (n != null) stringResource(R.string.delete_count, n) else stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun NoAccounts(onAddAccount: () -> Unit, onSettings: () -> Unit, bottomBar: @Composable () -> Unit) {
    Scaffold(bottomBar = bottomBar) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            EmptyState(
                icon = Icons.Outlined.MarkEmailUnread,
                title = stringResource(R.string.no_accounts_title),
                text = stringResource(R.string.no_accounts_text),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = onAddAccount, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_account))
                    }
                    Spacer(Modifier.padding(4.dp))
                    TextButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_title))
                    }
                }
            }
        }
    }
}
