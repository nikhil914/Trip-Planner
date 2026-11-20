import { addAttachment, AttachmentError, attachmentErrorResponse, readUpload, tripIdOf } from "@/server/lib/attachments";
import { currentUser } from "@/server/lib/session";

/** Upload from the board. A browser on another site cannot post here: the Origin must be this site. */
export async function POST(request: Request) {
  try {
    const origin = request.headers.get("origin");
    if (!origin || new URL(origin).host !== new URL(request.url).host) throw new AttachmentError(403, "Upload from the trip planner page.");
    const user = await currentUser();
    if (!user) throw new AttachmentError(401, "Sign in to continue.");
    const tripId = await tripIdOf(user.id);
    if (!tripId) throw new AttachmentError(404, "Save the trip first.");
    const { form, file } = await readUpload(request);
    const blockId = form.get("blockId");
    if (typeof blockId !== "string" || !blockId) throw new AttachmentError(400, "Pick a block first.");
    return Response.json(await addAttachment(tripId, blockId, file));
  } catch (e) {
    return attachmentErrorResponse(e);
  }
}
