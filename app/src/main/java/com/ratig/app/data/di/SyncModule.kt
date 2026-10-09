package com.ratig.app.data.di

import com.ratig.app.data.sync.SyncRepository
import com.ratig.app.data.sync.SyncRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

/**
 * Sync engine DI (CONTRACT-2 §Sync engine). Binds the repository facade and
 * provides the long-lived scope that feeds SyncStatusBus from Room/Network
 * Flows for as long as the process lives.
 */
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    @Named(com.ratig.app.data.sync.SyncRepositoryImpl.SYNC_SCOPE)
    fun provideSyncScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindsModule {

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository
}
