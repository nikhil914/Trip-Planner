import { NextResponse, type NextRequest } from "next/server";
import { isAllowed, parseAllowlist } from "@/lib/ip-allowlist";

// VPN gate. When ALLOWED_IPS is set, only those addresses reach the site: pages, sign-in, phone sync and upload links.
// The MCP connector is the one exception, because the assistant calls it from its own servers. Its secret URL protects it.
// On Vercel, x-real-ip and x-forwarded-for are set by the platform, so a client cannot fake them.
const OPEN_PREFIXES = ["/api/mcp/"];

const allowlist = parseAllowlist(process.env.ALLOWED_IPS);

function clientIp(request: NextRequest): string | null {
  return request.headers.get("x-real-ip") || request.headers.get("x-forwarded-for")?.split(",")[0]?.trim() || null;
}

export function proxy(request: NextRequest) {
  if (!allowlist) return NextResponse.next();
  const path = request.nextUrl.pathname;
  if (OPEN_PREFIXES.some((p) => path.startsWith(p))) return NextResponse.next();
  if (isAllowed(allowlist, clientIp(request))) return NextResponse.next();
  // A plain 404 says nothing about what is behind it.
  return new NextResponse("Not found", {
    status: 404,
    headers: { "Content-Type": "text/plain", "Cache-Control": "no-store", "X-Robots-Tag": "noindex, nofollow" },
  });
}
