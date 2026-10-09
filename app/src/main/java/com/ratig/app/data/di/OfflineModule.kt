package com.ratig.app.data.di

import android.content.Context
import androidx.room.Room
import com.ratig.app.data.local.RatigDatabase
import com.ratig.app.data.local.dao.AuditEventDao
import com.ratig.app.data.local.dao.ProfileDao
import com.ratig.app.data.local.dao.ProtocolDao
import com.ratig.app.data.local.dao.SessionDao
import com.ratig.app.data.local.dao.SyncQueueDao
import com.ratig.app.data.local.dao.WorkerDao
import com.ratig.app.data.offline.ConnectivityManagerNetworkMonitor
import com.ratig.app.data.offline.NetworkMonitor
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the offline-first Room database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object OfflineProvidesModule {

    /**
     * Room database in the app's INTERNAL storage (default location). The
     * file contains worker/NIK data - it must never be moved to external or
     * removable storage, and its contents never reach logs.
     */
    @Provides
    @Singleton
    fun provideRatigDatabase(@ApplicationContext context: Context): RatigDatabase =
        Room.databaseBuilder(context, RatigDatabase::class.java, RatigDatabase.NAME)
            .build()

    @Provides
    fun provideProfileDao(database: RatigDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideWorkerDao(database: RatigDatabase): WorkerDao = database.workerDao()

    @Provides
    fun provideProtocolDao(database: RatigDatabase): ProtocolDao = database.protocolDao()

    @Provides
    fun provideSessionDao(database: RatigDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideSyncQueueDao(database: RatigDatabase): SyncQueueDao = database.syncQueueDao()

    @Provides
    fun provideAuditEventDao(database: RatigDatabase): AuditEventDao = database.auditEventDao()
}

/** Interface bindings for the offline core. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OfflineBindingsModule {

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: ConnectivityManagerNetworkMonitor): NetworkMonitor
}
