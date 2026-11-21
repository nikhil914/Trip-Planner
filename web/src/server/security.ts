"use server";

import { eq, sql } from "drizzle-orm";
import QRCode from "qrcode";
import { signOut } from "@/auth";
import { getDb, schema } from "@/db/client";
import { audit, listAuditForUser, type AuditRow } from "./lib/audit";
import { requireUser } from "./lib/session";
import { newTotpSecret, totpUri, verifyTotp } from "./lib/totp";

const { users } = schema;

export interface SecurityState {
  totpEnabled: boolean;
  events: AuditRow[];
}

export async function securityStateAction(): Promise<SecurityState> {
  const user = await requireUser();
  const db = await getDb();
  const row = await db.query.users.findFirst({ where: eq(users.id, user.id), columns: { totpEnabledAt: true } });
  return { totpEnabled: Boolean(row?.totpEnabledAt), events: await listAuditForUser(user.id) };
}

/** Makes a new secret and returns it with a QR code. It only counts after confirmTotpAction accepts a code from it. */
export async function startTotpSetupAction(): Promise<{ secret: string; qrDataUrl: string }> {
  const user = await requireUser();
  const db = await getDb();
  const row = await db.query.users.findFirst({ where: eq(users.id, user.id), columns: { totpEnabledAt: true } });
  if (row?.totpEnabledAt) throw new Error("Two-factor sign-in is already on.");
  const secret = newTotpSecret();
  await db.update(users).set({ totpSecret: secret, totpEnabledAt: null, totpLastStep: null }).where(eq(users.id, user.id));
  const svg = await QRCode.toString(totpUri(secret, user.email), { type: "svg", margin: 1, errorCorrectionLevel: "M" });
  return { secret, qrDataUrl: "data:image/svg+xml;base64," + Buffer.from(svg).toString("base64") };
}

export async function confirmTotpAction(code: string): Promise<SecurityState> {
  const user = await requireUser();
  const db = await getDb();
  const row = await db.query.users.findFirst({ where: eq(users.id, user.id), columns: { totpSecret: true, totpEnabledAt: true } });
  if (!row?.totpSecret || row.totpEnabledAt) throw new Error("Start the setup again.");
  const step = verifyTotp(row.totpSecret, String(code ?? ""));
  if (step === null) throw new Error("That code is wrong. Check the time on your phone and try the next code.");
  await db.update(users).set({ totpEnabledAt: Date.now(), totpLastStep: step }).where(eq(users.id, user.id));
  await audit("totp_enabled", user.id);
  return securityStateAction();
}

export async function disableTotpAction(code: string): Promise<SecurityState> {
  const user = await requireUser();
  const db = await getDb();
  const row = await db.query.users.findFirst({ where: eq(users.id, user.id), columns: { totpSecret: true, totpEnabledAt: true, totpLastStep: true } });
  if (!row?.totpSecret || !row.totpEnabledAt) return securityStateAction();
  const step = verifyTotp(row.totpSecret, String(code ?? ""));
  if (step === null || (row.totpLastStep !== null && step <= row.totpLastStep)) throw new Error("That code is wrong or was already used.");
  await db.update(users).set({ totpSecret: null, totpEnabledAt: null, totpLastStep: null }).where(eq(users.id, user.id));
  await audit("totp_disabled", user.id);
  return securityStateAction();
}

/** Ends every browser session of this account, this one included. Paired phones and connector links are not touched. */
export async function signOutEverywhereAction(): Promise<void> {
  const user = await requireUser();
  const db = await getDb();
  await db.update(users).set({ sessionVersion: sql`${users.sessionVersion} + 1` }).where(eq(users.id, user.id));
  await audit("signout_everywhere", user.id);
  await signOut({ redirectTo: "/login" });
}
