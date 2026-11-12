import { haversineM } from "./linker";
import type { Block } from "./types";

export type Warning =
  | { kind: "OUTSIDE_HOURS"; blockId: string; title: string }
  | { kind: "OVERLAP"; blockId: string; otherId: string; title: string; otherTitle: string; minutes: number }
  | { kind: "TIGHT_TRANSIT"; blockId: string; otherId: string; title: string; otherTitle: string; neededMin: number; bufferMin: number };

const WALK_M_PER_MIN = 75;
const DETOUR = 1.4;
const RIDE_ABOVE_M = 2500;

export function checkDay(blocks: Block[]): Warning[] {
  const day = blocks
    .filter((b) => b.startMinute !== null)
    .sort((a, b) => a.startMinute! - b.startMinute! || a.sortOrder - b.sortOrder);
  const out: Warning[] = [];

  day.forEach((b, i) => {
    const end = b.startMinute! + b.durationMinutes;
    if (b.opensMinute !== null && b.closesMinute !== null && (b.startMinute! < b.opensMinute || end > b.closesMinute)) {
      out.push({ kind: "OUTSIDE_HOURS", blockId: b.id, title: b.title });
    }
    const next = day[i + 1];
    if (!next) return;
    const bufferMin = next.startMinute! - end;
    if (bufferMin < 0) {
      out.push({ kind: "OVERLAP", blockId: b.id, otherId: next.id, title: b.title, otherTitle: next.title, minutes: -bufferMin });
      return;
    }
    if (b.lat === null || b.lng === null || next.lat === null || next.lng === null) return;
    const meters = haversineM(b.lat, b.lng, next.lat, next.lng) * DETOUR;
    const neededMin = Math.ceil(meters > RIDE_ABOVE_M ? meters / 400 + 10 : meters / WALK_M_PER_MIN);
    if (neededMin > bufferMin) {
      out.push({ kind: "TIGHT_TRANSIT", blockId: b.id, otherId: next.id, title: b.title, otherTitle: next.title, neededMin, bufferMin });
    }
  });
  return out;
}
