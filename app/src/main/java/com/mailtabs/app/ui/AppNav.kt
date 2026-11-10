package com.mailtabs.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mailtabs.app.ui.accounts.AccountEditScreen
import com.mailtabs.app.ui.accounts.AccountsScreen
import com.mailtabs.app.ui.compose.ComposeScreen
import com.mailtabs.app.ui.main.MainScreen
import com.mailtabs.app.ui.message.MessageScreen
import com.mailtabs.app.ui.settings.SettingsScreen

/** Куда перейти по тапу на уведомление: вкладка ящика и (необязательно) письмо в ней. */
data class OpenTarget(val accountId: Long, val messageId: Long?)

@Composable
fun AppNav(openTarget: OpenTarget?, onOpenTargetHandled: () -> Unit) {
    val nav = rememberNavController()
    /** Вкладка, которую должен выбрать главный экран (переживает переход к письму и обратно). */
    var requestedAccount by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(openTarget) {
        val target = openTarget ?: return@LaunchedEffect
        requestedAccount = target.accountId
        nav.popBackStack("main", inclusive = false)
        target.messageId?.let { nav.navigate("message/$it") }
        onOpenTargetHandled()
    }

    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            MainScreen(
                openAccountId = requestedAccount,
                onOpenAccountHandled = { requestedAccount = null },
                onOpenMessage = { nav.navigate("message/$it") },
                onCompose = { accountId -> nav.navigate("compose?accountId=$accountId") },
                onManageAccounts = { nav.navigate("accounts") },
                onAddAccount = { nav.navigate("account_edit") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
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
