import "server-only";
import { createHash, randomBytes } from "node:crypto";
import { and, eq, isNull } from "drizzle-orm";
import { getDb, schema } from "@/db/client";

const { connectorKeys, users } = schema;

const sha256 = (value: string) => createHash("sha256").update(value).digest("hex");
const MAX_ACTIVE_KEYS = 5;

export interface ConnectorKeyInfo {
  id: string;
  name: string;
  createdAt: number;
  lastUsedAt: number | null;
}

export async function listConnectorKeys(userId: string): Promise<ConnectorKeyInfo[]> {
  const db = await getDb();
  return db
    .select({ id: connectorKeys.id, name: connectorKeys.name, createdAt: connectorKeys.createdAt, lastUsedAt: connectorKeys.lastUsedAt })
    .from(connectorKeys)
    .where(and(eq(connectorKeys.userId, userId), isNull(connectorKeys.revokedAt)))
    .orderBy(connectorKeys.createdAt);
}

/** Returns the secret once. Only its hash is stored, so it cannot be shown again. */
export async function createConnectorKey(userId: string, name: string): Promise<{ id: string; key: string }> {
  const db = await getDb();
  const active = await listConnectorKeys(userId);
  if (active.length >= MAX_ACTIVE_KEYS) throw new Error(`You can have at most ${MAX_ACTIVE_KEYS} connector links. Revoke one first.`);
  const key = "tpk_" + randomBytes(32).toString("base64url");
  const id = "ck-" + crypto.randomUUID();
  await db.insert(connectorKeys).values({ id, userId, name: name.trim().slice(0, 60) || "Connector", keyHash: sha256(key), createdAt: Date.now() });
  return { id, key };
}

export async function revokeConnectorKey(userId: string, id: string): Promise<void> {
  const db = await getDb();
  await db.update(connectorKeys).set({ revokedAt: Date.now() }).where(and(eq(connectorKeys.id, id), eq(connectorKeys.userId, userId)));
}

/** The user a connector link belongs to, or null when the link is unknown, revoked, or its account is disabled. */
export async function authenticateConnectorKey(key: string): Promise<{ id: string; name: string } | null> {
  if (!/^tpk_[A-Za-z0-9_-]{40,60}$/.test(key)) return null;
  const db = await getDb();
  const [row] = await db
    .select({ keyId: connectorKeys.id, revokedAt: connectorKeys.revokedAt, lastUsedAt: connectorKeys.lastUsedAt, userId: users.id, name: users.name, disabled: users.disabled })
    .from(connectorKeys)
    .innerJoin(users, eq(users.id, connectorKeys.userId))
    .where(eq(connectorKeys.keyHash, sha256(key)));
  if (!row || row.revokedAt !== null || row.disabled) return null;
  const now = Date.now();
  if (!row.lastUsedAt || now - row.lastUsedAt > 60_000) {
    await db.update(connectorKeys).set({ lastUsedAt: now }).where(eq(connectorKeys.id, row.keyId));
  }
  return { id: row.userId, name: row.name };
}
