import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // NOTE: org.jetbrains.kotlin.android is NOT applied here - AGP 9 provides
    // built-in Kotlin. KGP 2.4.20 is declared (apply false) in the root
    // build.gradle.kts to pin the built-in Kotlin version.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Load non-secret app configuration from local.properties (never committed).
// See local.properties.example and docs/oauth-setup.md.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(key: String): String = localProperties.getProperty(key) ?: ""

android {
    namespace = "com.ratig.app"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.ratig.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase project configuration. These are the PUBLIC anon/publishable
        // key and the project URL - safe to embed. All data protection is done
        // by Row Level Security on the server. NEVER put the service_role key here.
        buildConfigField("String", "SUPABASE_URL", "\"${localProp("supabase.url")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProp("supabase.anonKey")}\"")
        // Google OAuth "Web client ID" (server client id used by Credential Manager).
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProp("google.webClientId")}\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // NOTE: release builds need a signing config (see docs/deployment.md).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Navigation
    implementation(libs.navigation.compose)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Persistence (preferences only - Supabase PostgreSQL is the primary database)
    implementation(libs.datastore.preferences)

    // Async
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.serialization.json)

    // Supabase Kotlin SDK
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.android)

    // Google Sign-In via Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Offline-first (Room cache + WorkManager sync)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Barcode/QR scanning (worker identification)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.barcode.scanning)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}
