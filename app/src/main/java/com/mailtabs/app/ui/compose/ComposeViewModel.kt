package com.mailtabs.app.ui.compose

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailtabs.app.R
import com.mailtabs.app.data.ComposeAttachment
import com.mailtabs.app.data.ComposeAttachmentStore
import com.mailtabs.app.data.MailRepository
import com.mailtabs.app.data.OutgoingMail
import com.mailtabs.app.data.db.AccountEntity
import com.mailtabs.app.data.mail.MailParser
import com.mailtabs.app.settings.Strings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/** Порог, после которого перед отправкой спрашиваем подтверждение. */
const val ATTACHMENTS_WARN_BYTES = 20L * 1024 * 1024

@HiltViewModel
class ComposeViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repo: MailRepository,
    private val store: ComposeAttachmentStore,
    private val strings: Strings,
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
    var attachments by mutableStateOf<List<ComposeAttachment>>(emptyList())
        private set

    var sending by mutableStateOf(false)
        private set
    var savingDraft by mutableStateOf(false)
        private set
    /** Сколько операций подготовки вложений идёт сейчас (копирование, загрузка при пересылке). */
    private var preparingCount by mutableIntStateOf(0)
    val preparing: Boolean get() = preparingCount > 0

    /** Экран можно закрыть: письмо отправлено, черновик сохранён или изменения отброшены. */
    var closed by mutableStateOf(false)
        private set
    /** Черновик сохранён — показать короткое уведомление после закрытия. */
    var draftSaved by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    /** Ошибка сохранения черновика: предложить выйти без сохранения. */
    var draftError by mutableStateOf<String?>(null)
    /** Спросить подтверждение отправки больших вложений. */
    var confirmLarge by mutableStateOf(false)
        private set

    val totalSize: Long get() = attachments.sumOf { it.size }

    val title = when (mode) {
        "reply", "replyAll" -> R.string.compose_reply
        "forward" -> R.string.compose_forward
        "draft" -> R.string.compose_draft
        else -> R.string.compose_new
    }

    private var inReplyTo: String? = null
    private var references: String? = null
    private var draftId: Long? = null
    /** Состояние сразу после заполнения: если пользователь ничего не менял, черновик не сохраняем. */
    private var initial: String = fingerprint()

    init {
        viewModelScope.launch {
            if (fromId <= 0) fromId = repo.allAccounts().firstOrNull()?.id ?: -1L
            if (refId > 0) prefill()
            initial = fingerprint()
        }
    }

    private fun fingerprint() = listOf(fromId, to, cc, subject, body, attachments.map { it.id }).toString()

    private suspend fun prefill() {
        runCatching { repo.loadBody(refId) }
        val m = repo.getMessage(refId) ?: return
        val original = m.bodyText?.takeIf { it.isNotBlank() } ?: m.bodyHtml?.let { MailParser.htmlToText(it) } ?: ""
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, strings.locale()).format(Date(m.date))
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
                body = "\n\n" + strings.get(R.string.quote_header, date, sender) + "\n" +
                    original.lines().joinToString("\n") { "> $it" }
            }
            "forward" -> {
                subject = prefixed("Fwd: ", m.subject)
                body = "\n\n" + strings.get(R.string.forward_header, sender, date, m.subject, m.toList) +
                    "\n\n$original"
                // Исходные вложения прикрепляются сразу и видны в списке — их можно убрать.
                if (m.hasAttachments) loadAttachmentsOf(m.id)
            }
            "draft" -> {
                fromId = m.accountId
                draftId = m.id
                to = m.toList
                cc = m.ccList
                subject = m.subject
                body = original
                references = m.refsHeader
                if (m.hasAttachments) loadAttachmentsOf(m.id)
            }
        }
    }

    private suspend fun loadAttachmentsOf(messageId: Long) {
        preparingCount++
        try {
            attachments = attachments + repo.copyAttachments(messageId)
        } catch (e: Exception) {
            error = e.message ?: strings.get(R.string.err_load_message)
        } finally {
            preparingCount--
        }
    }

    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            preparingCount++
            try {
                for (uri in uris) {
                    try {
                        val a = store.import(uri)
                        attachments = attachments + a
                    } catch (e: Exception) {
                        error = strings.get(R.string.err_attach, uri.lastPathSegment ?: uri.toString())
                    }
                }
            } finally {
                preparingCount--
            }
        }
    }

    fun removeAttachment(a: ComposeAttachment) {
        attachments = attachments - a
        store.delete(a)
    }

    private fun prefixed(prefix: String, s: String): String =
        if (s.trim().startsWith(prefix.trim(), ignoreCase = true)) s else prefix + s

    private fun splitAddrs(s: String) = s.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun extractEmail(s: String) = s.substringAfter('<', s).substringBefore('>').trim()

    private fun outgoing() = OutgoingMail(
        accountId = fromId, to = to, cc = cc, subject = subject, body = body,
        inReplyTo = inReplyTo, references = references,
        attachments = attachments, draftMessageId = draftId,
    )

    private val busy get() = sending || savingDraft || preparing

    fun send() {
        if (busy) return
        if (fromId <= 0) { error = strings.get(R.string.err_choose_sender); return }
        if (to.isBlank()) { error = strings.get(R.string.err_specify_recipient); return }
        if (totalSize > ATTACHMENTS_WARN_BYTES) { confirmLarge = true; return }
        doSend()
    }

    fun confirmSendLarge() { confirmLarge = false; doSend() }
    fun cancelSendLarge() { confirmLarge = false }

    private fun doSend() {
        sending = true
        viewModelScope.launch {
            try {
                repo.send(outgoing())
                closed = true
            } catch (e: Exception) {
                error = e.message ?: strings.get(R.string.err_send)
            } finally {
                sending = false
            }
        }
    }

    /** «Назад»: если что-то изменено — сохранить черновик на сервере (с вложениями), иначе просто закрыть. */
    fun close() {
        if (sending || savingDraft) return
        if (fingerprint() == initial || fromId <= 0) { discard(); return }
        savingDraft = true
        viewModelScope.launch {
            try {
                repo.saveDraft(outgoing())
                draftSaved = true
                closed = true
            } catch (e: Exception) {
                draftError = strings.get(R.string.err_save_draft, e.message.orEmpty())
            } finally {
                savingDraft = false
            }
        }
    }

    /** Закрыть без сохранения; локальные копии вложений удаляются (черновик на сервере не трогаем). */
    fun discard() {
        store.deleteAll(attachments)
        closed = true
    }
}
