"use server";

import type { TripDoc } from "@/lib/types";
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
