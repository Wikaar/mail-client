package com.mailtabs.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class Security { SSL, STARTTLS, NONE }

enum class FolderRole { INBOX, DRAFTS, SENT, SPAM, TRASH, ARCHIVE, OTHER }

enum class ActionType {
    SET_SEEN, SET_UNSEEN, SET_FLAGGED, SET_UNFLAGGED, MOVE, DELETE_PERMANENT,
    MARK_ALL_SEEN, MOVE_ALL, DELETE_ALL_PERMANENT,
}

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val senderName: String,
    val imapHost: String,
    val imapPort: Int,
    val imapSecurity: Security,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: Security,
    val username: String,
    val encryptedPassword: String,
    val saveSentCopy: Boolean = true,
    val position: Int = 0,
)

@Entity(
    tableName = "folders",
    primaryKeys = ["accountId", "fullName"],
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("accountId")],
)
data class FolderEntity(
    val accountId: Long,
    val fullName: String,
    val name: String,
    val parent: String?,
    val depth: Int,
    val role: FolderRole,
    val selectable: Boolean,
    val unread: Int,
    val total: Int,
    val uidValidity: Long = 0,
    val position: Int = 0,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["accountId", "folder", "uid"], unique = true),
        Index("accountId"),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val folder: String,
    val uid: Long,
    val messageIdHeader: String?,
    val refsHeader: String? = null,
    val fromName: String,
    val fromAddress: String,
    val toList: String,
    val ccList: String,
    val subject: String,
    val preview: String,
    val date: Long,
    val seen: Boolean,
    val flagged: Boolean,
    val hasAttachments: Boolean,
    /** Скрыто локально, пока висит Snackbar «Отменить». */
    val hidden: Boolean = false,
    val bodyHtml: String? = null,
    val bodyText: String? = null,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["messageId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("messageId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    /** Путь к части MIME-дерева, например "1.0". */
    val partPath: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
)

/** Офлайн-очередь действий, которые нужно применить на сервере по IMAP. */
@Entity(
    tableName = "pending_actions",
    foreignKeys = [ForeignKey(
        entity = AccountEntity::class,
        parentColumns = ["id"],
        childColumns = ["accountId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("accountId")],
)
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val type: ActionType,
    val folder: String,
    /** UID письма; для *_ALL — максимальный UID, до которого применять. */
    val uid: Long = 0,
    val targetFolder: String? = null,
    val attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

data class InboxUnread(val accountId: Long, val unread: Int)

data class LocalFlags(val uid: Long, val seen: Boolean, val flagged: Boolean)

data class MessageListItem(
    val id: Long,
    val accountId: Long,
    val folder: String,
    val uid: Long,
    val fromName: String,
    val fromAddress: String,
    val subject: String,
    val preview: String,
    val date: Long,
    val seen: Boolean,
    val flagged: Boolean,
    val hasAttachments: Boolean,
)
