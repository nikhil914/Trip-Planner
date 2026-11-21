"use server";

import type { TripDoc } from "@/lib/types";
import { deleteAttachment, listAttachments, tripIdOf, type AttachmentMeta } from "./lib/attachments";
import { createConnectorKey, listConnectorKeys, revokeConnectorKey, type ConnectorKeyInfo } from "./lib/connector-keys";
import { audit } from "./lib/audit";
import { publicOrigin } from "./lib/origin";
import { requireUser } from "./lib/session";
import { loadTrip, saveTrip, TripDocSchema, tripRev, type SaveResult } from "./lib/trip-repo";
import { createPairingCode, listDevices, revokeDevice } from "./lib/sync-core";

export async function loadTripAction(): Promise<{ doc: TripDoc; rev: number } | null> {
  const user = await requireUser();
  return loadTrip(user.id);
}

export async function saveTripAction(doc: TripDoc, baseRev: number | null): Promise<SaveResult> {
  const user = await requireUser();
  const parsed = TripDocSchema.safeParse(doc);
  if (!parsed.success) throw new Error("The trip could not be saved: " + (parsed.error.issues[0]?.message ?? "invalid data"));
  return saveTrip(user.id, parsed.data, typeof baseRev === "number" ? baseRev : null);
}

export async function tripRevAction(): Promise<number | null> {
  const user = await requireUser();
  return tripRev(user.id);
}

export async function createPairingCodeAction(): Promise<{ code: string; expiresAt: number } | null> {
  const user = await requireUser();
  return createPairingCode(user.id);
}

export interface PairedDevice {
  id: string;
  name: string;
  appVersion: string;
  createdAt: number;
  lastSeenAt: number | null;
}

export async function listDevicesAction(): Promise<PairedDevice[]> {
  const user = await requireUser();
  return listDevices(user.id);
}

export async function revokeDeviceAction(deviceId: string): Promise<PairedDevice[]> {
  const user = await requireUser();
  await revokeDevice(user.id, String(deviceId));
  return listDevices(user.id);
}

export async function listAttachmentsAction(): Promise<AttachmentMeta[]> {
  const user = await requireUser();
  const tripId = await tripIdOf(user.id);
  return tripId ? listAttachments(tripId) : [];
}

export async function deleteAttachmentAction(attachmentId: string): Promise<AttachmentMeta[]> {
  const user = await requireUser();
  const tripId = await tripIdOf(user.id);
  if (!tripId) return [];
  await deleteAttachment(tripId, String(attachmentId));
  return listAttachments(tripId);
}

export async function listConnectorKeysAction(): Promise<ConnectorKeyInfo[]> {
  const user = await requireUser();
  return listConnectorKeys(user.id);
}

/** Returns the secret connector URL once, with the updated list. */
export async function createConnectorKeyAction(name: string): Promise<{ url: string; keys: ConnectorKeyInfo[] }> {
  const user = await requireUser();
  const { key } = await createConnectorKey(user.id, String(name ?? ""));
  await audit("connector_created", user.id, { detail: String(name ?? "").slice(0, 60) || null });
  return { url: `${await publicOrigin()}/api/mcp/${key}`, keys: await listConnectorKeys(user.id) };
}

export async function revokeConnectorKeyAction(id: string): Promise<ConnectorKeyInfo[]> {
  const user = await requireUser();
  await revokeConnectorKey(user.id, String(id));
  await audit("connector_revoked", user.id);
  return listConnectorKeys(user.id);
}
