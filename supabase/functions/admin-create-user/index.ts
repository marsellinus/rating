// RATIG — Edge Function: admin-create-user
// Creates a new user account (auth.users + profiles) so an administrator can
// onboard people without them going through Google self-signup.
//
// POST {
//   "email": "budi@example.com",
//   "full_name": "Budi Santoso",
//   "role": "user",                  // super_admin | admin | user
//                                    // (only a super_admin may create admin)
//   "password": "optional-8+chars",  // omit to send an invite/reset email
//   "account_status": "active"       // active (default) | pending
// }
// Auth: valid Supabase JWT whose profiles.role = 'admin' (403 otherwise).
// The service-role key is used SERVER-SIDE ONLY.
//
// Behaviour:
//  - If a user with this email already exists, returns 409 (no silent reuse).
//  - With `password`: account is created already confirmed and usable.
//  - Without `password`: account is created unconfirmed and a recovery/invite
//    email is sent so the user sets their own password (preferred).
//  - The `on_auth_user_created` trigger creates the profiles row; this function
//    then sets role / account_status / full_name explicitly.
//  - Every creation is written to `audit_logs`.
//
// Env (injected automatically when deployed; set in .env for local serve):
//   SUPABASE_URL
//   SUPABASE_SERVICE_ROLE_KEY
//
// Deploy: supabase functions deploy admin-create-user

import { createClient, type User } from "https://esm.sh/@supabase/supabase-js@2";

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const ROLES: Record<string, true> = { super_admin: true, admin: true, user: true };
const STATUSES: Record<string, true> = { active: true, pending: true };

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method !== "POST") {
    return json({ error: "Method not allowed. Use POST." }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    return json(
      { error: "Server misconfigured: missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY" },
      500,
    );
  }

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // 1. Validate the caller's JWT.
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) {
    return json({ error: "Missing bearer token" }, 401);
  }

  let caller: User;
  try {
    const { data, error } = await admin.auth.getUser(token);
    if (error || !data?.user) {
      return json({ error: "Invalid or expired token" }, 401);
    }
    caller = data.user;
  } catch {
    return json({ error: "Token validation failed" }, 401);
  }

  // 2. Verify the caller is an active admin.
  const { data: callerProfile, error: profileError } = await admin
    .from("profiles")
    .select("role, account_status")
    .eq("id", caller.id)
    .single();

  if (profileError || !callerProfile) {
    return json({ error: "Caller profile not found" }, 403);
  }
  if (callerProfile.role !== "admin" && callerProfile.role !== "super_admin") {
    return json({ error: "Forbidden: admin role required" }, 403);
  }
  if (callerProfile.account_status !== "active") {
    return json({ error: "Forbidden: caller account is not active" }, 403);
  }

  // 3. Parse and validate the payload.
  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }

  const email = typeof body.email === "string" ? body.email.trim().toLowerCase() : "";
  const fullName = typeof body.full_name === "string" ? body.full_name.trim() : "";
  const role = typeof body.role === "string" ? body.role.trim().toLowerCase() : "worker";
  const accountStatus =
    typeof body.account_status === "string"
      ? body.account_status.trim().toLowerCase()
      : "active";
  const password = typeof body.password === "string" ? body.password : "";

  if (!EMAIL_RE.test(email)) return json({ error: "Email tidak valid" }, 400);
  if (fullName.length < 2) return json({ error: "Nama lengkap wajib diisi" }, 400);
  if (!ROLES[role]) return json({ error: "Role tidak dikenal" }, 400);
  // Only a super_admin may create another admin/super_admin.
  if (role !== "user" && callerProfile.role !== "super_admin") {
    return json({ error: "Forbidden: hanya super admin dapat membuat admin" }, 403);
  }
  if (!STATUSES[accountStatus]) {
    return json({ error: "account_status harus 'active' atau 'pending'" }, 400);
  }
  if (password && password.length < 8) {
    return json({ error: "Kata sandi minimal 8 karakter" }, 400);
  }

  // 4. Reject duplicates explicitly (no silent reuse of an existing account).
  const { data: existing } = await admin
    .from("profiles")
    .select("id")
    .eq("email", email)
    .maybeSingle();
  if (existing) {
    return json({ error: "Email sudah terdaftar" }, 409);
  }

  // 5. Create the auth user. With a password it is usable immediately;
  //    otherwise an invite/recovery email lets the user set their own.
  const { data: created, error: createError } = await admin.auth.admin.createUser({
    email,
    password: password || undefined,
    email_confirm: Boolean(password),
    user_metadata: { full_name: fullName },
  });

  if (createError || !created?.user) {
    const message = createError?.message ?? "Gagal membuat akun";
    return json({ error: message }, 400);
  }

  const newUserId = created.user.id;

  // 6. Set role / status / name on the trigger-created profile row. The
  //    trigger may not have run yet, so upsert to be safe.
  const { error: upsertError } = await admin.from("profiles").upsert(
    {
      id: newUserId,
      email,
      full_name: fullName,
      role,
      account_status: accountStatus,
      updated_at: new Date().toISOString(),
    },
    { onConflict: "id" },
  );

  if (upsertError) {
    // Roll back the auth user so we do not leave a half-created account.
    await admin.auth.admin.deleteUser(newUserId);
    return json({ error: `Gagal menyimpan profil: ${upsertError.message}` }, 400);
  }

  // 7. Without a password, send the invite so the user sets their own.
  let inviteSent = false;
  if (!password) {
    const { error: inviteError } = await admin.auth.admin.inviteUserByEmail(email, {
      data: { full_name: fullName },
    });
    // A failure here is non-fatal: the account exists and can be recovered.
    inviteSent = !inviteError;
  }

  // 8. Audit trail.
  await admin.from("audit_logs").insert({
    actor_id: caller.id,
    action: "create_user",
    entity_type: "profiles",
    entity_id: newUserId,
    metadata: { source: "admin-create-user", role, account_status: accountStatus },
  });

  return json(
    {
      ok: true,
      user_id: newUserId,
      email,
      full_name: fullName,
      role,
      account_status: accountStatus,
      invite_sent: inviteSent,
    },
    201,
  );
});
