package com.mailtabs.app.ui.settings

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mailtabs.app.BuildConfig
import com.mailtabs.app.R
import com.mailtabs.app.push.Notifier
import com.mailtabs.app.settings.AppSettings
import com.mailtabs.app.settings.SyncMode
import com.mailtabs.app.settings.ThemeMode

private const val REPO_URL = "https://github.com/Wikaar/mail-client"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(hasNotificationPermission(context)) }
    // Разрешение могли выдать/отозвать в системных настройках — перепроверяем при возврате на экран.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasPermission = hasNotificationPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    fun requestPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
        ) {
            // ---------- Тема ----------
            SectionTitle(stringResource(R.string.settings_theme))
            Column(Modifier.selectableGroup()) {
                listOf(
                    ThemeMode.LIGHT to R.string.theme_light,
                    ThemeMode.DARK to R.string.theme_dark,
                    ThemeMode.SYSTEM to R.string.theme_system,
                ).forEach { (mode, label) ->
                    RadioRow(stringResource(label), selected = state.theme == mode) { vm.setTheme(mode) }
                }
            }

            SectionDivider()

            // ---------- Язык ----------
            SectionTitle(stringResource(R.string.settings_language))
            val currentLanguage = remember(state.language) { AppSettings.currentLanguage() }
            Column(Modifier.selectableGroup()) {
                AppSettings.LANGUAGES.forEach { tag ->
                    RadioRow(languageName(tag), selected = currentLanguage == tag) { vm.setLanguage(tag) }
                }
            }

            SectionDivider()

            // ---------- Уведомления ----------
            SectionTitle(stringResource(R.string.settings_notifications))
            SwitchRow(
                title = stringResource(R.string.notifications_enabled),
                checked = state.notificationsEnabled,
                onChange = { on ->
                    if (on) requestPermissionIfNeeded()
                    vm.setNotificationsEnabled(on)
                },
            )
            if (state.notificationsEnabled && !hasPermission) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.NotificationsOff, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.notifications_permission_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openAppNotificationSettings(context)
                    }) { Text(stringResource(R.string.notifications_allow)) }
                }
            }
            if (accounts.isNotEmpty()) {
                Text(
                    stringResource(R.string.notifications_per_account),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                accounts.forEach { a ->
                    SwitchRow(
                        title = a.name,
                        subtitle = a.email,
                        checked = a.id !in state.mutedAccounts,
                        enabled = state.notificationsEnabled,
                        onChange = { on ->
                            if (on) requestPermissionIfNeeded()
                            vm.setAccountNotifications(a.id, on)
                        },
                    )
                }
            }

            Text(
                stringResource(R.string.sync_mode),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            Column(Modifier.selectableGroup()) {
                RadioRow(
                    title = stringResource(R.string.sync_mode_instant),
                    subtitle = stringResource(R.string.sync_mode_instant_desc),
                    selected = state.syncMode == SyncMode.INSTANT,
                    enabled = state.notificationsEnabled,
                ) { vm.setSyncMode(SyncMode.INSTANT) }
                RadioRow(
                    title = stringResource(R.string.sync_mode_periodic),
                    subtitle = stringResource(R.string.sync_mode_periodic_desc),
                    selected = state.syncMode == SyncMode.PERIODIC,
                    enabled = state.notificationsEnabled,
                ) { vm.setSyncMode(SyncMode.PERIODIC) }
            }
            if (state.syncMode == SyncMode.INSTANT) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    OutlinedButton(onClick = { openSyncChannelSettings(context) }) {
                        Text(stringResource(R.string.hide_service_notification))
                    }
                    Text(
                        stringResource(R.string.hide_service_notification_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            SectionDivider()

            // ---------- О приложении ----------
            SectionTitle(stringResource(R.string.settings_about))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(stringResource(R.string.about_author, "Wikar"), style = MaterialTheme.typography.bodyMedium)
                Text(
                    REPO_URL.removePrefix("https://"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { openUrl(context, REPO_URL) }
                        .padding(vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun languageName(tag: String): String = when (tag) {
    "" -> stringResource(R.string.language_system)
    "ru" -> stringResource(R.string.language_ru)
    "en" -> stringResource(R.string.language_en)
    "de" -> stringResource(R.string.language_de)
    else -> tag
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(Modifier.padding(top = 12.dp))
}

@Composable
private fun RadioRow(
    title: String,
    selected: Boolean,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

private fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Системные настройки канала «Фоновая синхронизация»: его можно выключить, сервис продолжит работать. */
private fun openSyncChannelSettings(context: Context) {
    val intent = Intent(SystemSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(SystemSettings.EXTRA_CHANNEL_ID, Notifier.CH_SYNC)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        openAppNotificationSettings(context)
    }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(SystemSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(SystemSettings.EXTRA_APP_PACKAGE, context.packageName)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
    }
}
