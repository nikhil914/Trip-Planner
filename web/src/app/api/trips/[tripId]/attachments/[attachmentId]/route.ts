import { fileResponse, readAttachment, tripIdOf } from "@/server/lib/attachments";
import { currentUser } from "@/server/lib/session";
import { authenticateDevice, SyncError } from "@/server/lib/sync-core";

// A paired phone downloads with its sync token. The trip's owner can also open the file in the browser.
export async function GET(request: Request, { params }: RouteContext<"/api/trips/[tripId]/attachments/[attachmentId]">) {
  const { tripId, attachmentId } = await params;
  const authorization = request.headers.get("authorization");
  try {
    if (authorization) {
      await authenticateDevice(authorization, tripId);
    } else {
      const user = await currentUser();
      if (!user || (await tripIdOf(user.id)) !== tripId) return Response.json({ error: "not_found" }, { status: 404 });
    }
  } catch (e) {
    if (e instanceof SyncError) return Response.json({ error: e.code }, { status: e.status });
    throw e;
  }
  const file = await readAttachment(tripId, attachmentId);
  if (!file) return Response.json({ error: "not_found" }, { status: 404 });
  return fileResponse(file);
}
