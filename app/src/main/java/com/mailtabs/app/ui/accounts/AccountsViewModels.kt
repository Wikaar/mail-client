package com.mailtabs.app.ui.accounts

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailtabs.app.data.MailRepository
import com.mailtabs.app.data.db.AccountEntity
import com.mailtabs.app.data.db.Security
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(private val repo: MailRepository) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>> =
        repo.accounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteAccount(id) }
    }
}

enum class Preset(
    val title: String,
    val imapHost: String, val imapPort: Int,
    val smtpHost: String, val smtpPort: Int,
    val saveSentCopy: Boolean,
    val hint: String,
) {
    IONOS("IONOS", "imap.ionos.de", 993, "smtp.ionos.de", 465, true, ""),
    YANDEX(
        "Яндекс", "imap.yandex.ru", 993, "smtp.yandex.ru", 465, false,
        "Для Яндекса нужен пароль приложения (id.yandex.ru → Безопасность → Пароли приложений) " +
            "и включённый доступ по IMAP в настройках почты.",
    ),
    CUSTOM("Другой", "", 993, "", 465, true, ""),
}

data class AccountForm(
    val name: String = "",
    val email: String = "",
    val senderName: String = "",
    val username: String = "",
    val password: String = "",
    val imapHost: String = "",
    val imapPort: String = "993",
    val imapSecurity: Security = Security.SSL,
    val smtpHost: String = "",
    val smtpPort: String = "465",
    val smtpSecurity: Security = Security.SSL,
    val saveSentCopy: Boolean = true,
    val preset: Preset = Preset.CUSTOM,
)

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repo: MailRepository,
) : ViewModel() {

    val editId: Long = saved.get<Long>("accountId") ?: -1L
    val isEdit get() = editId > 0

    var form by mutableStateOf(AccountForm())
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)
        private set

    init {
        if (isEdit) viewModelScope.launch {
            repo.getAccount(editId)?.let { a ->
                form = AccountForm(
                    name = a.name, email = a.email, senderName = a.senderName, username = a.username,
                    password = "", imapHost = a.imapHost, imapPort = a.imapPort.toString(),
                    imapSecurity = a.imapSecurity, smtpHost = a.smtpHost, smtpPort = a.smtpPort.toString(),
                    smtpSecurity = a.smtpSecurity, saveSentCopy = a.saveSentCopy,
                )
            }
        }
    }

    fun applyPreset(p: Preset) {
        form = if (p == Preset.CUSTOM) form.copy(preset = p) else form.copy(
            preset = p,
            imapHost = p.imapHost, imapPort = p.imapPort.toString(), imapSecurity = Security.SSL,
            smtpHost = p.smtpHost, smtpPort = p.smtpPort.toString(), smtpSecurity = Security.SSL,
            saveSentCopy = p.saveSentCopy,
            name = form.name.ifBlank { p.title },
        )
    }

    fun save() {
        val f = form
        val imapPort = f.imapPort.toIntOrNull()
        val smtpPort = f.smtpPort.toIntOrNull()
        error = when {
            f.email.isBlank() || !f.email.contains('@') -> "Укажите адрес почты"
            f.imapHost.isBlank() || imapPort == null -> "Укажите IMAP-сервер и порт"
            f.smtpHost.isBlank() || smtpPort == null -> "Укажите SMTP-сервер и порт"
            !isEdit && f.password.isEmpty() -> "Введите пароль"
            else -> null
        }
        if (error != null || imapPort == null || smtpPort == null) return
        val draft = AccountEntity(
            id = if (isEdit) editId else 0,
            name = f.name.ifBlank { f.email },
            email = f.email.trim(),
            senderName = f.senderName.trim(),
            imapHost = f.imapHost.trim(), imapPort = imapPort, imapSecurity = f.imapSecurity,
            smtpHost = f.smtpHost.trim(), smtpPort = smtpPort, smtpSecurity = f.smtpSecurity,
            username = f.username.ifBlank { f.email }.trim(),
            encryptedPassword = "",
            saveSentCopy = f.saveSentCopy,
        )
        saving = true
        viewModelScope.launch {
            try {
                repo.saveAccount(draft, f.password)
                done = true
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                saving = false
            }
        }
    }
}
