package com.ratig.app.core.di

import com.ratig.app.core.config.AppConfig
import com.ratig.app.core.timing.MonotonicClock
import com.ratig.app.core.timing.SystemMonotonicClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.PropertyConversionMethod
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppConfig(): AppConfig = AppConfig.fromBuildConfig()

    /**
     * Single Supabase client. The anon/publishable key is a PUBLIC identifier -
     * authorization is enforced by PostgreSQL Row Level Security on every row.
     * Sessions are persisted automatically by auth-kt on Android and refreshed
     * transparently.
     */
    @Provides
    @Singleton
    fun provideSupabaseClient(config: AppConfig): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = config.supabaseUrl,
            supabaseKey = config.supabaseAnonKey,
        ) {
            install(Auth) {
                alwaysAutoRefresh = true
                autoLoadFromStorage = true
            }
            install(Postgrest) {
                propertyConversionMethod = PropertyConversionMethod.SERIAL_NAME
            }
            install(Functions)
        }

    /** Shared lenient Json for RPC payloads. */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideMonotonicClock(): MonotonicClock = SystemMonotonicClock()
}
