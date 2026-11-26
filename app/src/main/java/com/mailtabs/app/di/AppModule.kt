package com.mailtabs.app.di

import android.content.Context
import androidx.room.Room
import com.mailtabs.app.data.db.AppDatabase
import com.mailtabs.app.data.notes.NotesDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mail.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    /** Заметки календаря — отдельный файл, почтовая база не затрагивается. */
    @Provides
    @Singleton
    fun notesDatabase(@ApplicationContext context: Context): NotesDatabase =
        Room.databaseBuilder(context, NotesDatabase::class.java, "notes.db").build()
}
