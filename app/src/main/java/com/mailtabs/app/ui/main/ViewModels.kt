package com.mailtabs.app.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mailtabs.app.R
import com.mailtabs.app.data.MailRepository
import com.mailtabs.app.data.db.FolderEntity
import com.mailtabs.app.data.db.FolderRole
import com.mailtabs.app.data.db.MessageListItem
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountTab(val id: Long, val name: String, val unread: Int)

@HiltViewModel
class MainViewModel @Inject constructor(repo: MailRepository) : ViewModel() {
    /** null — ещё загружается. */
    val tabs: StateFlow<List<AccountTab>?> =
        combine(repo.accounts, repo.inboxUnread) { accounts, unread ->
            accounts.map { AccountTab(it.id, it.name, unread[it.id] ?: 0) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Состояние одной вкладки (одного ящика): своя папка, свой список, свои действия.
 * Создаётся по ключу "account_<id>", поэтому письма разных ящиков никогда не смешиваются.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = AccountViewModel.Factory::class)
class AccountViewModel @AssistedInject constructor(
    @Assisted val accountId: Long,
    private val repo: MailRepository,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(accountId: Long): AccountViewModel
    }

    private val _folder = MutableStateFlow("INBOX")
    val folderName: StateFlow<String> = _folder.asStateFlow()

    val folders: StateFlow<List<FolderEntity>> =
        repo.folders(accountId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentFolder: StateFlow<FolderEntity?> =
        combine(folders, _folder) { list, name -> list.firstOrNull { it.fullName == name } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _onlyFlagged = MutableStateFlow(false)
    /** Фильтр «Только важные» (звёздочка). */
    val onlyFlagged: StateFlow<Boolean> = _onlyFlagged.asStateFlow()

    val messages: StateFlow<List<MessageListItem>> =
        combine(_folder.flatMapLatest { repo.messages(accountId, it) }, _onlyFlagged) { list, only ->
            if (only) list.filter { it.flagged } else list
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleOnlyFlagged() { _onlyFlagged.value = !_onlyFlagged.value }
    fun toggleFlagged(messageId: Long) { repo.toggleFlagged(messageId) }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        repo.unhideAll(accountId)
        viewModelScope.launch {
            _refreshing.value = true
            try {
                repo.syncFolders(accountId)
                repo.syncFolder(accountId, _folder.value)
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    fun selectFolder(fullName: String) {
        if (_folder.value == fullName) return
        _folder.value = fullName
        refresh()
    }

    fun refresh() {
        val folder = _folder.value
        viewModelScope.launch {
            _refreshing.value = true
            try {
                repo.syncFolder(accountId, folder)
                if (folders.value.isEmpty()) repo.syncFolders(accountId)
            } catch (e: Exception) {
                _error.value = e.message ?: e.toString()
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun toggleSeen(messageId: Long) { repo.toggleSeen(messageId) }
    fun hide(messageId: Long) { repo.setHidden(messageId, true) }
    fun undoHide(messageId: Long) { repo.setHidden(messageId, false) }
    fun commitDelete(messageId: Long) { repo.deleteMessage(messageId) }
    fun move(messageId: Long, target: String) { repo.moveMessage(messageId, target) }
    fun markAllRead() { repo.markAllRead(accountId, _folder.value) }
    fun deleteAll() { repo.deleteAll(accountId, _folder.value) }

    /** Количество писем для диалога «Удалить все»: с сервера, если известно. */
    suspend fun countForDeleteAll(): Int {
        val serverTotal = currentFolder.value?.total ?: 0
        return maxOf(serverTotal, repo.localCount(accountId, _folder.value))
    }

    fun isTrash() = currentFolder.value?.role == FolderRole.TRASH

    fun createFolder(parent: String?, name: String) = folderOp { repo.createFolder(accountId, parent, name) }
    fun renameFolder(fullName: String, newName: String) = folderOp {
        repo.renameFolder(accountId, fullName, newName)
        if (_folder.value == fullName) _folder.value = "INBOX"
    }
    fun deleteFolder(fullName: String) = folderOp {
        repo.deleteFolder(accountId, fullName)
        if (_folder.value == fullName) _folder.value = "INBOX"
    }

    private fun folderOp(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { _error.value = e.message ?: e.toString() }
        }
    }
}

@Composable
fun accountViewModel(accountId: Long): AccountViewModel =
    hiltViewModel<AccountViewModel, AccountViewModel.Factory>(key = "account_$accountId") { it.create(accountId) }

@Composable
fun folderTitle(f: FolderEntity?, fallback: String): String = when (f?.role) {
    FolderRole.INBOX -> stringResource(R.string.folder_inbox)
    FolderRole.SENT -> stringResource(R.string.folder_sent)
    FolderRole.DRAFTS -> stringResource(R.string.folder_drafts)
    FolderRole.TRASH -> stringResource(R.string.folder_trash)
    FolderRole.SPAM -> stringResource(R.string.folder_spam)
    FolderRole.ARCHIVE -> stringResource(R.string.folder_archive)
    else -> f?.name ?: if (fallback == "INBOX") stringResource(R.string.folder_inbox) else fallback
}
