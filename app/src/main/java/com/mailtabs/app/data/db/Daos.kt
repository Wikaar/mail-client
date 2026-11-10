package com.mailtabs.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY position, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY position, id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE accountId = :accountId ORDER BY position")
    fun observe(accountId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE accountId = :accountId ORDER BY position")
    suspend fun getAll(accountId: Long): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND fullName = :fullName")
    suspend fun get(accountId: Long, fullName: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND role = :role LIMIT 1")
    suspend fun byRole(accountId: Long, role: String): FolderEntity?

    @Query("SELECT accountId, unread FROM folders WHERE role = 'INBOX'")
    fun observeInboxUnread(): Flow<List<InboxUnread>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(folders: List<FolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE accountId = :accountId AND fullName = :fullName")
    suspend fun delete(accountId: Long, fullName: String)

    @Query(
        "UPDATE folders SET unread = :unread, total = :total, uidValidity = :uidValidity " +
            "WHERE accountId = :accountId AND fullName = :fullName"
    )
    suspend fun updateSyncState(accountId: Long, fullName: String, unread: Int, total: Int, uidValidity: Long)

    @Query(
        "UPDATE folders SET unread = MAX(0, unread + :unreadDelta), total = MAX(0, total + :totalDelta) " +
            "WHERE accountId = :accountId AND fullName = :fullName"
    )
    suspend fun adjustCounts(accountId: Long, fullName: String, unreadDelta: Int, totalDelta: Int)

    @Query("UPDATE folders SET unread = :unread, total = :total WHERE accountId = :accountId AND fullName = :fullName")
    suspend fun setCounts(accountId: Long, fullName: String, unread: Int, total: Int)
}

@Dao
interface MessageDao {
    @Query(
        "SELECT id, accountId, folder, uid, fromName, fromAddress, subject, preview, date, seen, flagged, hasAttachments " +
            "FROM messages WHERE accountId = :accountId AND folder = :folder AND hidden = 0 ORDER BY date DESC"
    )
    fun observeFolder(accountId: Long, folder: String): Flow<List<MessageListItem>>

    @Query("SELECT * FROM messages WHERE id = :id")
    fun observe(id: Long): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: Long): MessageEntity?

    @Query("SELECT uid, seen, flagged FROM messages WHERE accountId = :accountId AND folder = :folder")
    suspend fun flagsForFolder(accountId: Long, folder: String): List<LocalFlags>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MessageEntity>): List<Long>

    @Query("UPDATE messages SET seen = :seen, flagged = :flagged WHERE accountId = :accountId AND folder = :folder AND uid = :uid")
    suspend fun updateFlags(accountId: Long, folder: String, uid: Long, seen: Boolean, flagged: Boolean)

    @Query("DELETE FROM messages WHERE accountId = :accountId AND folder = :folder AND uid IN (:uids)")
    suspend fun deleteUids(accountId: Long, folder: String, uids: List<Long>)

    @Query("DELETE FROM messages WHERE accountId = :accountId AND folder = :folder")
    suspend fun deleteFolder(accountId: Long, folder: String)

    @Query("UPDATE messages SET preview = :preview, hasAttachments = :hasAttachments WHERE accountId = :accountId AND folder = :folder AND uid = :uid")
    suspend fun setPreview(accountId: Long, folder: String, uid: Long, preview: String, hasAttachments: Boolean)

    @Query("UPDATE messages SET seen = :seen WHERE id = :id")
    suspend fun setSeen(id: Long, seen: Boolean)

    @Query("UPDATE messages SET flagged = :flagged WHERE id = :id")
    suspend fun setFlagged(id: Long, flagged: Boolean)

    @Query("UPDATE messages SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("UPDATE messages SET hidden = 0 WHERE accountId = :accountId AND hidden = 1")
    suspend fun unhideAll(accountId: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE messages SET bodyHtml = :html, bodyText = :text, refsHeader = :refs WHERE id = :id")
    suspend fun setBody(id: Long, html: String?, text: String?, refs: String?)

    @Query("UPDATE messages SET seen = 1 WHERE accountId = :accountId AND folder = :folder")
    suspend fun markAllSeen(accountId: Long, folder: String)

    @Query("SELECT MAX(uid) FROM messages WHERE accountId = :accountId AND folder = :folder")
    suspend fun maxUid(accountId: Long, folder: String): Long?

    @Query("SELECT COUNT(*) FROM messages WHERE accountId = :accountId AND folder = :folder AND hidden = 0")
    suspend fun count(accountId: Long, folder: String): Int
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY id")
    fun observe(messageId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun get(id: Long): AttachmentEntity?

    @Insert
    suspend fun insertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE messageId = :messageId")
    suspend fun deleteFor(messageId: Long)
}

@Dao
interface PendingActionDao {
    @Insert
    suspend fun insert(action: PendingActionEntity): Long

    @Query("SELECT * FROM pending_actions WHERE accountId = :accountId ORDER BY id")
    suspend fun getAll(accountId: Long): List<PendingActionEntity>

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE pending_actions SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incAttempts(id: Long)

    @Query("SELECT COUNT(*) FROM pending_actions WHERE accountId = :accountId AND folder = :folder")
    suspend fun countForFolder(accountId: Long, folder: String): Int
}
