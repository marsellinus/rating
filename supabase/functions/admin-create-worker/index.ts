// RATIG — Edge Function: admin-create-worker
//
// ONE OR ONE ACCOUNT: creates the worker row AND the login account in a single
// server-side flow, with rollback. The client never sees a service-role key.
//
// POST {
//   "employee_number": "3201234567890001",
//   "full_name": "Budi Santoso",
//   "email": "budi@example.com",
//   "password": "min 8 chars",
//   "department_id": null, "work_area_id": null,
//   "shift_id": null, "job_title": null, "active_status": true
// }
//
// Auth: caller must be an active admin / super_admin (their JWT is verified).
//
// Behaviour:
//  1. Validate all input, including unique employee_number / email / nik.
//  2. Create auth.users via the service role (email already confirmed).
//  3. The DB trigger creates `profiles`; this function sets role=user and
//     account_status=active explicitly (upsert, trigger may be async).
//  4. Insert `workers` with nik = employee_number and user_id = new auth id.
//  5. On ANY failure after step 2, delete the auth user (compensating rollback)
//     so a half-created account never survives.
//  6. Write an audit_logs row.
//
// Env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY (server-side only)
// Deploy: supabase functions deploy admin-create-worker

import { createClient } from "npm:@supabase/supabase-js@2";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const EMP_NO_RE = /^[0-9]{4,32}$/;

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}

/** Human-readable Postgres error mapping, so the app can show a specific message. */
function dbMessage(code: string | undefined, fallback: string): string {
  switch (code) {
    case "23505": return "Data sudah terdaftar (nomor induk atau email duplikat).";
    case "23502": return "Ada data wajib yang belum terisi.";
    case "23503": return "Data departemen, area, atau shift tidak ditemukan.";
    case "42501": return "Anda tidak memiliki izin untuk menambah pekerja.";
    default: return fallback;
  }
}

Deno.serve(async (req: Request): Promise<Response> => {
  if (req.method !== "POST") return json({ error: "Method not allowed. Use POST." }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceRoleKey) {
    return json({ error: "Server belum dikonfigurasi" }, 500);
  }

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // 1. Caller authentication + authorization.
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) return json({ error: "Sesi berakhir. Silakan masuk kembali." }, 401);

  let caller: Awaited<ReturnType<typeof admin.auth.getUser>>['data']['user'];
  try {
    const { data, error } = await admin.auth.getUser(token);
    if (error || !data?.user) return json({ error: "Sesi berakhir. Silakan masuk kembali." }, 401);
    caller = data.user;
  } catch {
    return json({ error: "Gagal memverifikasi sesi" }, 401);
  }

  const { data: callerProfile, error: callerErr } = await admin
    .from("profiles").select("role, account_status").eq("id", caller.id).maybeSingle();
  if (callerErr || !callerProfile) return json({ error: "Profil pengguna tidak ditemukan" }, 403);
  if (callerProfile.role !== "admin" && callerProfile.role !== "super_admin") {
    return json({ error: "Hanya admin yang dapat mendaftarkan pekerja" }, 403);
  }
  if (callerProfile.account_status !== "active") {
    return json({ error: "Akun Anda tidak aktif" }, 403);
  }

  // 2. Parse + validate payload.
  let body: Record<string, unknown>;
  try { body = await req.json(); }
  catch { return json({ error: "Format data tidak valid" }, 400); }

  const s = (k: string) => typeof body[k] === "string" ? (body[k] as string).trim() : "";
  const employeeNumber = s("employee_number");
  const fullName = s("full_name");
  const email = s("email").toLowerCase();
  const password = typeof body.password === "string" ? body.password : "";
  const departmentId = s("department_id") || null;
  const workAreaId = s("work_area_id") || null;
  const shiftId = s("shift_id") || null;
  const jobTitle = s("job_title") || null;
  const activeStatus = body.active_status !== false;

  if (!EMP_NO_RE.test(employeeNumber)) {
    return json({ error: "Nomor Induk Pekerja harus 4–32 digit angka" }, 400);
  }
  if (fullName.length < 2) return json({ error: "Nama lengkap wajib diisi" }, 400);
  if (!EMAIL_RE.test(email)) return json({ error: "Email wajib diisi dan harus valid" }, 400);
  if (password.length < 8) return json({ error: "Kata sandi minimal 8 karakter" }, 400);

  // 3. Duplicate checks BEFORE creating anything.
  const { data: dupWorker } = await admin.from("workers")
    .select("id").or(`employee_number.eq.${employeeNumber},nik.eq.${employeeNumber}`)
    .maybeSingle();
  if (dupWorker) return json({ error: "Nomor induk pekerja sudah terdaftar" }, 409);

  const { data: dupProfile } = await admin.from("profiles")
    .select("id").eq("email", email).maybeSingle();
  if (dupProfile) return json({ error: "Email sudah dipakai akun lain" }, 409);

  // 4. Create auth user. From here on, failures must roll back.
  const { data: created, error: createError } = await admin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { full_name: fullName },
  });
  if (createError || !created?.user) {
    return json({ error: createError?.message ?? "Gagal membuat akun login" }, 400);
  }
  const userId = created.user.id;

  const rollback = async (message: string, code?: string) => {
    await admin.auth.admin.deleteUser(userId);
    return json({ error: dbMessage(code, message) }, 400);
  };

  // 5. Profile row (the trigger should have created it; upsert to be safe).
  const { error: profileErr } = await admin.from("profiles").upsert(
    {
      id: userId,
      email,
      full_name: fullName,
      role: "user",
      account_status: "active",
      updated_at: new Date().toISOString(),
    },
    { onConflict: "id" },
  );
  if (profileErr) {
    return await rollback(`Gagal menyimpan profil: ${profileErr.message}`, profileErr.code);
  }

  // 6. Worker row, linked to that exact account. NIK follows employee_number.
  const { data: workerRow, error: workerErr } = await admin.from("workers").insert({
    employee_number: employeeNumber,
    nik: employeeNumber,
    full_name: fullName,
    email,
    department_id: departmentId,
    work_area_id: workAreaId,
    shift_id: shiftId,
    job_title: jobTitle,
    active_status: activeStatus,
    user_id: userId,
  }).select().maybeSingle();
  if (workerErr || !workerRow) {
    return await rollback(`Gagal menyimpan data pekerja: ${workerErr?.message ?? ""}`, workerErr?.code);
  }

  // 7. Audit.
  await admin.from("audit_logs").insert({
    actor_id: caller.id,
    action: "create_worker_with_account",
    entity_type: "workers",
    entity_id: workerRow.id,
    metadata: { source: "admin-create-worker", user_id: userId, email },
  });

  return json(
    { ok: true, worker: workerRow, user_id: userId, email },
    201,
  );
});
