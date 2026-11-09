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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(@ApplicationContext private val ctx: Context) {

    fun createChannels() {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MAIL, "Новые письма", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SERVICE, "Push-соединение", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                description = "Постоянное уведомление сервиса IMAP IDLE"
            }
        )
    }

    fun serviceNotification(accounts: Int): Notification =
        NotificationCompat.Builder(ctx, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_mail)
            .setContentTitle("Ожидание новых писем")
            .setContentText(if (accounts == 1) "1 ящик на связи" else "Ящиков на связи: $accounts")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openIntent(null))
            .build()

    fun notifyNew(account: AccountEntity, messages: List<MessageEntity>) {
        if (messages.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(ctx)
        val group = "account_${account.id}"
        val pi = openIntent(account.id)
        try {
            for (m in messages.sortedBy { it.date }.takeLast(5)) {
                val sender = m.fromName.ifBlank { m.fromAddress }
                val n = NotificationCompat.Builder(ctx, CH_MAIL)
                    .setSmallIcon(R.drawable.ic_stat_mail)
                    .setSubText(account.name)
                    .setContentTitle(sender)
                    .setContentText(m.subject.ifBlank { "(без темы)" })
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText("${m.subject}\n${m.preview}".trim())
                    )
                    .setWhen(m.date)
                    .setGroup(group)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_EMAIL)
                    .setContentIntent(pi)
                    .build()
                nm.notify("${account.id}:${m.uid}".hashCode(), n)
            }
            val summary = NotificationCompat.Builder(ctx, CH_MAIL)
                .setSmallIcon(R.drawable.ic_stat_mail)
                .setSubText(account.name)
                .setContentTitle(account.name)
                .setContentText("Новые письма")
                .setGroup(group)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()
            nm.notify(("summary" + account.id).hashCode(), summary)
        } catch (_: SecurityException) {
        }
    }

    private fun openIntent(accountId: Long?): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (accountId != null) putExtra(MainActivity.EXTRA_ACCOUNT_ID, accountId)
        }
        return PendingIntent.getActivity(
            ctx,
            accountId?.toInt() ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val CH_MAIL = "new_mail"
        const val CH_SERVICE = "push_service"
        const val SERVICE_NOTIFICATION_ID = 1
    }
}
