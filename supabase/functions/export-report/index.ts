// RATIG — Edge Function: export-report
// Exports finalized examination rows from v_report_rows as CSV.
//
// POST { "from": "2026-09-01", "to": "2026-09-30", "department_id"?: "<uuid>", "format"?: "csv" }
// Auth: valid Supabase JWT whose profiles.role is 'admin' or 'super_admin'
// (403 otherwise). Dates are INCLUSIVE, interpreted in Asia/Jakarta (UTC+7,
// fixed offset — no DST). The service-role key is used SERVER-SIDE ONLY.
//
// Env:
//   SUPABASE_URL
//   SUPABASE_SERVICE_ROLE_KEY
//
// Deploy: supabase functions deploy export-report

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

const CSV_COLUMNS = [
  "session_id",
  "worker_id",
  "employee_number",
  "worker_name",
  "department_name",
  "area_name",
  "shift_name",
  "started_at",
  "completed_at",
  "examiner_name",
  "valid_trial_count",
  "invalid_trial_count",
  "missed_response_count",
  "false_start_count",
  "mean_reaction_time_ms",
  "median_reaction_time_ms",
  "min_reaction_time_ms",
  "max_reaction_time_ms",
  "standard_deviation_ms",
  "slow_response_count",
  "excluded_artifact_count",
  "classification_code",
  "classification_label",
  "classification_explanation",
  "rule_version",
  "follow_up_status",
  "app_version",
] as const;

type ReportRow = Record<(typeof CSV_COLUMNS)[number], unknown>;

function csvEscape(value: unknown): string {
  if (value === null || value === undefined) return "";
  const s = String(value);
  return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
}

function toCsv(rows: ReportRow[]): string {
  const lines = [CSV_COLUMNS.join(",")];
  for (const row of rows) {
    lines.push(CSV_COLUMNS.map((c) => csvEscape(row[c])).join(","));
  }
  return lines.join("\r\n") + "\r\n";
}

function nextDay(dateStr: string): string {
  const [y, m, d] = dateStr.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d) + 86_400_000).toISOString().slice(0, 10);
}

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

  const admin = createClient(supabaseUrl, serviceRoleKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  // 1. Validate the caller's JWT and role (admin/management only).
  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) {
    return json({ error: "Missing bearer token" }, 401);
  }

  let callerId: string;
  try {
    const { data, error } = await admin.auth.getUser(token);
    if (error || !data?.user) {
      return json({ error: "Invalid or expired token" }, 401);
    }
    callerId = data.user.id;
  } catch {
    return json({ error: "Token validation failed" }, 401);
  }

  const { data: callerProfile, error: profileError } = await admin
    .from("profiles")
    .select("role, account_status")
    .eq("id", callerId)
    .single();

  if (profileError || !callerProfile) {
    return json({ error: "Caller profile not found" }, 403);
  }
  if (callerProfile.role !== "admin" && callerProfile.role !== "super_admin") {
    return json({ error: "Forbidden: admin role required" }, 403);
  }

  // 2. Parse and validate the payload.
  let from: unknown, to: unknown, departmentId: unknown, format: unknown;
  try {
    const body = await req.json();
    from = body?.from;
    to = body?.to;
    departmentId = body?.department_id;
    format = body?.format ?? "csv";
  } catch {
    return json({ error: "Invalid JSON body" }, 400);
  }

  if (typeof from !== "string" || typeof to !== "string" || !DATE_RE.test(from) || !DATE_RE.test(to)) {
    return json({ error: "from and to must be dates formatted YYYY-MM-DD" }, 400);
  }
  if (to < from) {
    return json({ error: "to must be on or after from" }, 400);
  }
  if (format !== "csv") {
    return json({ error: "Only format 'csv' is supported" }, 400);
  }
  const departmentFilter =
    typeof departmentId === "string" && departmentId.length > 0 ? departmentId : null;
  if (departmentFilter !== null && !UUID_RE.test(departmentFilter)) {
    return json({ error: "department_id must be a UUID" }, 400);
  }

  // 3. Query v_report_rows (service role honors role via the check above).
  //    Asia/Jakarta is a fixed UTC+7 offset, so inclusive [from, to] bounds
  //    translate exactly to [from T00:00+07, to+1d T00:00+07).
  let query = admin
    .from("v_report_rows")
    .select(CSV_COLUMNS.join(","))
    .gte("completed_at", `${from}T00:00:00+07:00`)
    .lt("completed_at", `${nextDay(to)}T00:00:00+07:00`)
    .order("completed_at", { ascending: false });

  if (departmentFilter !== null) {
    query = query.eq("department_id", departmentFilter);
  }

  const { data: rows, error: queryError } = await query;
  if (queryError) {
    return json({ error: `Query failed: ${queryError.message}` }, 500);
  }

  // 4. Stream CSV attachment.
  const csv = toCsv((rows ?? []) as ReportRow[]);
  return new Response(csv, {
    status: 200,
    headers: {
      "Content-Type": "text/csv; charset=utf-8",
      "Content-Disposition": `attachment; filename="ratig-report-${from}_${to}.csv"`,
      "Cache-Control": "no-store",
    },
  });
});
