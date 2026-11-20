import "server-only";
import { SyncError } from "./sync-core";

export function clientIp(request: Request): string {
  return request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || request.headers.get("x-real-ip") || "unknown";
}

export async function readJson(request: Request): Promise<unknown> {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

/** Runs a sync handler and turns a SyncError into the JSON error body the phone expects. */
export async function answer(run: () => Promise<unknown>): Promise<Response> {
  try {
    return Response.json(await run(), { headers: { "Cache-Control": "no-store" } });
  } catch (e) {
    if (e instanceof SyncError) {
      const headers: Record<string, string> = { "Cache-Control": "no-store" };
      if (e.retryAfterSeconds) headers["Retry-After"] = String(e.retryAfterSeconds);
      return Response.json({ error: e.code }, { status: e.status, headers });
    }
    console.error("sync request failed", e);
    return Response.json({ error: "server_error" }, { status: 500 });
  }
}
