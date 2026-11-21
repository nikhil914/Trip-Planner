import "server-only";
import { createHash, randomBytes } from "node:crypto";
import { and, count, eq, lt } from "drizzle-orm";
import { getDb, schema } from "@/db/client";
import { ENTITY } from "@/db/schema";

const { trips, timelineBlocks, attachments, tombstones, uploadLinks } = schema;

/** Vercel rejects request bodies over 4.5 MB, so one file stays under that with room for the form fields. */
export const MAX_ATTACHMENT_BYTES = 4 * 1024 * 1024;
export const MAX_ATTACHMENTS_PER_BLOCK = 10;
export const UPLOAD_LINK_TTL_MS = 60 * 60_000;

const sha256 = (value: string | Uint8Array) => createHash("sha256").update(value).digest("hex");

export class AttachmentError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export interface AttachmentMeta {
  id: string;
  blockId: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  createdAt: number;
}

/** The type comes from the file's first bytes, not from what the browser claims. The phone can show these four. */
export function sniffType(bytes: Uint8Array): string | null {
  const starts = (sig: number[], at = 0) => sig.every((b, i) => bytes[at + i] === b);
  if (starts([0x25, 0x50, 0x44, 0x46, 0x2d])) return "application/pdf";
  if (starts([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])) return "image/png";
  if (starts([0xff, 0xd8, 0xff])) return "image/jpeg";
  if (starts([0x52, 0x49, 0x46, 0x46]) && starts([0x57, 0x45, 0x42, 0x50], 8)) return "image/webp";
  return null;
}

const EXTENSION: Record<string, string> = { "application/pdf": ".pdf", "image/png": ".png", "image/jpeg": ".jpg", "image/webp": ".webp" };

function cleanName(name: string, mimeType: string): string {
  let base = name.replace(/[\u0000-\u001f\u007f/\\]/g, "").trim().slice(0, 120);
  if (!base) base = "ticket";
  const ext = EXTENSION[mimeType];
  return base.toLowerCase().endsWith(ext) || (ext === ".jpg" && /\.jpe?g$/i.test(base)) ? base : base + ext;
}

export async function tripIdOf(userId: string): Promise<string | null> {
  const db = await getDb();
  const trip = await db.query.trips.findFirst({ where: eq(trips.ownerId, userId), columns: { id: true } });
  return trip?.id ?? null;
}

const metaColumns = {
  id: attachments.id,
  blockId: attachments.blockId,
  fileName: attachments.fileName,
  mimeType: attachments.mimeType,
  sizeBytes: attachments.sizeBytes,
  createdAt: attachments.createdAt,
};

export async function listAttachments(tripId: string): Promise<AttachmentMeta[]> {
  const db = await getDb();
  return db.select(metaColumns).from(attachments).where(eq(attachments.tripId, tripId)).orderBy(attachments.createdAt);
}

/** Stores a file on a block and bumps the trip revision so paired phones download it on their next pull. */
export async function addAttachment(tripId: string, blockId: string, file: { name: string; bytes: Uint8Array }): Promise<AttachmentMeta> {
  if (file.bytes.byteLength === 0) throw new AttachmentError(400, "The file is empty.");
  if (file.bytes.byteLength > MAX_ATTACHMENT_BYTES) throw new AttachmentError(413, "Files can be at most 4 MB.");
  const mimeType = sniffType(file.bytes);
  if (!mimeType) throw new AttachmentError(415, "Only PDF, PNG, JPEG and WebP files can be attached.");

  const db = await getDb();
  return db.transaction(async (tx) => {
    const trip = await tx.query.trips.findFirst({ where: eq(trips.id, tripId), columns: { rev: true } });
    if (!trip) throw new AttachmentError(404, "The trip was not found.");
    const block = await tx.query.timelineBlocks.findFirst({
      where: and(eq(timelineBlocks.tripId, tripId), eq(timelineBlocks.id, blockId)),
      columns: { id: true },
    });
    if (!block) throw new AttachmentError(404, "That block no longer exists.");
    const [{ n }] = await tx
      .select({ n: count() })
      .from(attachments)
      .where(and(eq(attachments.tripId, tripId), eq(attachments.blockId, blockId)));
    if (n >= MAX_ATTACHMENTS_PER_BLOCK) throw new AttachmentError(409, `A block can hold at most ${MAX_ATTACHMENTS_PER_BLOCK} files.`);

    const rev = trip.rev + 1;
    const now = Date.now();
    const row = {
      tripId,
      id: "att-" + crypto.randomUUID(),
      blockId,
      fileName: cleanName(file.name, mimeType),
      mimeType,
      sizeBytes: file.bytes.byteLength,
      sha256: sha256(file.bytes),
      data: Buffer.from(file.bytes),
      rev,
      createdAt: now,
    };
    await tx.insert(attachments).values(row);
    await tx.update(trips).set({ rev, updatedAt: now }).where(eq(trips.id, tripId));
    return { id: row.id, blockId, fileName: row.fileName, mimeType, sizeBytes: row.sizeBytes, createdAt: now };
  });
}

export async function deleteAttachment(tripId: string, attachmentId: string): Promise<void> {
  const db = await getDb();
  await db.transaction(async (tx) => {
    const trip = await tx.query.trips.findFirst({ where: eq(trips.id, tripId), columns: { rev: true } });
    if (!trip) return;
    const where = and(eq(attachments.tripId, tripId), eq(attachments.id, attachmentId));
    const gone = await tx.delete(attachments).where(where).returning({ id: attachments.id });
    if (gone.length === 0) return;
    const rev = trip.rev + 1;
    const now = Date.now();
    await tx
      .insert(tombstones)
      .values({ tripId, entity: ENTITY.attachment, entityId: attachmentId, rev, deletedAt: now })
      .onConflictDoUpdate({ target: [tombstones.tripId, tombstones.entity, tombstones.entityId], set: { rev, deletedAt: now } });
    await tx.update(trips).set({ rev, updatedAt: now }).where(eq(trips.id, tripId));
  });
}

export async function readAttachment(tripId: string, attachmentId: string) {
  const db = await getDb();
  const [row] = await db
    .select({ fileName: attachments.fileName, mimeType: attachments.mimeType, data: attachments.data })
    .from(attachments)
    .where(and(eq(attachments.tripId, tripId), eq(attachments.id, attachmentId)));
  return row ?? null;
}

/** The file's response: shown in the browser, never cached by a shared cache. */
export function fileResponse(file: { fileName: string; mimeType: string; data: Buffer }): Response {
  const ascii = file.fileName.replace(/[^\x20-\x7e]|["\\]/g, "_");
  return new Response(new Uint8Array(file.data), {
    headers: {
      "Content-Type": file.mimeType,
      "Content-Length": String(file.data.byteLength),
      "Content-Disposition": `inline; filename="${ascii}"; filename*=UTF-8''${encodeURIComponent(file.fileName)}`,
      "Cache-Control": "private, no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

/** Reads the `file` field of a multipart upload. */
export async function readUpload(request: Request): Promise<{ form: FormData; file: { name: string; bytes: Uint8Array } }> {
  const length = Number(request.headers.get("content-length") ?? 0);
  if (length > MAX_ATTACHMENT_BYTES + 64 * 1024) throw new AttachmentError(413, "Files can be at most 4 MB.");
  let form: FormData;
  try {
    form = await request.formData();
  } catch {
    throw new AttachmentError(400, "Send the file as multipart form data.");
  }
  const file = form.get("file");
  if (!(file instanceof File)) throw new AttachmentError(400, "Choose a file to upload.");
  if (file.size > MAX_ATTACHMENT_BYTES) throw new AttachmentError(413, "Files can be at most 4 MB.");
  return { form, file: { name: file.name, bytes: new Uint8Array(await file.arrayBuffer()) } };
}

export function attachmentErrorResponse(e: unknown): Response {
  if (e instanceof AttachmentError) return Response.json({ error: e.message }, { status: e.status, headers: { "Cache-Control": "no-store" } });
  console.error("attachment request failed", e);
  return Response.json({ error: "Something went wrong. Try again." }, { status: 500 });
}

// Upload links

export async function createUploadLink(tripId: string, blockId: string): Promise<{ token: string; expiresAt: number }> {
  const db = await getDb();
  const block = await db.query.timelineBlocks.findFirst({
    where: and(eq(timelineBlocks.tripId, tripId), eq(timelineBlocks.id, blockId)),
    columns: { id: true },
  });
  if (!block) throw new AttachmentError(404, "That block does not exist.");
  const now = Date.now();
  await db.delete(uploadLinks).where(lt(uploadLinks.expiresAt, now));
  const token = randomBytes(24).toString("base64url");
  const expiresAt = now + UPLOAD_LINK_TTL_MS;
  await db.insert(uploadLinks).values({ tokenHash: sha256(token), tripId, blockId, expiresAt, createdAt: now });
  return { token, expiresAt };
}

export interface UploadTarget {
  tripId: string;
  blockId: string;
  blockTitle: string;
  expiresAt: number;
  files: AttachmentMeta[];
}

/** What an upload link points at, or null when it is unknown, expired, or its block was deleted. */
export async function resolveUploadLink(token: string): Promise<UploadTarget | null> {
  if (!/^[A-Za-z0-9_-]{20,64}$/.test(token)) return null;
  const db = await getDb();
  const link = await db.query.uploadLinks.findFirst({ where: eq(uploadLinks.tokenHash, sha256(token)) });
  if (!link || link.expiresAt < Date.now()) return null;
  const block = await db.query.timelineBlocks.findFirst({
    where: and(eq(timelineBlocks.tripId, link.tripId), eq(timelineBlocks.id, link.blockId)),
    columns: { title: true },
  });
  if (!block) return null;
  const files = await db
    .select(metaColumns)
    .from(attachments)
    .where(and(eq(attachments.tripId, link.tripId), eq(attachments.blockId, link.blockId)))
    .orderBy(attachments.createdAt);
  return { tripId: link.tripId, blockId: link.blockId, blockTitle: block.title || "Untitled block", expiresAt: link.expiresAt, files };
}
