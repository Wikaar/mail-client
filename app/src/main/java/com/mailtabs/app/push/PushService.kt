package com.mailtabs.app.push

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mailtabs.app.data.MailRepository
import com.mailtabs.app.data.db.AccountEntity
import com.mailtabs.app.di.AppEntryPoint
import com.mailtabs.app.settings.AppSettings
import com.mailtabs.app.settings.SyncMode
import dagger.hilt.android.EntryPointAccessors
import com.sun.mail.imap.IMAPFolder
import dagger.hilt.android.AndroidEntryPoint
import javax.mail.Folder
import javax.mail.event.MessageCountAdapter
import javax.mail.event.MessageCountEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Foreground-сервис: на каждый ящик держит IMAP-соединение с IDLE на INBOX.
 * Тип remoteMessaging (Android 14+): не имеет 6-часового лимита dataSync из Android 15
 * и разрешён к запуску из BOOT_COMPLETED.
 */
@AndroidEntryPoint
class PushService : Service() {

    @Inject lateinit var repo: MailRepository
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var settings: AppSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loops = HashMap<Long, Pair<AccountEntity, Job>>()
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNetwork: Network? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        goForeground()
        scope.launch {
            // Держим IDLE только для ящиков с включёнными уведомлениями и только в режиме «Мгновенно».
            combine(repo.accounts, settings.state) { accounts, s ->
                if (s.syncMode != SyncMode.INSTANT) emptyList() else accounts.filter { s.notifyFor(it.id) }
            }.collect { list -> reconcile(list) }
        }
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        networkCallback?.let {
            runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun goForeground() {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        ServiceCompat.startForeground(this, Notifier.SERVICE_NOTIFICATION_ID, notifier.serviceNotification(), type)
    }

    private fun reconcile(accounts: List<AccountEntity>) {
        if (accounts.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        synchronized(loops) {
            val ids = accounts.map { it.id }.toSet()
            loops.keys.filter { it !in ids }.forEach { loops.remove(it)?.second?.cancel() }
            for (a in accounts) {
                val cur = loops[a.id]
                if (cur == null || cur.first != a.copy(position = cur.first.position)) {
                    cur?.second?.cancel()
                    loops[a.id] = a to scope.launch { idleLoop(a) }
                }
            }
        }
    }

    private fun restartAll() = synchronized(loops) {
        for ((id, pair) in loops.toMap()) {
            pair.second.cancel()
            loops[id] = pair.first to scope.launch { idleLoop(pair.first) }
        }
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java)
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val previous = lastNetwork
                lastNetwork = network
                // Сеть сменилась (Wi-Fi <-> мобильная) — старые сокеты мертвы, переподключаемся.
                if (previous != null && previous != network) restartAll()
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { networkCallback = cb }
    }

    private suspend fun idleLoop(account: AccountEntity) {
        var backoff = 15_000L
        while (coroutineContext.isActive) {
            try {
                runCatching { repo.syncFolder(account.id, INBOX, notify = true) }
                idleSession(account)
                backoff = 15_000L
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "IDLE ${account.name}: ${e.message}")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(5 * 60_000L)
            }
        }
    }

    /** Одна IDLE-сессия; возвращается при обрыве соединения. */
    private suspend fun idleSession(account: AccountEntity) {
        val store = repo.openIdleStore(account, readTimeoutMs = IDLE_READ_TIMEOUT_MS)
        try {
            val inbox = store.getFolder(INBOX) as IMAPFolder
            inbox.open(Folder.READ_ONLY)
            val newMail = Channel<Unit>(Channel.CONFLATED)
            inbox.addMessageCountListener(object : MessageCountAdapter() {
                override fun messagesAdded(e: MessageCountEvent) {
                    newMail.trySend(Unit)
                }
            })
            val supportsIdle = store.hasCapability("IDLE")
            coroutineScope {
                launch {
                    for (x in newMail) runCatching { repo.syncFolder(account.id, INBOX, notify = true) }
                }
                launch {
                    // Каждые 9 минут перезапускаем IDLE (NOOP прерывает его): иначе сервер/NAT рвут соединение.
                    while (true) {
                        delay(if (supportsIdle) KEEPALIVE_MS else POLL_MS)
                        runCatching { noop(inbox) }
                    }
                }
                launch {
                    try {
                        awaitCancellation()
                    } finally {
                        runCatching { noop(inbox) }
                    }
                }
                try {
                    while (isActive && inbox.isOpen && store.isConnected) {
                        if (supportsIdle) inbox.idle() else delay(POLL_MS)
                    }
                } finally {
                    coroutineContext.cancelChildren()
                }
            }
        } finally {
            runCatching { store.close() }
        }
    }

    private fun noop(f: IMAPFolder) {
        f.doCommand { p ->
            p.simpleCommand("NOOP", null)
            null
        }
    }

    companion object {
        private const val TAG = "PushService"
        private const val INBOX = "INBOX"
        private const val KEEPALIVE_MS = 9 * 60_000L
        private const val POLL_MS = 5 * 60_000L
        private const val IDLE_READ_TIMEOUT_MS = 20 * 60_000

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, PushService::class.java))
            } catch (e: Exception) {
                // Android 12+: запуск FGS из фона запрещён — попробуем при следующем открытии приложения.
                Log.w(TAG, "Cannot start push service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PushService::class.java))
        }
    }
}

/** После перезагрузки/обновления запускает выбранный в настройках механизм получения писем. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val ep = EntryPointAccessors.fromApplication(context.applicationContext, AppEntryPoint::class.java)
                val pending = goAsync()
                ep.repository().scope.launch {
                    try {
                        ep.syncController().apply(startService = true)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
