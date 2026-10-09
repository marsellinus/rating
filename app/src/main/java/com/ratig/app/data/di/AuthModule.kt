package com.ratig.app.data.di

import com.ratig.app.data.remote.AuthRepositoryImpl
import com.ratig.app.data.remote.ProfileRepositoryImpl
import com.ratig.app.domain.repository.AuthRepository
import com.ratig.app.domain.repository.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository
}
