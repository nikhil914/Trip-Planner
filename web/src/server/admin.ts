"use server";

import { and, count, eq, isNull, ne } from "drizzle-orm";
import { getDb, schema } from "@/db/client";
import { createInvite, INVITE_TTL_MS, listInvites, revokeInvite, type InviteInfo } from "./lib/invites";
import { publicOrigin } from "./lib/origin";
import { requireAdmin } from "./lib/session";

const { users, trips, devices } = schema;

export interface AdminUserRow {
  id: string;
  email: string;
  name: string;
  role: "ADMIN" | "USER";
  disabled: boolean;
  createdAt: number;
  tripTitle: string | null;
  tripRev: number | null;
  deviceCount: number;
}

export async function listUsersAction(): Promise<AdminUserRow[]> {
  await requireAdmin();
  const db = await getDb();
  const rows = await db
    .select({
      id: users.id,
      email: users.email,
      name: users.name,
      role: users.role,
      disabled: users.disabled,
      createdAt: users.createdAt,
      tripTitle: trips.title,
      tripRev: trips.rev,
    })
    .from(users)
    .leftJoin(trips, eq(trips.ownerId, users.id))
    .orderBy(users.createdAt);
  const deviceCounts = await db
    .select({ userId: devices.userId, total: count() })
    .from(devices)
    .where(isNull(devices.revokedAt))
    .groupBy(devices.userId);
  const byUser = new Map(deviceCounts.map((d) => [d.userId, d.total]));
  return rows.map((r) => ({ ...r, deviceCount: byUser.get(r.id) ?? 0 }));
}

async function otherActiveAdmins(userId: string): Promise<number> {
  const db = await getDb();
  const [{ total }] = await db
    .select({ total: count() })
    .from(users)
    .where(and(eq(users.role, "ADMIN"), eq(users.disabled, false), ne(users.id, userId)));
  return total;
}

export async function setUserRoleAction(userId: string, role: "ADMIN" | "USER"): Promise<AdminUserRow[]> {
  await requireAdmin();
  if (role !== "ADMIN" && role !== "USER") throw new Error("Unknown role.");
  if (role === "USER" && (await otherActiveAdmins(userId)) === 0) throw new Error("Keep at least one active admin.");
  const db = await getDb();
  await db.update(users).set({ role }).where(eq(users.id, String(userId)));
  return listUsersAction();
}

export async function setUserDisabledAction(userId: string, disabled: boolean): Promise<AdminUserRow[]> {
  const admin = await requireAdmin();
  if (disabled && userId === admin.id) throw new Error("You cannot disable your own account.");
  if (disabled && (await otherActiveAdmins(userId)) === 0) throw new Error("Keep at least one active admin.");
  const db = await getDb();
  await db.update(users).set({ disabled: Boolean(disabled) }).where(eq(users.id, String(userId)));
  return listUsersAction();
}

export async function listInvitesAction(): Promise<InviteInfo[]> {
  await requireAdmin();
  return listInvites();
}

/** Returns the invite link once, with the updated list. The link works for one sign-up within 7 days. */
export async function createInviteAction(note: string, role: "ADMIN" | "USER"): Promise<{ url: string; days: number; invites: InviteInfo[] }> {
  const admin = await requireAdmin();
  const token = await createInvite(admin.id, String(note ?? ""), role === "ADMIN" ? "ADMIN" : "USER");
  return { url: `${await publicOrigin()}/signup?invite=${token}`, days: INVITE_TTL_MS / 86_400_000, invites: await listInvites() };
}

export async function revokeInviteAction(id: string): Promise<InviteInfo[]> {
  await requireAdmin();
  await revokeInvite(String(id));
  return listInvites();
}
