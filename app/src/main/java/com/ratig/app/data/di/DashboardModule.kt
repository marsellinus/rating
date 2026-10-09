package com.ratig.app.data.di

import com.ratig.app.data.remote.DashboardRepositoryImpl
import com.ratig.app.data.remote.FollowUpRepositoryImpl
import com.ratig.app.domain.repository.DashboardRepository
import com.ratig.app.domain.repository.FollowUpRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardModule {

    @Binds
    abstract fun bindDashboardRepository(impl: DashboardRepositoryImpl): DashboardRepository

    @Binds
    abstract fun bindFollowUpRepository(impl: FollowUpRepositoryImpl): FollowUpRepository
}
