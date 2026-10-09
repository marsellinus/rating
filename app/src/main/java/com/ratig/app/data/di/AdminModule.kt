package com.ratig.app.data.di

import com.ratig.app.data.reports.ReportRepository
import com.ratig.app.data.remote.AuditRepositoryImpl
import com.ratig.app.data.remote.ReportRepositoryImpl
import com.ratig.app.data.remote.ScheduleRepositoryImpl
import com.ratig.app.domain.repository.AuditRepository
import com.ratig.app.domain.repository.ScheduleRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the admin/reports slice repositories. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AdminModule {

    @Binds
    @Singleton
    abstract fun bindScheduleRepository(impl: ScheduleRepositoryImpl): ScheduleRepository

    @Binds
    @Singleton
    abstract fun bindAuditRepository(impl: AuditRepositoryImpl): AuditRepository

    @Binds
    @Singleton
    abstract fun bindReportRepository(impl: ReportRepositoryImpl): ReportRepository
}
