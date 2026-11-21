import "server-only";
import { desc, eq, lt } from "drizzle-orm";
import { headers } from "next/headers";
import { getDb, schema } from "@/db/client";

const { auditEvents, users } = schema;
const KEEP_MS = 180 * 24 * 60 * 60_000;

export type AuditKind =
  | "login"
  | "login_failed"
  | "login_blocked"
  | "signup"
  | "signout_everywhere"
  | "totp_enabled"
  | "totp_disabled"
  | "phone_paired"
  | "connector_created"
  | "connector_revoked"
  | "invite_created";

const LABEL: Record<AuditKind, string> = {
  login: "Signed in",
  login_failed: "Wrong password or code",
  login_blocked: "Sign-in blocked after too many attempts",
  signup: "Account created",
  signout_everywhere: "Signed out everywhere",
  totp_enabled: "Two-factor sign-in turned on",
  totp_disabled: "Two-factor sign-in turned off",
  phone_paired: "Phone paired",
  connector_created: "Connector link created",
  connector_revoked: "Connector link revoked",
  invite_created: "Invite link created",
};

// These are worth a message to the owner. Failed attempts are logged but would be noise one by one.
const ALERT: ReadonlySet<AuditKind> = new Set(["login", "login_blocked", "signup", "signout_everywhere", "totp_enabled", "totp_disabled", "phone_paired", "connector_created", "invite_created"]);

export interface AuditRow {
  id: number;
  kind: string;
  label: string;
  detail: string | null;
  ip: string | null;
  at: number;
  userEmail?: string | null;
}

/** The caller's IP inside a server action or page. */
export async function actionIp(): Promise<string | null> {
  try {
    const h = await headers();
    return h.get("x-real-ip") || h.get("x-forwarded-for")?.split(",")[0]?.trim() || null;
  } catch {
    return null;
  }
}

/**
 * Telegram alert, when TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID are set. It carries the event and IP only,
 * never trip details. A failure to send never blocks the action that caused it.
 */
async function alert(kind: AuditKind, detail: string | null, ip: string | null): Promise<void> {
  const token = process.env.TELEGRAM_BOT_TOKEN?.trim();
  const chat = process.env.TELEGRAM_CHAT_ID?.trim();
  if (!token || !chat || !ALERT.has(kind)) return;
  const text = `Trip Planner: ${LABEL[kind]}${detail ? ` (${detail})` : ""}${ip ? `\nIP ${ip}` : ""}\n${new Date().toISOString()}`;
  try {
    await fetch(`https://api.telegram.org/bot${token}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: chat, text, disable_web_page_preview: true }),
      signal: AbortSignal.timeout(3000),
    });
  } catch (e) {
    console.warn("alert not sent", (e as Error).message);
  }
}

export async function audit(kind: AuditKind, userId: string | null, opts: { detail?: string | null; ip?: string | null } = {}): Promise<void> {
  const detail = opts.detail?.slice(0, 200) ?? null;
  const ip = opts.ip === undefined ? await actionIp() : opts.ip;
  try {
    const db = await getDb();
    const now = Date.now();
    await db.insert(auditEvents).values({ userId, kind, detail, ip, at: now });
    if (Math.random() < 0.02) await db.delete(auditEvents).where(lt(auditEvents.at, now - KEEP_MS));
  } catch (e) {
    console.error("audit write failed", e);
  }
  await alert(kind, detail, ip);
}

const toRow = (r: typeof auditEvents.$inferSelect, userEmail?: string | null): AuditRow => ({
  id: r.id,
  kind: r.kind,
  label: LABEL[r.kind as AuditKind] ?? r.kind,
  detail: r.detail,
  ip: r.ip,
  at: r.at,
  ...(userEmail !== undefined && { userEmail }),
});

export async function listAuditForUser(userId: string, limit = 50): Promise<AuditRow[]> {
  const db = await getDb();
  const rows = await db.select().from(auditEvents).where(eq(auditEvents.userId, userId)).orderBy(desc(auditEvents.at)).limit(limit);
  return rows.map((r) => toRow(r));
}

export async function listAuditAll(limit = 100): Promise<AuditRow[]> {
  const db = await getDb();
  const rows = await db
    .select({ event: auditEvents, email: users.email })
    .from(auditEvents)
    .leftJoin(users, eq(users.id, auditEvents.userId))
    .orderBy(desc(auditEvents.at))
    .limit(limit);
  return rows.map((r) => toRow(r.event, r.email));
}
