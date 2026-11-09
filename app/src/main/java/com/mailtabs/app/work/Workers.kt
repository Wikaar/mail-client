package com.mailtabs.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mailtabs.app.data.MailRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkScheduler @Inject constructor(@ApplicationContext private val ctx: Context) {

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Отправить офлайн-очередь действий ящика на сервер, как только будет сеть. */
    fun flushActions(accountId: Long) {
        val req = OneTimeWorkRequestBuilder<ActionWorker>()
            .setInputData(workDataOf(ActionWorker.KEY_ACCOUNT to accountId))
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniqueWork("actions_$accountId", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    /** Подстраховка к IMAP IDLE: периодическая синхронизация входящих. */
    fun schedulePeriodic() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(ctx)
            .enqueueUniquePeriodicWork("periodic_sync", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

@HiltWorker
class ActionWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: MailRepository,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ACCOUNT, -1)
        if (id < 0) return Result.success()
        return try {
            if (repo.flushQueue(id)) Result.success() else Result.retry()
        } catch (e: Exception) {
            if (runAttemptCount < 20) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_ACCOUNT = "accountId"
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repo: MailRepository,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        for (account in repo.allAccounts()) {
            runCatching { repo.syncFolder(account.id, "INBOX", notify = true) }
        }
        return Result.success()
    }
}
