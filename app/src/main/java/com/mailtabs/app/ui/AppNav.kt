package com.mailtabs.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.ui.calendar.CalendarScreen
import com.mailtabs.app.ui.main.MainScreen
import com.mailtabs.app.ui.main.MainViewModel
import com.mailtabs.app.ui.settings.SettingsScreen
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mailtabs.app.ui.accounts.AccountEditScreen
import com.mailtabs.app.ui.accounts.AccountsScreen
import com.mailtabs.app.ui.compose.ComposeScreen
import com.mailtabs.app.ui.message.MessageScreen

/** Куда перейти по тапу на уведомление: вкладка ящика и (необязательно) письмо в ней. */
data class OpenTarget(val accountId: Long, val messageId: Long?)

@Composable
fun AppNav(openTarget: OpenTarget?, onOpenTargetHandled: () -> Unit) {
    val nav = rememberNavController()
    /** Вкладка, которую должен выбрать главный экран (переживает переход к письму и обратно). */
    var requestedAccount by rememberSaveable { mutableStateOf<Long?>(null) }

    /** Раздел нижней навигации (переживает переход к письму и пересоздание). */
    var homeTab by rememberSaveable { mutableStateOf(HomeTab.MAIL) }

    LaunchedEffect(openTarget) {
        val target = openTarget ?: return@LaunchedEffect
        homeTab = HomeTab.MAIL
        requestedAccount = target.accountId
        nav.popBackStack("main", inclusive = false)
        target.messageId?.let { nav.navigate("message/$it") }
        onOpenTargetHandled()
    }

    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            HomeScreen(
                tab = homeTab,
                onTab = { homeTab = it },
                openAccountId = requestedAccount,
                onOpenAccountHandled = { requestedAccount = null },
                onOpenMessage = { nav.navigate("message/$it") },
                onCompose = { accountId -> nav.navigate("compose?accountId=$accountId") },
                onManageAccounts = { nav.navigate("accounts") },
                onAddAccount = { nav.navigate("account_edit") },
            )
        }
        composable("accounts") {
            AccountsScreen(
                onBack = { nav.popBackStack() },
                onAdd = { nav.navigate("account_edit") },
                onEdit = { nav.navigate("account_edit?accountId=$it") },
            )
        }
        composable(
            "account_edit?accountId={accountId}",
            arguments = listOf(navArgument("accountId") { type = NavType.LongType; defaultValue = -1L }),
        ) {
            AccountEditScreen(onDone = { nav.popBackStack() })
        }
        composable(
            "message/{messageId}",
            arguments = listOf(navArgument("messageId") { type = NavType.LongType }),
        ) {
            MessageScreen(
                onBack = { nav.popBackStack() },
                onReply = { accountId, id, all ->
                    nav.navigate("compose?accountId=$accountId&mode=${if (all) "replyAll" else "reply"}&refId=$id")
                },
                onForward = { accountId, id -> nav.navigate("compose?accountId=$accountId&mode=forward&refId=$id") },
                onEditDraft = { accountId, id ->
                    // Старая версия черновика будет удалена — экран просмотра закрываем.
                    nav.navigate("compose?accountId=$accountId&mode=draft&refId=$id") {
                        popUpTo("message/{messageId}") { inclusive = true }
                    }
                },
            )
        }
        composable(
            "compose?accountId={accountId}&mode={mode}&refId={refId}",
            arguments = listOf(
                navArgument("accountId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("mode") { type = NavType.StringType; defaultValue = "new" },
                navArgument("refId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            ComposeScreen(onBack = { nav.popBackStack() })
        }
    }
}

/**
 * Корневой экран с нижней навигацией: Почта / Календарь / Настройки.
 * Состояние каждого раздела (выбранный ящик, прокрутка) сохраняется при переключении.
 */
@Composable
private fun HomeScreen(
    tab: HomeTab,
    onTab: (HomeTab) -> Unit,
    openAccountId: Long?,
    onOpenAccountHandled: () -> Unit,
    onOpenMessage: (Long) -> Unit,
    onCompose: (Long) -> Unit,
    onManageAccounts: () -> Unit,
    onAddAccount: () -> Unit,
    mainVm: MainViewModel = hiltViewModel(),
) {
    val tabs by mainVm.tabs.collectAsStateWithLifecycle()
    val unread = tabs.orEmpty().sumOf { it.unread }
    val bar: @Composable () -> Unit = { HomeNavigationBar(current = tab, onSelect = onTab, unreadMail = unread) }
    val holder = rememberSaveableStateHolder()
    holder.SaveableStateProvider(tab.name) {
        when (tab) {
            HomeTab.MAIL -> MainScreen(
                openAccountId = openAccountId,
                onOpenAccountHandled = onOpenAccountHandled,
                onOpenMessage = onOpenMessage,
                onCompose = onCompose,
                onManageAccounts = onManageAccounts,
                onAddAccount = onAddAccount,
                onSettings = { onTab(HomeTab.SETTINGS) },
                bottomBar = bar,
                vm = mainVm,
            )
            HomeTab.CALENDAR -> CalendarScreen(bottomBar = bar)
            HomeTab.SETTINGS -> SettingsScreen(onBack = null, onManageAccounts = onManageAccounts, bottomBar = bar)
        }
    }
}
