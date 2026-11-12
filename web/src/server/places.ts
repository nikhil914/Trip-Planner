"use server";

import type { PlaceHit } from "@/lib/types";
import { searchPlaces, type PlaceSource } from "./lib/places-core";

export type PlaceSearchResult =
  | { ok: true; source: PlaceSource; hits: PlaceHit[] }
  | { ok: false; error: string };

export async function searchPlacesAction(query: string, near?: { lat: number; lng: number }): Promise<PlaceSearchResult> {
  const q = query.trim().slice(0, 200);
  if (q.length < 2) return { ok: true, source: "osm", hits: [] };
  try {
    return { ok: true, ...(await searchPlaces({ query: q, near, radiusM: 20_000, limit: 6 })) };
  } catch (e) {
    return { ok: false, error: (e as Error).message };
  }
}
