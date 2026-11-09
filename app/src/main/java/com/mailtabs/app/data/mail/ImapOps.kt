package com.mailtabs.app.data.mail

import com.mailtabs.app.data.db.ActionType
import com.mailtabs.app.data.db.FolderRole
import com.mailtabs.app.data.db.PendingActionEntity
import org.eclipse.angus.mail.imap.IMAPFolder
import org.eclipse.angus.mail.imap.IMAPStore
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.FolderNotFoundException
import jakarta.mail.Message

/** Синхронные (блокирующие) операции над IMAP. Вызывать только из IO-потока. */
object ImapOps {

    const val TRASH_TOKEN = "\u0000TRASH"

    data class RemoteFolder(
        val fullName: String,
        val name: String,
        val parent: String?,
        val depth: Int,
        val role: FolderRole,
        val selectable: Boolean,
        val unread: Int,
        val total: Int,
    )

    fun listFolders(store: IMAPStore): List<RemoteFolder> {
        val folders = store.defaultFolder.list("*").map { it as IMAPFolder }

        data class Raw(
            val f: IMAPFolder, val attrs: Set<String>, val selectable: Boolean,
            val attrRole: FolderRole?, val nameRole: FolderRole?,
        )

        val raw = folders.map { f ->
            val attrs = (f.attributes ?: emptyArray()).map { it.lowercase() }.toSet()
            val selectable = (f.type and Folder.HOLDS_MESSAGES) != 0 &&
                "\\noselect" !in attrs && "\\nonexistent" !in attrs
            Raw(f, attrs, selectable, roleFromAttrs(f.fullName, attrs), roleFromName(f.name))
        }

        // У каждой специальной роли — ровно одна папка: SPECIAL-USE важнее имени.
        val roleOf = HashMap<String, FolderRole>()
        for (role in FolderRole.entries) {
            if (role == FolderRole.OTHER) continue
            val pick = raw.firstOrNull { it.attrRole == role && it.selectable }
                ?: raw.firstOrNull { it.nameRole == role && it.selectable && it.f.fullName !in roleOf }
            if (pick != null && pick.f.fullName !in roleOf) roleOf[pick.f.fullName] = role
        }

        val result = raw.map { r ->
            val f = r.f
            val sep = runCatching { f.separator }.getOrDefault('/')
            val full = f.fullName
            var unread = 0
            var total = 0
            if (r.selectable) runCatching {
                total = f.messageCount
                unread = f.unreadMessageCount
            }
            val parent = if (full.contains(sep)) full.substringBeforeLast(sep) else null
            RemoteFolder(
                fullName = full,
                name = f.name,
                parent = parent,
                depth = full.count { it == sep },
                role = roleOf[full] ?: FolderRole.OTHER,
                selectable = r.selectable,
                unread = unread,
                total = total,
            )
        }

        // Порядок: Входящие, системные, затем пользовательские; вложенные — под родителем.
        val byName = result.associateBy { it.fullName }
        fun rootRank(rf: RemoteFolder): Int {
            var cur = rf
            while (cur.parent != null) cur = byName[cur.parent] ?: break
            return if (cur.role == FolderRole.OTHER) 100 else cur.role.ordinal
        }
        return result.sortedWith(compareBy({ rootRank(it) }, { it.fullName.lowercase() }))
    }

    private fun roleFromAttrs(fullName: String, attrs: Set<String>): FolderRole? = when {
        fullName.equals("INBOX", ignoreCase = true) -> FolderRole.INBOX
        "\\trash" in attrs -> FolderRole.TRASH
        "\\sent" in attrs -> FolderRole.SENT
        "\\drafts" in attrs -> FolderRole.DRAFTS
        "\\junk" in attrs -> FolderRole.SPAM
        "\\archive" in attrs -> FolderRole.ARCHIVE
        else -> null
    }

    private fun roleFromName(name: String): FolderRole? = when (name.lowercase()) {
        "trash", "deleted", "deleted items", "deleted messages", "корзина", "удаленные", "удалённые",
        "papierkorb", "gelöschte objekte", "gelöschte elemente" -> FolderRole.TRASH
        "sent", "sent items", "sent messages", "sent mail", "отправленные", "gesendet",
        "gesendete objekte", "gesendete elemente" -> FolderRole.SENT
        "drafts", "draft", "черновики", "entwürfe" -> FolderRole.DRAFTS
        "spam", "junk", "junk e-mail", "junk-e-mail", "спам" -> FolderRole.SPAM
        "archive", "archiv", "архив" -> FolderRole.ARCHIVE
        else -> null
    }

    /** Применяет действие из офлайн-очереди на сервере. */
    fun apply(store: IMAPStore, a: PendingActionEntity, trashName: () -> String) {
        val f = store.getFolder(a.folder) as IMAPFolder
        if (!f.exists()) throw FolderNotFoundException(f, "Папка ${a.folder} не найдена")
        f.open(Folder.READ_WRITE)
        var expunge = false
        try {
            when (a.type) {
                ActionType.SET_SEEN, ActionType.SET_UNSEEN ->
                    f.getMessageByUID(a.uid)?.setFlag(Flags.Flag.SEEN, a.type == ActionType.SET_SEEN)

                ActionType.MOVE -> {
                    val m = f.getMessageByUID(a.uid) ?: return
                    expunge = move(store, f, arrayOf(m), target(store, a, trashName))
                }

                ActionType.DELETE_PERMANENT -> {
                    f.getMessageByUID(a.uid)?.setFlag(Flags.Flag.DELETED, true)
                    expunge = true
                }

                ActionType.MARK_ALL_SEEN -> {
                    val ms = upTo(f, a.uid)
                    if (ms.isNotEmpty()) f.setFlags(ms, Flags(Flags.Flag.SEEN), true)
                }

                ActionType.MOVE_ALL -> {
                    val ms = upTo(f, a.uid)
                    if (ms.isNotEmpty()) expunge = move(store, f, ms, target(store, a, trashName))
                }

                ActionType.DELETE_ALL_PERMANENT -> {
                    val ms = upTo(f, a.uid)
                    if (ms.isNotEmpty()) {
                        f.setFlags(ms, Flags(Flags.Flag.DELETED), true)
                        expunge = true
                    }
                }
            }
        } finally {
            runCatching { f.close(expunge) }
        }
    }

    private fun upTo(f: IMAPFolder, maxUid: Long): Array<Message> =
        f.getMessagesByUID(1, maxUid).filterNotNull().toTypedArray()

    private fun target(store: IMAPStore, a: PendingActionEntity, trashName: () -> String): Folder {
        val tf = a.targetFolder
        val isTrash = tf == null || tf == TRASH_TOKEN
        val name: String = if (tf == null || tf == TRASH_TOKEN) trashName() else tf
        val t = store.getFolder(name)
        if (!t.exists()) {
            if (isTrash) t.create(Folder.HOLDS_MESSAGES)
            else throw FolderNotFoundException(t, "Папка $name не найдена")
        }
        return t
    }

    /** @return true, если после операции нужно сделать EXPUNGE исходной папки. */
    private fun move(store: IMAPStore, from: IMAPFolder, msgs: Array<Message>, to: Folder): Boolean {
        return if (store.hasCapability("MOVE")) {
            from.moveMessages(msgs, to)
            false
        } else {
            from.copyMessages(msgs, to)
            from.setFlags(msgs, Flags(Flags.Flag.DELETED), true)
            true
        }
    }
}
