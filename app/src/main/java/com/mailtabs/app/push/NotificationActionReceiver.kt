package com.mailtabs.app.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mailtabs.app.di.AppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * Кнопки уведомлений «Прочитано», «Удалить», «Прочитать все» — без открытия приложения.
 * Room меняется сразу, операция на сервере (\Seen или перемещение в Корзину) уходит
 * в офлайн-очередь и выполняется через WorkManager.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)
        val repo = ep.repository()
        val notifier = ep.notifier()
        val accountId = intent.getLongExtra(Notifier.EXTRA_ACCOUNT_ID, -1L)
        val messageId = intent.getLongExtra(Notifier.EXTRA_MESSAGE_ID, -1L)
        val pending = goAsync()
        repo.scope.launch {
            try {
                when (intent.action) {
                    Notifier.ACTION_MARK_READ -> if (messageId > 0) {
                        notifier.cancelMessage(accountId, messageId)
                        repo.markRead(messageId)
                    }
                    Notifier.ACTION_DELETE -> if (messageId > 0) {
                        notifier.cancelMessage(accountId, messageId)
                        repo.deleteMessageNow(messageId)
                    }
                    Notifier.ACTION_MARK_ALL_READ -> if (accountId > 0) {
                        val ids = notifier.activeMessageIds(accountId)
                        notifier.cancelAccount(accountId)
                        for (id in ids) repo.markRead(id)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
