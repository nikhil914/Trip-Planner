import "server-only";
import { headers } from "next/headers";

/** The site's public address, for links shown to the user. PUBLIC_URL wins over the request's host. */
export async function publicOrigin(): Promise<string> {
  const configured = process.env.PUBLIC_URL?.replace(/\/+$/, "");
  if (configured) return configured;
  const h = await headers();
  const host = h.get("x-forwarded-host") ?? h.get("host");
  const proto = h.get("x-forwarded-proto") ?? (host?.startsWith("localhost") || host?.startsWith("127.") ? "http" : "https");
  return `${proto}://${host}`;
}
