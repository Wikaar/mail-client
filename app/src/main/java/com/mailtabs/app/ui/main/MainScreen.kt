package com.mailtabs.app.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    vm: MainViewModel = hiltViewModel(),
) {
    val tabsOrNull by vm.tabs.collectAsStateWithLifecycle()
    val tabs = tabsOrNull
    if (tabs == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (tabs.isEmpty()) {
        NoAccounts(onAddAccount)
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
        // Жесты открытия выключены, чтобы не мешать свайпу между вкладками.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
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
                                Text(
                                    folderTitle(currentFolder, currentFolderName),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    current.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Папки")
                            }
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { topMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Меню ящика")
                                }
                                AccountMenu(
                                    expanded = topMenu,
                                    accountId = current.id,
                                    onDismiss = { topMenu = false },
                                    onDeleteAll = { deleteAllFor = current.id },
                                    onManageAccounts = onManageAccounts,
                                )
                            }
                        },
                    )
                    ScrollableTabRow(selectedTabIndex = page, edgePadding = 8.dp) {
                        tabs.forEachIndexed { i, tab ->
                            AccountTabItem(
                                tab = tab,
                                selected = i == page,
                                onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                                onLongClick = { menuForTab = tab.id },
                            ) {
                                AccountMenu(
                                    expanded = menuForTab == tab.id,
                                    accountId = tab.id,
                                    onDismiss = { menuForTab = null },
                                    onDeleteAll = { deleteAllFor = tab.id },
                                    onManageAccounts = onManageAccounts,
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { onCompose(current.id) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    text = { Text("Написать") },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            HorizontalPager(
                state = pagerState,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountTabItem(
    tab: AccountTab,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menu: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tab.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (tab.unread > 0) {
                Spacer(Modifier.width(6.dp))
                Badge { Text(if (tab.unread > 999) "999+" else tab.unread.toString()) }
            }
        }
        menu()
    }
}

/** Меню ящика и его текущей папки (долгое нажатие на вкладку или «⋮»). */
@Composable
private fun AccountMenu(
    expanded: Boolean,
    accountId: Long,
    onDismiss: () -> Unit,
    onDeleteAll: () -> Unit,
    onManageAccounts: () -> Unit,
) {
    if (!expanded) return
    val vm = accountViewModel(accountId)
    val folder by vm.currentFolder.collectAsStateWithLifecycle()
    val folderName by vm.folderName.collectAsStateWithLifecycle()
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        Text(
            "Папка: ${folderTitle(folder, folderName)}",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownMenuItem(
            text = { Text("Пометить все прочитанными") },
            leadingIcon = { Icon(Icons.Default.DoneAll, null) },
            onClick = { vm.markAllRead(); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("Удалить все письма") },
            leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
            onClick = { onDismiss(); onDeleteAll() },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = { Text("Управление ящиками") },
            onClick = { onDismiss(); onManageAccounts() },
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить все письма?") },
        text = {
            Text(
                if (n == null) "Подсчёт писем…" else buildString {
                    append("Ящик «${tab.name}», папка «${folderTitle(folder, folderName)}»: $n ")
                    append(pluralLetters(n))
                    append(if (trash) ". Они будут удалены безвозвратно." else ". Они будут перемещены в Корзину.")
                }
            )
        },
        confirmButton = {
            TextButton(
                enabled = n != null && n > 0,
                onClick = { vm.deleteAll(); onDismiss() },
            ) { Text(if (n != null) "Удалить ($n)" else "Удалить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun pluralLetters(n: Int): String {
    val m10 = n % 10
    val m100 = n % 100
    return when {
        m10 == 1 && m100 != 11 -> "письмо"
        m10 in 2..4 && m100 !in 12..14 -> "письма"
        else -> "писем"
    }
}

@Composable
private fun NoAccounts(onAddAccount: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Нет почтовых ящиков", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.padding(8.dp))
        Text(
            "Добавьте ящик IONOS, Яндекс или любой другой с IMAP/SMTP. Каждый ящик будет отдельной вкладкой.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(12.dp))
        Button(onClick = onAddAccount) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Добавить ящик")
        }
    }
}
