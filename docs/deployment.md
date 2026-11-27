# Deployment

How to build, configure, and ship RATIG.

## 1. Toolchain

| Tool | Version |
|---|---|
| JDK | 26 (build machine) |
| Gradle | 9.8.0 (wrapper) |
| Android Gradle Plugin | 9.4.1 |
| Kotlin | 2.4.20 (AGP built-in Kotlin; KGP pinned `apply false`) |
| compileSdk | 37 |
| minSdk | 26 |
| targetSdk | 36 |

Use the wrapper (`./gradlew`) — never a system Gradle.

## 2. Local configuration (public values only)

Copy `local.properties.example` to `local.properties` (never committed) and
fill in:

```properties
sdk.dir=/path/to/Android/Sdk

# Supabase project URL, e.g. https://xxxxxxxx.supabase.co
supabase.url=
# Supabase public anon (publishable) key — NEVER the service_role key.
supabase.anonKey=
# OPTIONAL — leave blank for email/password-only login.
google.webClientId=
```

Only `supabase.url` and `supabase.anonKey` are required. These values are
injected into `BuildConfig` (`SUPABASE_URL`, `SUPABASE_ANON_KEY`,
`GOOGLE_WEB_CLIENT_ID`) and surfaced by `core/config/AppConfig.kt`. If either
required value is blank or the URL is not `https://`, `AppConfig.isConfigured`
is false and the app shows the "not configured" state instead of attempting a
broken login. A blank `google.webClientId` simply hides the optional Google
button; email/password login remains fully functional.

> **Security:** the anon key is public by design — all data protection is
> enforced by Row Level Security. The **service-role key must never** be placed
> in `local.properties`, `BuildConfig`, or the APK.

## 3. Backend

Apply migrations in order and deploy the Edge Functions — see
`supabase/README.md`. In short:

```bash
supabase link --project-ref <project-ref>
supabase db push                 # 0001–0013
supabase functions deploy approve-account
supabase functions deploy admin-create-user
supabase functions deploy admin-create-worker
supabase functions deploy export-report
```

If the project already has 0001–0004, paste `supabase/APPLY_ALL.sql`
(0005–0013, idempotent) into the Supabase SQL Editor instead of re-pushing.
It includes the worker NIK trigger, operational-mode fixes, data backfills,
and the RLS lock that stops an `admin` from writing master data or promoting
users to `admin`.

Then promote the first super admin (see `supabase/README.md` §3).

## 4. Build

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:testDebugUnitTest    # JVM unit tests
./gradlew :app:assembleRelease      # release APK (needs signing, below)
```

Debug output: `app/build/outputs/apk/debug/app-debug.apk`.

## 5. Release signing

The release build type currently reuses the **debug** signing config as a
placeholder so CI can produce an installable artifact. Before distributing:

1. Generate an upload keystore and keep it out of VCS.
2. Add a `signingConfigs { create("release") { … } }` block in
   `app/build.gradle.kts` reading the keystore path/passwords from environment
   variables (never hard-code them).
3. Point `buildTypes.release.signingConfig` at it.
4. Only if the optional Google button is enabled: register the release SHA-1
   in Google Cloud (see `docs/oauth-setup.md`). Email/password builds need no
   Google Cloud configuration.

Release builds have `isMinifyEnabled = true` and `isShrinkResources = true`; if
reflection-based code breaks after shrinking, extend `proguard-rules.pro`.

## 6. Verification checklist before shipping

- [ ] `./gradlew :app:testDebugUnitTest` → **140 tests**, all green (metrics,
      classifier, NIK/QR, CSV, sync-failure, `jsonb`, dashboard parsers).
- [ ] `./gradlew :app:assembleRelease` succeeds.
- [ ] `supabase/README.md` §5 RLS scenarios all behave as documented against the
      target project, including §6: `admin` cannot write master data, cannot
      promote a user to `admin`, and cannot create an admin via Edge Function.
- [ ] `finalize_test_session` is idempotent (refinalize returns the stored
      result) — covered by the backend smoke test.
- [ ] Offline flow: start a test with the device in airplane mode, finalize,
      confirm it queues, then reconnect and confirm it syncs.
- [ ] Scanner flow: grant CAMERA only on first scan; verify manual NIK entry and
      QR `RATIG1:<NIK>` both resolve the worker.

## 7. Notes

- `local.properties` and `*.jks`/`*.keystore` are git-ignored.
- The app requests `INTERNET`, `ACCESS_NETWORK_STATE`, and `CAMERA` (the last
  only when the scanner opens).
- Reports are shared via `FileProvider` (`com.ratig.app.fileprovider`); no
  storage permissions are required.
