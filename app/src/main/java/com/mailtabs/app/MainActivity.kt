package com.mailtabs.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mailtabs.app.push.Notifier
import com.mailtabs.app.push.SyncController
import com.mailtabs.app.settings.AppSettings
import com.mailtabs.app.ui.AppNav
import com.mailtabs.app.ui.OpenTarget
import com.mailtabs.app.ui.theme.MailTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AppCompatActivity — ради per-app language API (AppCompatDelegate.setApplicationLocales)
 * и тёмной темы приложения (setDefaultNightMode) на всех версиях Android.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var notifier: Notifier
    @Inject lateinit var settings: AppSettings
    @Inject lateinit var syncController: SyncController

    /** Что открыть по тапу на уведомление: вкладку ящика и, возможно, письмо. */
    private val openTarget = mutableStateOf<OpenTarget?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Названия каналов — на текущем языке приложения.
        notifier.createChannels()
        if (savedInstanceState == null) handleIntent(intent)
        if (savedInstanceState == null && settings.cached.notificationsEnabled && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MailTheme {
                AppNav(
                    openTarget = openTarget.value,
                    onOpenTargetHandled = { openTarget.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { runCatching { syncController.apply(startService = true) } }
    }

    private fun handleIntent(intent: Intent?) {
        val accountId = intent?.getLongExtra(EXTRA_ACCOUNT_ID, -1L) ?: -1L
        val messageId = intent?.getLongExtra(EXTRA_MESSAGE_ID, -1L) ?: -1L
        if (accountId > 0) openTarget.value = OpenTarget(accountId, messageId.takeIf { it > 0 })
    }

    companion object {
        const val EXTRA_ACCOUNT_ID = "accountId"
        const val EXTRA_MESSAGE_ID = "messageId"
    }
}
