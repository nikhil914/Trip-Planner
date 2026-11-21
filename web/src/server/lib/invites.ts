import "server-only";
import { createHash, randomBytes } from "node:crypto";
import { and, count, desc, eq, gt, isNull, like, lt } from "drizzle-orm";
import { getDb, schema } from "@/db/client";

const { invites, users } = schema;

const sha256 = (value: string) => createHash("sha256").update(value).digest("hex");
export const INVITE_TTL_MS = 7 * 24 * 60 * 60_000;
const MAX_OPEN_INVITES = 20;

export interface InviteInfo {
  id: string;
  note: string;
  role: "ADMIN" | "USER";
  createdAt: number;
  expiresAt: number;
}

/** Open invites only: unused and not expired. The id is a prefix of the hash, enough to revoke one. */
export async function listInvites(): Promise<InviteInfo[]> {
  const db = await getDb();
  const rows = await db
    .select()
    .from(invites)
    .where(and(isNull(invites.usedAt), gt(invites.expiresAt, Date.now())))
    .orderBy(desc(invites.createdAt));
  return rows.map((r) => ({ id: r.tokenHash.slice(0, 16), note: r.note, role: r.role, createdAt: r.createdAt, expiresAt: r.expiresAt }));
}

/** Returns the token once. Only its hash is stored, so the link cannot be shown again. */
export async function createInvite(createdBy: string, note: string, role: "ADMIN" | "USER"): Promise<string> {
  const db = await getDb();
  const now = Date.now();
  await db.delete(invites).where(and(isNull(invites.usedAt), lt(invites.expiresAt, now)));
  if ((await listInvites()).length >= MAX_OPEN_INVITES) throw new Error(`At most ${MAX_OPEN_INVITES} open invites. Revoke one first.`);
  const token = "inv_" + randomBytes(24).toString("base64url");
  await db.insert(invites).values({
    tokenHash: sha256(token),
    createdBy,
    note: note.trim().slice(0, 80) || "Invite",
    role: role === "ADMIN" ? "ADMIN" : "USER",
    createdAt: now,
    expiresAt: now + INVITE_TTL_MS,
  });
  return token;
}

export async function revokeInvite(id: string): Promise<void> {
  if (!/^[0-9a-f]{16}$/.test(id)) return;
  const db = await getDb();
  await db.delete(invites).where(and(like(invites.tokenHash, id + "%"), isNull(invites.usedAt)));
}

/** The role an invite grants, or null when the token is wrong, used or expired. */
export async function checkInvite(token: string | null | undefined): Promise<"ADMIN" | "USER" | null> {
  if (!token) return null;
  const db = await getDb();
  const row = await db.query.invites.findFirst({ where: eq(invites.tokenHash, sha256(token)) });
  if (!row || row.usedAt !== null || row.expiresAt < Date.now()) return null;
  return row.role;
}

/** Marks the invite used. Only one caller wins, so a link cannot create two accounts. */
export async function claimInvite(token: string): Promise<"ADMIN" | "USER" | null> {
  const db = await getDb();
  const now = Date.now();
  const claimed = await db
    .update(invites)
    .set({ usedAt: now })
    .where(and(eq(invites.tokenHash, sha256(token)), isNull(invites.usedAt), gt(invites.expiresAt, now)))
    .returning({ role: invites.role });
  return claimed[0]?.role ?? null;
}

export async function finishInvite(token: string, userId: string): Promise<void> {
  const db = await getDb();
  await db.update(invites).set({ usedBy: userId }).where(eq(invites.tokenHash, sha256(token)));
}

/** Gives a claimed invite back when the account could not be created. */
export async function releaseInvite(token: string): Promise<void> {
  const db = await getDb();
  await db.update(invites).set({ usedAt: null }).where(and(eq(invites.tokenHash, sha256(token)), isNull(invites.usedBy)));
}

/**
 * Who may sign up without an invite: nobody, once the site has an account.
 * The very first account is the admin. ADMIN_EMAIL, when set, may also claim admin while no admin exists.
 */
export async function openSignupRole(email: string | null): Promise<"ADMIN" | null> {
  const db = await getDb();
  const [{ total }] = await db.select({ total: count() }).from(users);
  const adminEmail = process.env.ADMIN_EMAIL?.trim().toLowerCase();
  if (total === 0) return !adminEmail || !email || email === adminEmail ? "ADMIN" : null;
  if (!adminEmail || email !== adminEmail) return null;
  const [{ admins }] = await db.select({ admins: count() }).from(users).where(eq(users.role, "ADMIN"));
  return admins === 0 ? "ADMIN" : null;
}

/** True while someone may still create an account without an invite (see openSignupRole). */
export async function signupOpenWithoutInvite(): Promise<boolean> {
  const db = await getDb();
  const [{ total }] = await db.select({ total: count() }).from(users);
  if (total === 0) return true;
  if (!process.env.ADMIN_EMAIL?.trim()) return false;
  const [{ admins }] = await db.select({ admins: count() }).from(users).where(eq(users.role, "ADMIN"));
  return admins === 0;
}
