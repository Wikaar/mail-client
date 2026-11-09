package com.mailtabs.app.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.mailtabs.app.data.crypto.PasswordCipher
import com.mailtabs.app.data.db.AccountEntity
import com.mailtabs.app.data.db.ActionType
import com.mailtabs.app.data.db.AppDatabase
import com.mailtabs.app.data.db.AttachmentEntity
import com.mailtabs.app.data.db.FolderEntity
import com.mailtabs.app.data.db.FolderRole
import com.mailtabs.app.data.db.MessageEntity
import com.mailtabs.app.data.db.MessageListItem
import com.mailtabs.app.data.db.PendingActionEntity
import com.mailtabs.app.data.mail.ImapOps
import com.mailtabs.app.data.mail.MailParser
import com.mailtabs.app.data.mail.MailSessions
import com.mailtabs.app.push.Notifier
import com.mailtabs.app.work.WorkScheduler
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.IMAPStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.activation.DataHandler
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.FolderNotFoundException
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

class MailException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class OutgoingMail(
    val accountId: Long,
    val to: String,
    val cc: String,
    val subject: String,
    val body: String,
    val inReplyTo: String? = null,
    val references: String? = null,
    val forwardOfMessageId: Long? = null,
)

@Singleton
class MailRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val cipher: PasswordCipher,
    private val scheduler: WorkScheduler,
    private val notifier: Notifier,
) {
    private val accountDao = db.accountDao()
    private val folderDao = db.folderDao()
    private val messageDao = db.messageDao()
    private val attachmentDao = db.attachmentDao()
    private val pendingDao = db.pendingActionDao()

    /** Скоуп для действий, которые должны пережить экран (удаление после Snackbar и т.п.). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val locks = ConcurrentHashMap<Long, Mutex>()
    private fun lock(accountId: Long) = locks.getOrPut(accountId) { Mutex() }

    // ---------- Наблюдение ----------

    val accounts: Flow<List<AccountEntity>> = accountDao.observeAll()

    val inboxUnread: Flow<Map<Long, Int>> =
        folderDao.observeInboxUnread().map { list -> list.associate { it.accountId to it.unread } }

    fun folders(accountId: Long): Flow<List<FolderEntity>> = folderDao.observe(accountId)
    fun messages(accountId: Long, folder: String): Flow<List<MessageListItem>> = messageDao.observeFolder(accountId, folder)
    fun observeMessage(id: Long): Flow<MessageEntity?> = messageDao.observe(id)
    fun attachments(messageId: Long): Flow<List<AttachmentEntity>> = attachmentDao.observe(messageId)

    suspend fun getMessage(id: Long) = messageDao.get(id)
    suspend fun getAccount(id: Long) = accountDao.get(id)
    suspend fun allAccounts() = accountDao.getAll()
    suspend fun folder(accountId: Long, fullName: String) = folderDao.get(accountId, fullName)
    suspend fun localCount(accountId: Long, folder: String) = messageDao.count(accountId, folder)

    fun password(account: AccountEntity): String = cipher.decrypt(account.encryptedPassword)

    suspend fun <T> withStore(
        account: AccountEntity,
        readTimeoutMs: Int = 60_000,
        block: suspend (IMAPStore) -> T,
    ): T = withContext(Dispatchers.IO) {
        val store = try {
            MailSessions.connectImap(account, password(account), readTimeoutMs)
        } catch (e: AuthenticationFailedException) {
            throw MailException("${account.name}: неверный логин или пароль", e)
        } catch (e: MessagingException) {
            throw MailException("${account.name}: нет соединения с сервером (${e.message})", e)
        }
        try {
            block(store)
        } finally {
            runCatching { store.close() }
        }
    }

    fun openIdleStore(account: AccountEntity, readTimeoutMs: Int): IMAPStore =
        MailSessions.connectImap(account, password(account), readTimeoutMs)

    // ---------- Ящики ----------

    suspend fun testConnection(account: AccountEntity, password: String) = withContext(Dispatchers.IO) {
        try {
            MailSessions.connectImap(account, password, 30_000).close()
        } catch (e: AuthenticationFailedException) {
            throw MailException("IMAP: неверный логин или пароль", e)
        } catch (e: Exception) {
            throw MailException("IMAP: не удалось подключиться к ${account.imapHost}:${account.imapPort} (${e.message})", e)
        }
        try {
            MailSessions.connectSmtp(account, password).close()
        } catch (e: AuthenticationFailedException) {
            throw MailException("SMTP: неверный логин или пароль", e)
        } catch (e: Exception) {
            throw MailException("SMTP: не удалось подключиться к ${account.smtpHost}:${account.smtpPort} (${e.message})", e)
        }
    }

    /**
     * Проверяет подключение и сохраняет ящик. [plainPassword] пустой при редактировании —
     * оставить прежний пароль.
     */
    suspend fun saveAccount(draft: AccountEntity, plainPassword: String): Long {
        val existing = if (draft.id > 0) accountDao.get(draft.id) else null
        val password = plainPassword.ifEmpty {
            existing?.let { password(it) } ?: throw MailException("Введите пароль")
        }
        testConnection(draft, password)
        val entity = draft.copy(
            encryptedPassword = cipher.encrypt(password),
            position = existing?.position ?: (accountDao.getAll().maxOfOrNull { it.position + 1 } ?: 0),
        )
        val id = if (existing != null) {
            accountDao.update(entity); entity.id
        } else {
            accountDao.insert(entity)
        }
        scope.launch {
            runCatching { syncFolders(id) }
            runCatching { syncFolder(id, "INBOX") }
        }
        return id
    }

    suspend fun deleteAccount(id: Long) = accountDao.delete(id)

    // ---------- Папки ----------

    suspend fun syncFolders(accountId: Long) {
        val account = accountDao.get(accountId) ?: return
        val remote = withStore(account) { store -> ImapOps.listFolders(store) }
        val existing = folderDao.getAll(accountId).associateBy { it.fullName }
        val entities = remote.mapIndexed { i, r ->
            FolderEntity(
                accountId = accountId,
                fullName = r.fullName,
                name = r.name,
                parent = r.parent,
                depth = r.depth,
                role = r.role,
                selectable = r.selectable,
                unread = r.unread,
                total = r.total,
                uidValidity = existing[r.fullName]?.uidValidity ?: 0,
                position = i,
            )
        }
        db.withTransaction {
            val keep = entities.map { it.fullName }.toSet()
            for (name in existing.keys) if (name !in keep) {
                folderDao.delete(accountId, name)
                messageDao.deleteFolder(accountId, name)
            }
            folderDao.upsertAll(entities)
        }
    }

    suspend fun createFolder(accountId: Long, parent: String?, name: String) {
        val account = accountDao.get(accountId) ?: return
        withStore(account) { store ->
            val full = if (parent.isNullOrEmpty()) name else {
                val sep = store.getFolder(parent).separator
                "$parent$sep$name"
            }
            val f = store.getFolder(full)
            if (f.exists()) throw MailException("Папка «$name» уже существует")
            if (!f.create(Folder.HOLDS_MESSAGES)) throw MailException("Сервер не создал папку «$name»")
            f.setSubscribed(true)
        }
        syncFolders(accountId)
    }

    suspend fun renameFolder(accountId: Long, fullName: String, newName: String) {
        val account = accountDao.get(accountId) ?: return
        withStore(account) { store ->
            val f = store.getFolder(fullName)
            val sep = f.separator
            val newFull = if (fullName.contains(sep)) fullName.substringBeforeLast(sep) + sep + newName else newName
            if (!f.renameTo(store.getFolder(newFull))) throw MailException("Сервер не переименовал папку")
        }
        messageDao.deleteFolder(accountId, fullName)
        syncFolders(accountId)
    }

    suspend fun deleteFolder(accountId: Long, fullName: String) {
        val account = accountDao.get(accountId) ?: return
        withStore(account) { store ->
            val f = store.getFolder(fullName)
            if (f.exists() && !f.delete(true)) throw MailException("Сервер не удалил папку")
        }
        messageDao.deleteFolder(accountId, fullName)
        folderDao.delete(accountId, fullName)
        syncFolders(accountId)
    }

    // ---------- Синхронизация писем ----------

    /**
     * Синхронизирует последние [WINDOW] писем папки. Возвращает новые непрочитанные
     * письма (пусто при первой синхронизации). При [notify] показывает уведомления.
     */
    suspend fun syncFolder(accountId: Long, folderName: String, notify: Boolean = false): List<MessageEntity> =
        lock(accountId).withLock {
            val account = accountDao.get(accountId) ?: return@withLock emptyList()
            runCatching { flushQueueLocked(account) }
            // Пока на сервер не ушли локальные изменения папки — не перетираем их.
            if (pendingDao.countForFolder(accountId, folderName) > 0) return@withLock emptyList()

            val fresh = withStore(account) { store -> syncFolderWith(store, account, folderName) }
            if (notify && fresh.isNotEmpty()) notifier.notifyNew(account, fresh)
            fresh
        }

    private suspend fun syncFolderWith(store: IMAPStore, account: AccountEntity, folderName: String): List<MessageEntity> {
        val accountId = account.id
        val f = store.getFolder(folderName) as IMAPFolder
        if (!f.exists()) {
            folderDao.delete(accountId, folderName)
            messageDao.deleteFolder(accountId, folderName)
            return emptyList()
        }
        f.open(Folder.READ_ONLY)
        try {
            val uidValidity = f.uidValidity
            val folderEntity = folderDao.get(accountId, folderName)
            val prevValidity = folderEntity?.uidValidity ?: 0L
            val firstSync = prevValidity == 0L
            if (prevValidity != 0L && prevValidity != uidValidity) messageDao.deleteFolder(accountId, folderName)

            val count = f.messageCount
            val unread = runCatching { f.unreadMessageCount }.getOrDefault(0)
            if (folderEntity == null) {
                folderDao.upsert(
                    FolderEntity(
                        accountId = accountId, fullName = folderName, name = f.name, parent = null, depth = 0,
                        role = if (folderName.equals("INBOX", true)) FolderRole.INBOX else FolderRole.OTHER,
                        selectable = true, unread = unread, total = count, uidValidity = uidValidity,
                    )
                )
            } else {
                folderDao.updateSyncState(accountId, folderName, unread, count, uidValidity)
            }
            if (count == 0) {
                messageDao.deleteFolder(accountId, folderName)
                return emptyList()
            }

            val msgs = f.getMessages(max(1, count - WINDOW + 1), count)
            val fp = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.FLAGS)
                add(FetchProfile.Item.CONTENT_INFO)
                add(UIDFolder.FetchProfileItem.UID)
            }
            f.fetch(msgs, fp)

            val local = messageDao.flagsForFolder(accountId, folderName).associateBy { it.uid }
            val serverUids = HashSet<Long>()
            val newOnes = ArrayList<Pair<Message, MessageEntity>>()
            val flagChanges = ArrayList<Triple<Long, Boolean, Boolean>>()
            for (m in msgs) {
                if (m.isExpunged) continue
                val uid = f.getUID(m)
                serverUids += uid
                val seen = m.isSet(Flags.Flag.SEEN)
                val flagged = m.isSet(Flags.Flag.FLAGGED)
                val l = local[uid]
                if (l == null) {
                    newOnes += m to toEntity(accountId, folderName, uid, m, seen, flagged)
                } else if (l.seen != seen || l.flagged != flagged) {
                    flagChanges += Triple(uid, seen, flagged)
                }
            }
            val minUid = serverUids.minOrNull() ?: 0L
            val removed = local.keys.filter { it >= minUid && it !in serverUids }

            db.withTransaction {
                removed.chunked(500).forEach { messageDao.deleteUids(accountId, folderName, it) }
                for ((uid, seen, flagged) in flagChanges) messageDao.updateFlags(accountId, folderName, uid, seen, flagged)
                messageDao.insertAll(newOnes.map { it.second })
            }

            // Второй проход: превью текста (заголовки уже видны в списке).
            val withPreview = ArrayList<MessageEntity>(newOnes.size)
            for ((m, e) in newOnes.sortedByDescending { it.second.date }) {
                val preview = runCatching { MailParser.preview(m) }.getOrDefault("")
                val hasAtt = e.hasAttachments || MailParser.hasAttachments(m)
                messageDao.setPreview(accountId, folderName, e.uid, preview, hasAtt)
                withPreview += e.copy(preview = preview, hasAttachments = hasAtt)
            }
            return if (firstSync) emptyList() else withPreview.filter { !it.seen }
        } finally {
            runCatching { f.close(false) }
        }
    }

    private fun toEntity(accountId: Long, folder: String, uid: Long, m: Message, seen: Boolean, flagged: Boolean): MessageEntity {
        val from = m.from?.firstOrNull() as? InternetAddress
        return MessageEntity(
            accountId = accountId,
            folder = folder,
            uid = uid,
            messageIdHeader = (m as? MimeMessage)?.messageID,
            fromName = from?.personal ?: "",
            fromAddress = from?.address ?: "",
            toList = addresses(m, Message.RecipientType.TO),
            ccList = addresses(m, Message.RecipientType.CC),
            subject = m.subject ?: "",
            preview = "",
            date = (m.receivedDate ?: m.sentDate)?.time ?: 0L,
            seen = seen,
            flagged = flagged,
            hasAttachments = runCatching { m.isMimeType("multipart/mixed") }.getOrDefault(false),
        )
    }

    private fun addresses(m: Message, type: Message.RecipientType): String =
        runCatching {
            m.getRecipients(type)?.joinToString(", ") { (it as? InternetAddress)?.toUnicodeString() ?: it.toString() }
        }.getOrNull() ?: ""

    suspend fun loadBody(messageId: Long) {
        val msg = messageDao.get(messageId) ?: return
        if (msg.bodyHtml != null || msg.bodyText != null) return
        val account = accountDao.get(msg.accountId) ?: return
        withStore(account) { store ->
            val f = store.getFolder(msg.folder) as IMAPFolder
            f.open(Folder.READ_ONLY)
            try {
                val m = f.getMessageByUID(msg.uid) ?: throw MailException("Письмо не найдено на сервере")
                val parsed = MailParser.parse(m)
                val refs = runCatching { m.getHeader("References")?.firstOrNull() }.getOrNull()
                db.withTransaction {
                    messageDao.setBody(messageId, parsed.html, parsed.text ?: if (parsed.html == null) "" else null, refs)
                    attachmentDao.deleteFor(messageId)
                    attachmentDao.insertAll(parsed.attachments.map {
                        AttachmentEntity(messageId = messageId, partPath = it.path, fileName = it.fileName, mimeType = it.mimeType, size = it.size)
                    })
                }
            } finally {
                runCatching { f.close(false) }
            }
        }
    }

    suspend fun downloadAttachment(attachmentId: Long): Pair<Uri, String> {
        val att = attachmentDao.get(attachmentId) ?: throw MailException("Вложение не найдено")
        val msg = messageDao.get(att.messageId) ?: throw MailException("Письмо не найдено")
        val account = accountDao.get(msg.accountId) ?: throw MailException("Ящик не найден")
        val uri = withStore(account) { store ->
            val f = store.getFolder(msg.folder) as IMAPFolder
            f.open(Folder.READ_ONLY)
            try {
                val m = f.getMessageByUID(msg.uid) ?: throw MailException("Письмо не найдено на сервере")
                val part = MailParser.findPart(m, att.partPath)
                part.inputStream.use { saveToDownloads(att.fileName, att.mimeType, it) }
            } finally {
                runCatching { f.close(false) }
            }
        }
        return uri to att.mimeType
    }

    private fun saveToDownloads(name: String, mime: String, input: InputStream): Uri {
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "attachment" }
        return if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw MailException("Не удалось сохранить файл")
            resolver.openOutputStream(uri)!!.use { input.copyTo(it) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
            val file = File(dir, safe)
            file.outputStream().use { input.copyTo(it) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }
    }

    // ---------- Действия с письмами (локально сразу, на сервер — через очередь) ----------

    fun toggleSeen(messageId: Long) = scope.launch {
        val m = messageDao.get(messageId) ?: return@launch
        setSeen(m, !m.seen)
    }

    suspend fun setSeen(m: MessageEntity, seen: Boolean) {
        if (m.seen == seen) return
        messageDao.setSeen(m.id, seen)
        folderDao.adjustCounts(m.accountId, m.folder, if (seen) -1 else 1, 0)
        enqueue(PendingActionEntity(accountId = m.accountId, type = if (seen) ActionType.SET_SEEN else ActionType.SET_UNSEEN, folder = m.folder, uid = m.uid))
    }

    fun setHidden(messageId: Long, hidden: Boolean) = scope.launch { messageDao.setHidden(messageId, hidden) }

    fun unhideAll(accountId: Long) = scope.launch { messageDao.unhideAll(accountId) }

    /** Удалить письмо: в Корзину, а из самой Корзины — окончательно. */
    fun deleteMessage(messageId: Long) = scope.launch {
        val m = messageDao.get(messageId) ?: return@launch
        val inTrash = folderDao.get(m.accountId, m.folder)?.role == FolderRole.TRASH
        val action = if (inTrash) {
            PendingActionEntity(accountId = m.accountId, type = ActionType.DELETE_PERMANENT, folder = m.folder, uid = m.uid)
        } else {
            PendingActionEntity(accountId = m.accountId, type = ActionType.MOVE, folder = m.folder, uid = m.uid, targetFolder = trashTarget(m.accountId))
        }
        enqueue(action)
        removeLocal(m)
    }

    fun moveMessage(messageId: Long, target: String) = scope.launch {
        val m = messageDao.get(messageId) ?: return@launch
        if (m.folder == target) return@launch
        enqueue(PendingActionEntity(accountId = m.accountId, type = ActionType.MOVE, folder = m.folder, uid = m.uid, targetFolder = target))
        removeLocal(m)
        folderDao.adjustCounts(m.accountId, target, if (m.seen) 0 else 1, 1)
    }

    private suspend fun removeLocal(m: MessageEntity) {
        messageDao.delete(m.id)
        folderDao.adjustCounts(m.accountId, m.folder, if (m.seen) 0 else -1, -1)
    }

    fun markAllRead(accountId: Long, folder: String) = scope.launch {
        val maxUid = messageDao.maxUid(accountId, folder) ?: 0L
        messageDao.markAllSeen(accountId, folder)
        val f = folderDao.get(accountId, folder)
        folderDao.setCounts(accountId, folder, 0, f?.total ?: 0)
        if (maxUid > 0) enqueue(PendingActionEntity(accountId = accountId, type = ActionType.MARK_ALL_SEEN, folder = folder, uid = maxUid))
    }

    fun deleteAll(accountId: Long, folder: String) = scope.launch {
        val maxUid = messageDao.maxUid(accountId, folder) ?: 0L
        val inTrash = folderDao.get(accountId, folder)?.role == FolderRole.TRASH
        if (maxUid > 0) {
            enqueue(
                if (inTrash) PendingActionEntity(accountId = accountId, type = ActionType.DELETE_ALL_PERMANENT, folder = folder, uid = maxUid)
                else PendingActionEntity(accountId = accountId, type = ActionType.MOVE_ALL, folder = folder, uid = maxUid, targetFolder = trashTarget(accountId))
            )
        }
        messageDao.deleteFolder(accountId, folder)
        folderDao.setCounts(accountId, folder, 0, 0)
    }

    private suspend fun trashTarget(accountId: Long): String =
        folderDao.byRole(accountId, FolderRole.TRASH.name)?.fullName ?: ImapOps.TRASH_TOKEN

    private suspend fun enqueue(action: PendingActionEntity) {
        pendingDao.insert(action)
        scheduler.flushActions(action.accountId)
    }

    /** @return true, если очередь полностью отправлена. */
    suspend fun flushQueue(accountId: Long): Boolean = lock(accountId).withLock {
        val account = accountDao.get(accountId) ?: return@withLock true
        flushQueueLocked(account)
    }

    private suspend fun flushQueueLocked(account: AccountEntity): Boolean {
        val actions = pendingDao.getAll(account.id)
        if (actions.isEmpty()) return true
        val trash = folderDao.byRole(account.id, FolderRole.TRASH.name)?.fullName ?: "Trash"
        return withStore(account) { store ->
            for (a in actions) {
                try {
                    ImapOps.apply(store, a) { trash }
                    pendingDao.delete(a.id)
                } catch (e: FolderNotFoundException) {
                    pendingDao.delete(a.id)
                } catch (e: MessagingException) {
                    if (!store.isConnected) throw e
                    if (a.attempts >= 4) {
                        pendingDao.delete(a.id)
                    } else {
                        pendingDao.incAttempts(a.id)
                        return@withStore false
                    }
                }
            }
            true
        }
    }

    // ---------- Отправка ----------

    suspend fun send(mail: OutgoingMail) = withContext(Dispatchers.IO) {
        val account = accountDao.get(mail.accountId) ?: throw MailException("Ящик не найден")
        val pwd = password(account)
        val session = MailSessions.smtpSession(account)
        val mime = MimeMessage(session)
        mime.setFrom(InternetAddress(account.email, account.senderName.ifBlank { null }, "UTF-8"))
        val to = parseAddresses(mail.to)
        if (to.isEmpty()) throw MailException("Укажите получателя")
        mime.setRecipients(Message.RecipientType.TO, to)
        parseAddresses(mail.cc).takeIf { it.isNotEmpty() }?.let { mime.setRecipients(Message.RecipientType.CC, it) }
        mime.setSubject(mail.subject, "UTF-8")
        mime.sentDate = Date()
        mail.inReplyTo?.let { mime.setHeader("In-Reply-To", it) }
        mail.references?.let { mime.setHeader("References", it) }

        val forwardParts = mail.forwardOfMessageId?.let { collectForwardParts(it) }.orEmpty()
        if (forwardParts.isEmpty()) {
            mime.setText(mail.body, "UTF-8")
        } else {
            val mp = MimeMultipart()
            mp.addBodyPart(MimeBodyPart().apply { setText(mail.body, "UTF-8") })
            forwardParts.forEach { mp.addBodyPart(it) }
            mime.setContent(mp)
        }
        mime.saveChanges()

        val transport = try {
            MailSessions.connectSmtp(account, pwd, session)
        } catch (e: AuthenticationFailedException) {
            throw MailException("SMTP: неверный логин или пароль", e)
        } catch (e: MessagingException) {
            throw MailException("SMTP: нет соединения (${e.message})", e)
        }
        try {
            transport.sendMessage(mime, mime.allRecipients)
        } finally {
            runCatching { transport.close() }
        }

        if (account.saveSentCopy) runCatching {
            val sent = folderDao.byRole(account.id, FolderRole.SENT.name)?.fullName ?: return@runCatching
            withStore(account) { store ->
                mime.setFlag(Flags.Flag.SEEN, true)
                store.getFolder(sent).appendMessages(arrayOf(mime))
            }
        }
        Unit
    }

    private fun parseAddresses(s: String): Array<InternetAddress> {
        val cleaned = s.replace(';', ',').trim().trimEnd(',')
        if (cleaned.isBlank()) return emptyArray()
        return try {
            InternetAddress.parse(cleaned, false)
        } catch (e: Exception) {
            throw MailException("Неверный адрес: $s")
        }
    }

    private suspend fun collectForwardParts(messageId: Long): List<MimeBodyPart> {
        val msg = messageDao.get(messageId) ?: return emptyList()
        val account = accountDao.get(msg.accountId) ?: return emptyList()
        return withStore(account) { store ->
            val f = store.getFolder(msg.folder) as IMAPFolder
            f.open(Folder.READ_ONLY)
            try {
                val m = f.getMessageByUID(msg.uid) ?: return@withStore emptyList()
                MailParser.parse(m).attachments.mapNotNull { a ->
                    runCatching {
                        val src = MailParser.findPart(m, a.path)
                        val bytes = src.inputStream.use { it.readBytes() }
                        MimeBodyPart().apply {
                            dataHandler = DataHandler(ByteArrayDataSource(bytes, a.mimeType))
                            fileName = a.fileName
                        }
                    }.getOrNull()
                }
            } finally {
                runCatching { f.close(false) }
            }
        }
    }

    companion object {
        const val WINDOW = 150
    }
}
