package com.mailtabs.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailtabs.app.data.MailRepository
import com.mailtabs.app.data.db.AccountEntity
import com.mailtabs.app.push.SyncController
import com.mailtabs.app.settings.AppSettings
import com.mailtabs.app.settings.SettingsState
import com.mailtabs.app.settings.SyncMode
import com.mailtabs.app.settings.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: AppSettings,
    private val syncController: SyncController,
    repo: MailRepository,
) : ViewModel(), SettingsActions {

    val state: StateFlow<SettingsState> =
        settings.state.stateIn(viewModelScope, SharingStarted.Eagerly, settings.cached)

    val accounts: StateFlow<List<AccountEntity>> =
        repo.accounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    override fun setTheme(theme: ThemeMode) {
        viewModelScope.launch {
            settings.setTheme(theme)
            AppSettings.applyNightMode(theme)
        }
    }

    override fun setLanguage(tag: String) {
        viewModelScope.launch {
            settings.setLanguage(tag)
            AppSettings.applyLanguage(tag)
        }
    }

    override fun setNotificationsEnabled(enabled: Boolean) = updateSync { settings.setNotificationsEnabled(enabled) }

    override fun setAccountNotifications(accountId: Long, enabled: Boolean) =
        updateSync { settings.setAccountNotifications(accountId, enabled) }

    override fun setSyncMode(mode: SyncMode) = updateSync { settings.setSyncMode(mode) }

    /** Сохраняет настройку и сразу останавливает/запускает нужный механизм получения писем. */
    private fun updateSync(block: suspend () -> Unit): Unit {
        viewModelScope.launch {
            block()
            runCatching { syncController.apply(startService = true) }
        }
    }
}

/** Действия экрана настроек (реализует [SettingsViewModel]). */
interface SettingsActions {
    fun setTheme(theme: ThemeMode)
    fun setLanguage(tag: String)
    fun setNotificationsEnabled(enabled: Boolean)
    fun setAccountNotifications(accountId: Long, enabled: Boolean)
    fun setSyncMode(mode: SyncMode)
}
