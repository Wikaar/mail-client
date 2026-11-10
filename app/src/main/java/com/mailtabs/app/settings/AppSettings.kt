package com.mailtabs.app.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Как получать новые письма. */
enum class SyncMode {
    /** Постоянное соединение IMAP IDLE в foreground-сервисе. */
    INSTANT,

    /** PeriodicWorkRequest раз в 15 минут, без foreground-сервиса. */
    PERIODIC,
}

data class SettingsState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** BCP-47 тег языка; пусто — как в системе. */
    val language: String = "",
    val notificationsEnabled: Boolean = true,
    /** Ящики, для которых уведомления выключены. */
    val mutedAccounts: Set<Long> = emptySet(),
    val syncMode: SyncMode = SyncMode.INSTANT,
) {
    fun notifyFor(accountId: Long) = notificationsEnabled && accountId !in mutedAccounts
}

/** Настройки приложения (DataStore). */
@Singleton
class AppSettings @Inject constructor(@ApplicationContext ctx: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { ctx.preferencesDataStoreFile("settings") },
    )

    val state: Flow<SettingsState> = store.data
        .catch { emit(emptyPreferences()) }
        .map(::toState)
        .distinctUntilChanged()

    /** Последнее известное значение — для мест, где нельзя ждать (уведомления, строки). */
    @Volatile
    var cached: SettingsState = runBlocking { state.first() }
        private set

    init {
        scope.launch { state.collect { cached = it } }
    }

    suspend fun current(): SettingsState = state.first()

    suspend fun setTheme(theme: ThemeMode) = edit { it[THEME] = theme.name }

    suspend fun setLanguage(tag: String) = edit { it[LANGUAGE] = tag }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[NOTIFICATIONS] = enabled }

    suspend fun setAccountNotifications(accountId: Long, enabled: Boolean) = edit { p ->
        val muted = p[MUTED] ?: emptySet()
        p[MUTED] = if (enabled) muted - accountId.toString() else muted + accountId.toString()
    }

    suspend fun setSyncMode(mode: SyncMode) = edit { it[SYNC_MODE] = mode.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
        cached = state.first()
    }

    private fun toState(p: Preferences) = SettingsState(
        theme = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        language = p[LANGUAGE] ?: "",
        notificationsEnabled = p[NOTIFICATIONS] ?: true,
        mutedAccounts = p[MUTED].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
        syncMode = p[SYNC_MODE]?.let { runCatching { SyncMode.valueOf(it) }.getOrNull() } ?: SyncMode.INSTANT,
    )

    companion object {
        private val THEME = stringPreferencesKey("theme")
        private val LANGUAGE = stringPreferencesKey("language")
        private val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
        private val MUTED = stringSetPreferencesKey("muted_accounts")
        private val SYNC_MODE = stringPreferencesKey("sync_mode")

        /** Языки в настройках: пусто — как в системе. */
        val LANGUAGES = listOf("", "ru", "en", "de")

        fun applyNightMode(theme: ThemeMode) {
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                }
            )
        }

        /** Per-app language API: применяется сразу (активити пересоздаётся). */
        fun applyLanguage(tag: String) {
            AppCompatDelegate.setApplicationLocales(
                if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag)
            )
        }

        /** Текущий язык приложения (из per-app language API); пусто — как в системе. */
        fun currentLanguage(): String {
            val locales = AppCompatDelegate.getApplicationLocales()
            return if (locales.isEmpty) "" else locales[0]?.language.orEmpty()
        }
    }
}
