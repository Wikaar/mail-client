package com.mailtabs.app.ui.message

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailtabs.app.data.MailRepository
import com.mailtabs.app.data.db.AttachmentEntity
import com.mailtabs.app.data.db.FolderEntity
import com.mailtabs.app.data.db.MessageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MessageEvent {
    data object Close : MessageEvent
    data class Error(val text: String) : MessageEvent
    data class OpenFile(val uri: Uri, val mime: String, val name: String) : MessageEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MessageViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repo: MailRepository,
) : ViewModel() {

    private val id: Long = checkNotNull(saved.get<Long>("messageId"))

    val message: StateFlow<MessageEntity?> =
        repo.observeMessage(id).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val attachments: StateFlow<List<AttachmentEntity>> =
        repo.attachments(id).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val folders: StateFlow<List<FolderEntity>> =
        message.filterNotNull().map { it.accountId }.distinctUntilChanged()
            .flatMapLatest { repo.folders(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = Channel<MessageEvent>(Channel.BUFFERED)
    val events: Flow<MessageEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val m = repo.getMessage(id) ?: return@launch
            if (!m.seen) repo.setSeen(m, true)
            if (m.bodyHtml == null && m.bodyText == null) {
                _loading.value = true
                try {
                    repo.loadBody(id)
                } catch (e: Exception) {
                    _events.send(MessageEvent.Error(e.message ?: "Не удалось загрузить письмо"))
                } finally {
                    _loading.value = false
                }
            }
        }
    }

    fun delete() {
        repo.deleteMessage(id)
        viewModelScope.launch { _events.send(MessageEvent.Close) }
    }

    fun move(target: String) {
        repo.moveMessage(id, target)
        viewModelScope.launch { _events.send(MessageEvent.Close) }
    }

    fun markUnreadAndClose() {
        viewModelScope.launch {
            repo.getMessage(id)?.let { repo.setSeen(it, false) }
            _events.send(MessageEvent.Close)
        }
    }

    fun download(a: AttachmentEntity) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val (uri, mime) = repo.downloadAttachment(a.id)
                _events.send(MessageEvent.OpenFile(uri, mime, a.fileName))
            } catch (e: Exception) {
                _events.send(MessageEvent.Error(e.message ?: "Не удалось скачать вложение"))
            } finally {
                _loading.value = false
            }
        }
    }
}
