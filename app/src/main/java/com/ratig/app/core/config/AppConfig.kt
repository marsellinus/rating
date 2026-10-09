package com.ratig.app.core.config

import com.ratig.app.BuildConfig

/**
 * Runtime application configuration sourced from local.properties at build time.
 *
 * Only PUBLIC values are embedded (Supabase project URL + anon/publishable key,
 * Google OAuth web client id). All row/column level protection lives in
 * PostgreSQL Row Level Security - see supabase/migrations.
 */
data class AppConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val googleWebClientId: String,
) {
    /**
     * Core backend is usable. Google client id is intentionally NOT part of
     * this check: debug builds can sign in with email/password without a
     * Google OAuth client (see [isGoogleConfigured]).
     */
    val isConfigured: Boolean
        get() = supabaseUrl.isNotBlank() &&
            supabaseAnonKey.isNotBlank() &&
            supabaseUrl.startsWith("https://")

    /** Google sign-in is available (Web client id present). */
    val isGoogleConfigured: Boolean
        get() = googleWebClientId.isNotBlank()

    companion object {
        fun fromBuildConfig() = AppConfig(
            supabaseUrl = BuildConfig.SUPABASE_URL.trim(),
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY.trim(),
            googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim(),
        )
    }
}
