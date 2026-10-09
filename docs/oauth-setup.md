# Google OAuth setup

RATIG signs users in with **Google ID tokens via Android Credential Manager**,
then exchanges the token with Supabase Auth. There is no WebView and no
`compose-auth` plugin.

Flow (`data/remote/AuthRepositoryImpl.kt`):

1. `CredentialManager.getCredential(...)` with `GetGoogleIdOption`
   (`serverClientId = google.webClientId`).
2. Read the ID token from `GoogleIdTokenCredential`.
3. `supabase.auth.signInWith(IDToken) { provider = Google }`.
4. Load the `profiles` row to decide `ACTIVE` vs `PENDING`/`SUSPENDED`.

The only value the app needs is the **Web client ID** — it goes in
`local.properties` as `google.webClientId` (see `docs/deployment.md`).

## 1. Google Cloud project

1. Create/select a project in <https://console.cloud.google.com/>.
2. Configure the OAuth consent screen (External or Internal as appropriate).
   Add scopes `.../auth/userinfo.email` and `.../auth/userinfo.profile`.

## 2. OAuth client IDs

Create these under **APIs & Services → Credentials → Create credentials →
OAuth client ID**:

| Type | Purpose | Used by |
|---|---|---|
| **Web application** | `serverClientId` for Credential Manager; also the `aud` Supabase validates | Android app (`google.webClientId`) **and** Supabase |
| **Android** | Binds the app signature (package + SHA-1) to the project | Android build |

For the **Android** client:

- Package name: `com.ratig.app`
- SHA-1: from your signing keystore —

  ```bash
  keytool -list -v -keystore <your.keystore> -alias <alias> | grep SHA1
  ```

  For the debug build use `~/.android/debug.keystore` (alias `androiddebugkey`,
  password `android`). Register **both** debug and release SHA-1s if you test
  both.

The **Web client ID** looks like
`1234567890-abcdefghijklmnop.apps.googleusercontent.com` — copy it into
`local.properties`:

```properties
google.webClientId=1234567890-abcdefghijklmnop.apps.googleusercontent.com
```

## 3. Supabase Auth

In the Supabase dashboard → **Authentication → Providers → Google**:

1. Enable Google.
2. Paste the **Web** client ID and its **client secret** (the secret is used by
   Supabase only — it never enters the app).
3. Ensure the **Authorized Client IDs / audience** includes the same Web client
   ID so the ID token audience validates.
4. Set the Site URL / redirect URLs to your scheme if you later add a web
   redirect; the Android ID-token flow does not need a redirect URL.

The `on_auth_user_created` trigger (`supabase/migrations/0002_security.sql`)
creates a `profiles` row for each new Google user with `role='user'` and
`account_status='pending'`. An admin activates the account (Admin → Users, or
the `approve-account` Edge Function). Promoting to `admin` is a manual SQL step
(see `supabase/README.md` §3).

## 4. Verify

1. Build and install the debug APK.
2. Tap **Masuk dengan Google**; pick an account.
3. Expected outcomes:
   - New user → `pending_approval` screen.
   - Active user → dashboard for their role.
   - Misconfigured client ID → the app reports "not configured" / sign-in
     failure without crashing.

Common failures:

| Symptom | Cause |
|---|---|
| `NoCredentialException` / no accounts listed | No Google account on device, or SHA-1 not registered for the Android client |
| Token rejected by Supabase (`invalid audience`) | Web client ID in `local.properties` differs from the one configured in Supabase |
| `AppError.NotConfigured` | `supabase.url` / `supabase.anonKey` / `google.webClientId` blank in `local.properties` |
| Stuck on pending | `profiles.account_status` is `pending`; approve the account |
