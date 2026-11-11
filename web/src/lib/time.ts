export const PX_PER_MIN = 1;
export const SNAP_MIN = 15;
export const DAY_MINUTES = 1440;

export function hhmm(minute: number): string {
  const m = ((minute % DAY_MINUTES) + DAY_MINUTES) % DAY_MINUTES;
  return `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;
}

export function snapMinute(rawMinute: number, durationMinutes: number): number {
  const snapped = Math.round(rawMinute / SNAP_MIN) * SNAP_MIN;
  return Math.max(0, Math.min(DAY_MINUTES - durationMinutes, snapped));
}

export function addDays(dateIso: string, days: number): string {
  const d = new Date(`${dateIso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

export function formatDay(dateIso: string): string {
  return new Date(`${dateIso}T00:00:00Z`).toLocaleDateString("en-GB", {
    weekday: "short",
    day: "numeric",
    month: "short",
    timeZone: "UTC",
  });
}
