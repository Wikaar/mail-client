package com.mailtabs.app.di

import com.mailtabs.app.data.MailRepository
import com.mailtabs.app.push.Notifier
import com.mailtabs.app.push.SyncController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Доступ к синглтонам из BroadcastReceiver'ов (без @AndroidEntryPoint). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppEntryPoint {
    fun repository(): MailRepository
    fun notifier(): Notifier
    fun syncController(): SyncController
}
