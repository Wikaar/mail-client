package com.mailtabs.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun AppNav(openAccountId: Long?, onOpenAccountHandled: () -> Unit) {
    val nav = rememberNavController()

    LaunchedEffect(openAccountId) {
        if (openAccountId != null) nav.popBackStack("main", inclusive = false)
    }

    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            MainScreen(
                openAccountId = openAccountId,
                onOpenAccountHandled = onOpenAccountHandled,
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
