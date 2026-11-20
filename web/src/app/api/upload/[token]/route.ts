import { addAttachment, AttachmentError, attachmentErrorResponse, readUpload, resolveUploadLink } from "@/server/lib/attachments";

/** Upload through a link the owner or the connector created. The link is the permission, so no sign-in. */
export async function POST(request: Request, { params }: RouteContext<"/api/upload/[token]">) {
  try {
    const { token } = await params;
    const target = await resolveUploadLink(token);
    if (!target) throw new AttachmentError(410, "This upload link has expired. Ask for a new one.");
    const { file } = await readUpload(request);
    return Response.json(await addAttachment(target.tripId, target.blockId, file));
  } catch (e) {
    return attachmentErrorResponse(e);
  }
}
