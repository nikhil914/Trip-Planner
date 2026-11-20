import { authenticateDevice, push } from "@/server/lib/sync-core";
import { answer, readJson } from "@/server/lib/sync-http";

export async function POST(request: Request, { params }: RouteContext<"/api/trips/[tripId]/sync/push">) {
  return answer(async () => {
    const { tripId } = await params;
    await authenticateDevice(request.headers.get("authorization"), tripId);
    return push(tripId, await readJson(request));
  });
}
