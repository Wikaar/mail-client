package com.mailtabs.app.ui.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailtabs.app.data.MailRepository
import com.mailtabs.app.data.OutgoingMail
import com.mailtabs.app.data.db.AccountEntity
import com.mailtabs.app.data.mail.MailParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class ComposeViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repo: MailRepository,
) : ViewModel() {

    private val mode: String = saved.get<String>("mode") ?: "new"
    private val refId: Long = saved.get<Long>("refId") ?: -1L

    val accounts: StateFlow<List<AccountEntity>> =
        repo.accounts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var fromId by mutableLongStateOf(saved.get<Long>("accountId") ?: -1L)
    var to by mutableStateOf("")
    var cc by mutableStateOf("")
    var subject by mutableStateOf("")
    var body by mutableStateOf("")
    var sending by mutableStateOf(false)
        private set
    var sent by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var forwardNote by mutableStateOf("")
        private set

    val title = when (mode) {
        "reply", "replyAll" -> "Ответ"
        "forward" -> "Пересылка"
        else -> "Новое письмо"
    }

    private var inReplyTo: String? = null
    private var references: String? = null
    private var forwardOf: Long? = null

    init {
        viewModelScope.launch {
            if (fromId <= 0) fromId = repo.allAccounts().firstOrNull()?.id ?: -1L
            if (refId > 0) prefill()
        }
    }

    private suspend fun prefill() {
        runCatching { repo.loadBody(refId) }
        val m = repo.getMessage(refId) ?: return
        val original = m.bodyText?.takeIf { it.isNotBlank() } ?: m.bodyHtml?.let { MailParser.htmlToText(it) } ?: ""
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(m.date))
        val sender = if (m.fromName.isNotBlank()) "${m.fromName} <${m.fromAddress}>" else m.fromAddress
        when (mode) {
            "reply", "replyAll" -> {
                to = m.fromAddress
                if (mode == "replyAll") {
                    val own = repo.getAccount(m.accountId)?.email?.lowercase()
                    cc = (splitAddrs(m.toList) + splitAddrs(m.ccList))
                        .filter { extractEmail(it).lowercase() != own && extractEmail(it).lowercase() != m.fromAddress.lowercase() }
                        .distinct()
                        .joinToString(", ")
                }
                subject = prefixed("Re: ", m.subject)
                inReplyTo = m.messageIdHeader
                references = listOfNotNull(m.refsHeader, m.messageIdHeader).joinToString(" ").ifBlank { null }
                body = "\n\n$date, $sender:\n" + original.lines().joinToString("\n") { "> $it" }
            }
            "forward" -> {
                subject = prefixed("Fwd: ", m.subject)
                forwardOf = m.id
                if (m.hasAttachments) forwardNote = "Вложения исходного письма будут пересланы."
                body = "\n\n---------- Пересланное сообщение ----------\n" +
                    "От: $sender\nДата: $date\nТема: ${m.subject}\nКому: ${m.toList}\n\n$original"
            }
        }
    }

    private fun prefixed(prefix: String, s: String): String =
        if (s.trim().startsWith(prefix.trim(), ignoreCase = true)) s else prefix + s

    private fun splitAddrs(s: String) = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun extractEmail(s: String) = s.substringAfter('<', s).substringBefore('>').trim()

    fun send() {
        if (sending) return
        if (fromId <= 0) { error = "Выберите ящик-отправитель"; return }
        if (to.isBlank()) { error = "Укажите получателя"; return }
        sending = true
        viewModelScope.launch {
            try {
                repo.send(
                    OutgoingMail(
                        accountId = fromId, to = to, cc = cc, subject = subject, body = body,
                        inReplyTo = inReplyTo, references = references, forwardOfMessageId = forwardOf,
                    )
                )
                sent = true
            } catch (e: Exception) {
                error = e.message ?: "Не удалось отправить"
            } finally {
                sending = false
            }
        }
    }
}
