import "server-only";
import { and, count, eq, gt, lt } from "drizzle-orm";
import { getDb, schema } from "@/db/client";

const { loginFailures } = schema;

// Counted per IP address only, so a stranger guessing at your email cannot lock you out from your own network.
const WINDOW_MS = 15 * 60_000;
const PER_IP = 10;

const ipKey = (ip: string) => "ip:" + ip;

export function requestIp(request: Request | undefined): string {
  const h = request?.headers;
  return h?.get("x-forwarded-for")?.split(",")[0]?.trim() || h?.get("x-real-ip") || "unknown";
}

/** True when this address has failed to sign in too often in the last 15 minutes. */
export async function isLoginLimited(ip: string): Promise<boolean> {
  const db = await getDb();
  const [{ total }] = await db
    .select({ total: count() })
    .from(loginFailures)
    .where(and(eq(loginFailures.key, ipKey(ip)), gt(loginFailures.at, Date.now() - WINDOW_MS)));
  return total >= PER_IP;
}

export async function recordLoginFailure(ip: string): Promise<void> {
  const db = await getDb();
  const now = Date.now();
  await db.delete(loginFailures).where(lt(loginFailures.at, now - WINDOW_MS));
  await db.insert(loginFailures).values({ key: ipKey(ip), at: now });
}

export async function clearLoginFailures(ip: string): Promise<void> {
  const db = await getDb();
  await db.delete(loginFailures).where(eq(loginFailures.key, ipKey(ip)));
}
