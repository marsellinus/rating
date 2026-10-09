package com.ratig.app.data.di

import com.ratig.app.data.remote.ProtocolRepositoryImpl
import com.ratig.app.data.remote.TestSessionRepositoryImpl
import com.ratig.app.domain.repository.ProtocolRepository
import com.ratig.app.domain.repository.TestSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the test-flow (session + protocol) repository implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TestFlowModule {

    @Binds
    @Singleton
    abstract fun bindTestSessionRepository(impl: TestSessionRepositoryImpl): TestSessionRepository

    @Binds
    @Singleton
    abstract fun bindProtocolRepository(impl: ProtocolRepositoryImpl): ProtocolRepository
}
