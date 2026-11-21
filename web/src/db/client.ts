import "server-only";
import path from "node:path";
import { createClient } from "@libsql/client";
import { drizzle, type LibSQLDatabase } from "drizzle-orm/libsql";
import { migrate } from "drizzle-orm/libsql/migrator";
import { encryptExistingRows } from "./encrypt-existing";
import { purgeEndedTrips } from "./retention";
import * as schema from "./schema";

export type Db = LibSQLDatabase<typeof schema>;

// Kept on globalThis so a dev-server reload reuses the connection and the finished migration.
const holder = globalThis as unknown as { __tripDb?: Promise<Db> };

async function open(): Promise<Db> {
  const url = process.env.TURSO_DATABASE_URL || "file:local.db";
  const client = createClient({ url, authToken: process.env.TURSO_AUTH_TOKEN || undefined });
  const db = drizzle(client, { schema });
  if (url.startsWith("file:")) await client.execute("PRAGMA foreign_keys = ON");
  await migrate(db, { migrationsFolder: path.join(process.cwd(), "drizzle") });
  if (!process.env.DATA_ENCRYPTION_KEY && process.env.VERCEL_ENV === "production") {
    console.warn("DATA_ENCRYPTION_KEY is not set: personal data is stored unencrypted.");
  }
  await encryptExistingRows(client);
  await purgeEndedTrips(client);
  return db;
}

export function getDb(): Promise<Db> {
  if (!holder.__tripDb) {
    holder.__tripDb = open().catch((e) => {
      holder.__tripDb = undefined;
      throw e;
    });
  }
  return holder.__tripDb;
}

export { schema };
