import { authenticateDevice, pull } from "@/server/lib/sync-core";
import { answer } from "@/server/lib/sync-http";

export async function GET(request: Request, { params }: RouteContext<"/api/trips/[tripId]/sync">) {
  return answer(async () => {
    const { tripId } = await params;
    await authenticateDevice(request.headers.get("authorization"), tripId);
    const since = Number(new URL(request.url).searchParams.get("since") ?? 0);
    return pull(tripId, Number.isFinite(since) ? Math.floor(since) : 0);
  });
}
