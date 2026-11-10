package com.mailtabs.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mailtabs.app.push.Notifier
import com.mailtabs.app.push.SyncController
import com.mailtabs.app.settings.AppSettings
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MailApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var settings: AppSettings
    @Inject lateinit var syncController: SyncController

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        AppSettings.applyNightMode(settings.cached.theme)
        notifier.createChannels()
        // Периодическую задачу включаем/выключаем сразу; foreground-сервис — при открытии приложения или загрузке.
        appScope.launch { runCatching { syncController.apply(startService = false) } }
    }
}
