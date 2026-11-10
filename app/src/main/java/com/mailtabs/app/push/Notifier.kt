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

    fun notifyNew(account: AccountEntity, messages: List<MessageEntity>) {
        if (messages.isEmpty() || !settings.cached.notifyFor(account.id)) return
        if (!canPost()) return
        val nm = NotificationManagerCompat.from(ctx)
        val group = group(account.id)
        try {
            for (m in messages.sortedBy { it.date }.takeLast(5)) {
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
                    .setContentIntent(openIntent(account.id, null))
                    .build()
                nm.notify(TAG_MESSAGE, m.id.toInt(), n)
            }
            val summary = NotificationCompat.Builder(ctx, CH_MAIL)
                .setSmallIcon(R.drawable.ic_stat_mail)
                .setSubText(account.name)
                .setContentTitle(account.name)
                .setContentText(strings.get(R.string.notification_new_mail))
                .setGroup(group)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(openIntent(account.id, null))
                .build()
            nm.notify(TAG_SUMMARY, account.id.toInt(), summary)
        } catch (_: SecurityException) {
        }
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun group(accountId: Long) = "account_$accountId"

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
    }
}
