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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.mailtabs.app.ui.components.SectionCard
import com.mailtabs.app.ui.components.SectionLabel
import com.mailtabs.app.ui.theme.CardShape
import com.mailtabs.app.ui.theme.PillShape
import com.mailtabs.app.ui.theme.Wikar

private const val REPO_URL = "https://github.com/Wikaar/mail-client"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    onManageAccounts: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
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
                title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Wikar.dimens.maxContentWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                // ---------- Оформление ----------
                SectionLabel(stringResource(R.string.settings_theme))
                SectionCard {
                    val options = listOf(
                        ThemeMode.SYSTEM to R.string.theme_system,
                        ThemeMode.LIGHT to R.string.theme_light,
                        ThemeMode.DARK to R.string.theme_dark,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                        options.forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = state.theme == mode,
                                onClick = { vm.setTheme(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                                icon = {},
                                label = { Text(stringResource(label), maxLines = 1) },
                            )
                        }
                    }
                }

                // ---------- Язык ----------
                SectionLabel(stringResource(R.string.settings_language))
                SectionCard {
                    val currentLanguage = remember(state.language) { AppSettings.currentLanguage() }
                    Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
                        AppSettings.LANGUAGES.forEach { tag ->
                            RadioRow(languageName(tag), selected = currentLanguage == tag) { vm.setLanguage(tag) }
                        }
                    }
                }

                // ---------- Ящики ----------
                SectionLabel(stringResource(R.string.accounts_title))
                SectionCard {
                    NavRow(
                        icon = Icons.Default.ManageAccounts,
                        title = stringResource(R.string.manage_accounts),
                        subtitle = if (accounts.isEmpty()) null else accounts.joinToString(", ") { it.name },
                        onClick = onManageAccounts,
                    )
                }

                // ---------- Уведомления ----------
                SectionLabel(stringResource(R.string.settings_notifications))
                SectionCard {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        SwitchRow(
                            title = stringResource(R.string.notifications_enabled),
                            checked = state.notificationsEnabled,
                            onChange = { on ->
                                if (on) requestPermissionIfNeeded()
                                vm.setNotificationsEnabled(on)
                            },
                        )
                        if (state.notificationsEnabled && !hasPermission) {
                            Surface(
                                shape = CardShape,
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.NotificationsOff, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        stringResource(R.string.notifications_permission_missing),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = {
                                        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        else openAppNotificationSettings(context)
                                    }) { Text(stringResource(R.string.notifications_allow)) }
                                }
                            }
                        }
                        if (accounts.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = Wikar.colors.divider)
                            Text(
                                stringResource(R.string.notifications_per_account),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
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
                    }
                }

                // ---------- Синхронизация ----------
                SectionLabel(stringResource(R.string.sync_mode))
                SectionCard {
                    Column(Modifier.selectableGroup().padding(vertical = 4.dp)) {
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
                        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                            OutlinedButton(onClick = { openSyncChannelSettings(context) }, shape = PillShape) {
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
                }

                // ---------- О приложении ----------
                SectionLabel(stringResource(R.string.settings_about))
                SectionCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CardShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Mail, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.about_author, "Wikar"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Wikar.colors.divider)
                    NavRow(
                        icon = Icons.Default.Code,
                        title = REPO_URL.removePrefix("https://"),
                        subtitle = null,
                        onClick = { openUrl(context, REPO_URL) },
                    )
                }
            }
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
private fun NavRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
            .heightIn(min = 48.dp)
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
            .heightIn(min = 48.dp)
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
