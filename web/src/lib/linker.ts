import type { Item, TripDoc } from "./types";

export const LINK_RADIUS_M = 800;
const EARTH_R = 6_371_000;
const rad = (d: number) => (d * Math.PI) / 180;

export function haversineM(lat1: number, lng1: number, lat2: number, lng2: number): number {
  const dLat = rad(lat2 - lat1);
  const dLng = rad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_R * Math.asin(Math.sqrt(a));
}

type LinkFields = Pick<Item, "linkedBlockId" | "linkSource" | "distanceFromBlockMeters" | "dayId">;

const same = (a: LinkFields, b: LinkFields) =>
  a.linkedBlockId === b.linkedBlockId &&
  a.linkSource === b.linkSource &&
  a.distanceFromBlockMeters === b.distanceFromBlockMeters &&
  a.dayId === b.dayId;

// Rewrites every link the user did not set by hand. A MANUAL link survives until its block
// leaves the timeline; a MANUAL unlink (linkedBlockId null) is never overridden.
export function relink(doc: TripDoc): TripDoc {
  const anchors = Object.values(doc.blocks).filter(
    (b) => b.dayId !== null && b.lat !== null && b.lng !== null && b.blockType !== "TRANSIT",
  );
  let items = doc.items;

  for (const item of Object.values(doc.items)) {
    if (item.category === "MORNING_PREP") continue;
    let next: LinkFields;

    const manualTarget = item.linkSource === "MANUAL" && item.linkedBlockId ? doc.blocks[item.linkedBlockId] : null;
    if (item.linkSource === "MANUAL" && (item.linkedBlockId === null || manualTarget?.dayId)) {
      next = { ...item, dayId: manualTarget?.dayId ?? item.dayId };
    } else {
      let best: { id: string; dayId: string; meters: number } | null = null;
      if (item.geoVerified && item.lat !== null && item.lng !== null) {
        for (const b of anchors) {
          const meters = Math.round(haversineM(item.lat, item.lng, b.lat!, b.lng!));
          if (meters <= LINK_RADIUS_M && (!best || meters < best.meters)) best = { id: b.id, dayId: b.dayId!, meters };
        }
      }
      next = {
        linkedBlockId: best?.id ?? null,
        linkSource: best ? "AUTO" : null,
        distanceFromBlockMeters: best?.meters ?? null,
        dayId: best?.dayId ?? null,
      };
    }

    if (!same(item, next)) {
      if (items === doc.items) items = { ...doc.items };
      items[item.id] = { ...item, ...next };
    }
  }
  return items === doc.items ? doc : { ...doc, items };
}
