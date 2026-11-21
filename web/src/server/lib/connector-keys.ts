import "server-only";
import { createHash, randomBytes } from "node:crypto";
import { and, count, eq, gt, isNull, lt } from "drizzle-orm";
import { getDb, schema } from "@/db/client";

const { connectorKeys, loginFailures, users } = schema;

const sha256 = (value: string) => createHash("sha256").update(value).digest("hex");
const MAX_ACTIVE_KEYS = 5;
export const CONNECTOR_KEY_TTL_MS = 90 * 24 * 60 * 60_000;
// Calls allowed per connector link in a 10-minute window. A busy chat makes a few dozen.
const CALLS_PER_WINDOW = 300;
const CALL_WINDOW_MS = 10 * 60_000;

const expiryOf = (row: { createdAt: number; expiresAt: number | null }) => row.expiresAt ?? row.createdAt + CONNECTOR_KEY_TTL_MS;

export interface ConnectorKeyInfo {
  id: string;
  name: string;
  createdAt: number;
  lastUsedAt: number | null;
  expiresAt: number;
}

export async function listConnectorKeys(userId: string): Promise<ConnectorKeyInfo[]> {
  const db = await getDb();
  const rows = await db
    .select({ id: connectorKeys.id, name: connectorKeys.name, createdAt: connectorKeys.createdAt, lastUsedAt: connectorKeys.lastUsedAt, expiresAt: connectorKeys.expiresAt })
    .from(connectorKeys)
    .where(and(eq(connectorKeys.userId, userId), isNull(connectorKeys.revokedAt)))
    .orderBy(connectorKeys.createdAt);
  const now = Date.now();
  return rows.map((r) => ({ ...r, expiresAt: expiryOf(r) })).filter((r) => r.expiresAt > now);
}

/** Returns the secret once. Only its hash is stored, so it cannot be shown again. */
export async function createConnectorKey(userId: string, name: string): Promise<{ id: string; key: string }> {
  const db = await getDb();
  const active = await listConnectorKeys(userId);
  if (active.length >= MAX_ACTIVE_KEYS) throw new Error(`You can have at most ${MAX_ACTIVE_KEYS} connector links. Revoke one first.`);
  const key = "tpk_" + randomBytes(32).toString("base64url");
  const id = "ck-" + crypto.randomUUID();
  await db.insert(connectorKeys).values({ id, userId, name: name.trim().slice(0, 60) || "Connector", keyHash: sha256(key), createdAt: Date.now(), expiresAt: Date.now() + CONNECTOR_KEY_TTL_MS });
  return { id, key };
}

export async function revokeConnectorKey(userId: string, id: string): Promise<void> {
  const db = await getDb();
  await db.update(connectorKeys).set({ revokedAt: Date.now() }).where(and(eq(connectorKeys.id, id), eq(connectorKeys.userId, userId)));
}

/** The user a connector link belongs to, or null when the link is unknown, revoked, or its account is disabled. */
export async function authenticateConnectorKey(key: string): Promise<{ id: string; name: string; keyId: string } | null> {
  if (!/^tpk_[A-Za-z0-9_-]{40,60}$/.test(key)) return null;
  const db = await getDb();
  const [row] = await db
    .select({
      keyId: connectorKeys.id,
      revokedAt: connectorKeys.revokedAt,
      lastUsedAt: connectorKeys.lastUsedAt,
      createdAt: connectorKeys.createdAt,
      expiresAt: connectorKeys.expiresAt,
      userId: users.id,
      name: users.name,
      disabled: users.disabled,
    })
    .from(connectorKeys)
    .innerJoin(users, eq(users.id, connectorKeys.userId))
    .where(eq(connectorKeys.keyHash, sha256(key)));
  if (!row || row.revokedAt !== null || row.disabled) return null;
  const now = Date.now();
  if (expiryOf(row) <= now) return null;
  if (!row.lastUsedAt || now - row.lastUsedAt > 60_000) {
    await db.update(connectorKeys).set({ lastUsedAt: now }).where(eq(connectorKeys.id, row.keyId));
  }
  return { id: row.userId, name: row.name, keyId: row.keyId };
}

/** Counts this call and says whether the link is over its limit. Uses the same table as the sign-in limit. */
export async function connectorOverLimit(keyId: string): Promise<boolean> {
  const db = await getDb();
  const now = Date.now();
  const key = "mcp:" + keyId;
  const [{ total }] = await db
    .select({ total: count() })
    .from(loginFailures)
    .where(and(eq(loginFailures.key, key), gt(loginFailures.at, now - CALL_WINDOW_MS)));
  if (total >= CALLS_PER_WINDOW) return true;
  await db.insert(loginFailures).values({ key, at: now });
  if (Math.random() < 0.05) await db.delete(loginFailures).where(and(eq(loginFailures.key, key), lt(loginFailures.at, now - CALL_WINDOW_MS)));
  return false;
}
