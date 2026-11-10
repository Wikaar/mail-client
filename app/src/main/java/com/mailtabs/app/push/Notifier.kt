package com.mailtabs.app.push

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mailtabs.app.MainActivity
import com.mailtabs.app.R
import com.mailtabs.app.data.db.AccountEntity
import com.mailtabs.app.data.db.MessageEntity
import com.mailtabs.app.settings.AppSettings
import com.mailtabs.app.settings.Strings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val strings: Strings,
    private val settings: AppSettings,
) {

    /** Создаёт/обновляет каналы. Повторный вызов обновляет названия под текущий язык. */
    fun createChannels() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        // Каналы версии 0.1 (важность канала после создания поменять нельзя).
        for (old in listOf("new_mail", "push_service")) runCatching { nm.deleteNotificationChannel(old) }
        nm.createNotificationChannel(
            NotificationChannel(CH_MAIL, strings.get(R.string.channel_mail), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = strings.get(R.string.channel_mail_desc)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SYNC, strings.get(R.string.channel_sync), NotificationManager.IMPORTANCE_MIN).apply {
                description = strings.get(R.string.channel_sync_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
    }

    /** Служебное уведомление foreground-сервиса: тихое, без времени и счётчиков. */
    fun serviceNotification(): Notification =
        NotificationCompat.Builder(ctx, CH_SYNC)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setContentTitle(strings.get(R.string.sync_notification_title))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .setContentIntent(openIntent(null, null))
            .build()

    /**
     * Уведомление на каждое новое письмо (кнопки «Прочитано» и «Удалить», тап открывает письмо)
     * плюс summary группы ящика с кнопкой «Прочитать все».
     */
    fun notifyNew(account: AccountEntity, messages: List<MessageEntity>) {
        if (messages.isEmpty() || !settings.cached.notifyFor(account.id)) return
        if (!canPost()) return
        val nm = NotificationManagerCompat.from(ctx)
        val group = group(account.id)
        try {
            for (m in messages.filter { it.id > 0 }.sortedBy { it.date }.takeLast(MAX_PER_SYNC)) {
                val sender = m.fromName.ifBlank { m.fromAddress }
                val subject = m.subject.ifBlank { strings.get(R.string.no_subject) }
                val n = NotificationCompat.Builder(ctx, CH_MAIL)
                    .setSmallIcon(R.drawable.ic_stat_mail)
                    .setSubText(account.name)
                    .setContentTitle(sender)
                    .setContentText(subject)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$subject\n${m.preview}".trim()))
                    .setWhen(m.date)
                    .setShowWhen(true)
                    .setGroup(group)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_EMAIL)
                    .setContentIntent(openIntent(account.id, m.id))
                    .addAction(0, strings.get(R.string.action_mark_read), actionIntent(ACTION_MARK_READ, account.id, m.id))
                    .addAction(0, strings.get(R.string.action_delete), actionIntent(ACTION_DELETE, account.id, m.id))
                    .addExtras(Bundle().apply {
                        putLong(EXTRA_MESSAGE_ID, m.id)
                        putLong(EXTRA_ACCOUNT_ID, account.id)
                    })
                    .build()
                nm.notify(TAG_MESSAGE, m.id.toInt(), n)
            }
            postSummary(account.id, account.name)
        } catch (_: SecurityException) {
        }
    }

    private fun postSummary(accountId: Long, accountName: String) {
        val count = activeMessageIds(accountId).size
        if (count == 0) {
            cancelSummary(accountId)
            return
        }
        val summary = NotificationCompat.Builder(ctx, CH_MAIL)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setSubText(accountName)
            .setContentTitle(accountName)
            .setContentText(strings.plural(R.plurals.new_messages, count, count))
            .setGroup(group(accountId))
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EMAIL)
            .setContentIntent(openIntent(accountId, null))
            .addAction(0, strings.get(R.string.action_read_all), actionIntent(ACTION_MARK_ALL_READ, accountId, null))
            .addExtras(Bundle().apply { putString(EXTRA_ACCOUNT_NAME, accountName) })
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(TAG_SUMMARY, accountId.toInt(), summary)
        } catch (_: SecurityException) {
        }
    }

    /** id писем, уведомления о которых сейчас показаны для ящика. */
    fun activeMessageIds(accountId: Long): List<Long> {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return nm.activeNotifications
            .filter { it.tag == TAG_MESSAGE && it.notification.group == group(accountId) }
            .map { it.notification.extras.getLong(EXTRA_MESSAGE_ID, it.id.toLong()) }
    }

    /** Убирает уведомление письма; summary — если в группе больше ничего нет, иначе обновляет счётчик. */
    fun cancelMessage(accountId: Long, messageId: Long) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.cancel(TAG_MESSAGE, messageId.toInt())
        val summary = nm.activeNotifications.firstOrNull { it.tag == TAG_SUMMARY && it.id == accountId.toInt() }
        if (activeMessageIds(accountId).isEmpty()) {
            cancelSummary(accountId)
        } else if (summary != null) {
            summary.notification.extras.getString(EXTRA_ACCOUNT_NAME)?.let { postSummary(accountId, it) }
        }
    }

    fun cancelAccount(accountId: Long) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        for (id in activeMessageIds(accountId)) nm.cancel(TAG_MESSAGE, id.toInt())
        cancelSummary(accountId)
    }

    private fun cancelSummary(accountId: Long) {
        ctx.getSystemService(NotificationManager::class.java).cancel(TAG_SUMMARY, accountId.toInt())
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun group(accountId: Long) = "account_$accountId"

    /** Кнопка уведомления → BroadcastReceiver (без открытия приложения). Уникальный requestCode на письмо. */
    private fun actionIntent(action: String, accountId: Long, messageId: Long?): PendingIntent {
        val intent = Intent(ctx, NotificationActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ACCOUNT_ID, accountId)
            if (messageId != null) putExtra(EXTRA_MESSAGE_ID, messageId)
        }
        val requestCode = when (action) {
            ACTION_MARK_READ -> (messageId!! * 4 + 1).toInt()
            ACTION_DELETE -> (messageId!! * 4 + 2).toInt()
            else -> -(accountId * 4 + 3).toInt()
        }
        return PendingIntent.getBroadcast(
            ctx, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun openIntent(accountId: Long?, messageId: Long?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (accountId != null) putExtra(MainActivity.EXTRA_ACCOUNT_ID, accountId)
            if (messageId != null) putExtra(MainActivity.EXTRA_MESSAGE_ID, messageId)
        }
        val requestCode = when {
            messageId != null -> (messageId * 4).toInt()
            accountId != null -> -(accountId * 4 + 1).toInt()
            else -> 0
        }
        return PendingIntent.getActivity(
            ctx, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CH_MAIL = "mail"
        const val CH_SYNC = "background_sync"
        const val SERVICE_NOTIFICATION_ID = 1
        private const val TAG_MESSAGE = "msg"
        private const val TAG_SUMMARY = "summary"
        private const val MAX_PER_SYNC = 10

        const val ACTION_MARK_READ = "com.mailtabs.app.action.MARK_READ"
        const val ACTION_DELETE = "com.mailtabs.app.action.DELETE"
        const val ACTION_MARK_ALL_READ = "com.mailtabs.app.action.MARK_ALL_READ"
        const val EXTRA_ACCOUNT_ID = "accountId"
        const val EXTRA_MESSAGE_ID = "messageId"
        private const val EXTRA_ACCOUNT_NAME = "accountName"
    }
}
