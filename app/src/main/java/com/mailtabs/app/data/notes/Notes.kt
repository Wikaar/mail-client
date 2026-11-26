package com.mailtabs.app.data.notes

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Заметки календаря. Отдельная база notes.db: почтовая mail.db не меняется
 * (у неё destructive-миграция, и любая правка схемы стёрла бы ящики).
 */
@Entity(tableName = "notes", indices = [Index("day")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LocalDate.toEpochDay(). */
    val day: Long,
    /** Минуты от начала дня; null — без времени. */
    val timeMinutes: Int? = null,
    val title: String,
    val text: String = "",
    /** Индекс цветной метки (0..4). */
    val color: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE day BETWEEN :fromDay AND :toDay ORDER BY day, timeMinutes IS NOT NULL, timeMinutes, id")
    fun observeRange(fromDay: Long, toDay: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
}

@Singleton
class NotesRepository @Inject constructor(db: NotesDatabase) {
    private val dao = db.noteDao()

    fun range(fromDay: Long, toDay: Long): Flow<List<NoteEntity>> = dao.observeRange(fromDay, toDay)

    suspend fun save(note: NoteEntity): Long =
        dao.upsert(note.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(id: Long) = dao.delete(id)
}
