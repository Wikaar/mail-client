package com.mailtabs.app.push

import android.content.Context
import com.mailtabs.app.data.db.AppDatabase
import com.mailtabs.app.settings.AppSettings
import com.mailtabs.app.settings.SyncMode
import com.mailtabs.app.work.WorkScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Включает нужный механизм получения писем по настройкам:
 * «Мгновенно» — foreground-сервис с IMAP IDLE, «Каждые 15 минут» — PeriodicWorkRequest.
 * Если уведомления выключены для всех ящиков — не держит ни сервис, ни периодическую задачу.
 */
@Singleton
class SyncController @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: AppSettings,
    private val db: AppDatabase,
    private val scheduler: WorkScheduler,
) {
    /** @param startService false — когда приложение не на экране и запуск FGS запрещён. */
    suspend fun apply(startService: Boolean = true) {
        val s = settings.current()
        val active = db.accountDao().getAll().any { s.notifyFor(it.id) }
        when {
            !active -> {
                scheduler.cancelPeriodic()
                PushService.stop(ctx)
            }
            s.syncMode == SyncMode.PERIODIC -> {
                PushService.stop(ctx)
                scheduler.schedulePeriodic()
            }
            else -> {
                scheduler.cancelPeriodic()
                if (startService) PushService.start(ctx)
            }
        }
    }
}
