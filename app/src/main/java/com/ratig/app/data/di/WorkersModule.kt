package com.ratig.app.data.di

import com.ratig.app.data.remote.OrganizationRepositoryImpl
import com.ratig.app.data.remote.WorkerRepositoryImpl
import com.ratig.app.domain.repository.OrganizationRepository
import com.ratig.app.domain.repository.WorkerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkersModule {

    @Binds
    abstract fun bindWorkerRepository(impl: WorkerRepositoryImpl): WorkerRepository

    @Binds
    abstract fun bindOrganizationRepository(impl: OrganizationRepositoryImpl): OrganizationRepository
}
