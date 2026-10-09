// RATIG — Edge Function: approve-account
// Approves a pending user account (profiles.account_status -> 'active').
//
// POST { "user_id": "<uuid>" }
// Auth: valid Supabase JWT whose profiles.role is 'admin' or 'super_admin' (403 otherwise).
// The service-role key is used SERVER-SIDE ONLY to read/write data.
//
// Env (injected automatically when deployed; set in .env for local serve):
//   SUPABASE_URL
//   SUPABASE_SERVICE_ROLE_KEY
//
// Deploy: supabase functions deploy approve-account

import { createClient, type User } from "https://esm.sh/@supabase/supabase-js@2";

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

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
    return json({ error: "Server misconfigured: missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY" }, 500);
  }

  // Admin client (bypasses RLS; server-side only).
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

  // 2. Verify the caller is an active admin (query via service role).
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

  // 3. Parse and validate the payload.
  let userId: unknown;
  try {
    const body = await req.json();
    userId = body?.user_id;
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }
  if (typeof userId !== "string" || !UUID_RE.test(userId)) {
    return json({ error: "user_id must be a UUID" }, 400);
  }

  // 4. Activate the account.
  const { data: updated, error: updateError } = await admin
    .from("profiles")
    .update({ account_status: "active", updated_at: new Date().toISOString() })
    .eq("id", userId)
    .select("id, email, account_status")
    .single();

  if (updateError || !updated) {
    return json({ error: "Profile not found" }, 404);
  }

  // 5. Audit trail (append-only table; service role inserts are allowed).
  await admin.from("audit_logs").insert({
    actor_id: caller.id,
    action: "approve_account",
    entity_type: "profiles",
    entity_id: userId,
    metadata: { source: "approve-account" },
  });

  return json(
    {
      ok: true,
      user_id: updated.id,
      email: updated.email,
      account_status: updated.account_status,
    },
    200,
  );
});
