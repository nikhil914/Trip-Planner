import type { PlaceHit } from "@/lib/types";

const GOOGLE_BASE = "https://places.googleapis.com/v1";

// Pro-tier fields only. Adding regularOpeningHours here bills every search at a higher tier.
const SEARCH_MASK = "places.id,places.displayName,places.formattedAddress,places.location,places.googleMapsUri";

export interface PlaceQuery {
  query: string;
  near?: { lat: number; lng: number };
  radiusM?: number;
  languageCode?: string;
  limit?: number;
}

export type PlaceSource = "google" | "osm";

interface GooglePlace {
  id: string;
  displayName?: { text?: string };
  formattedAddress?: string;
  location: { latitude: number; longitude: number };
  googleMapsUri?: string;
}

async function searchGoogle(q: PlaceQuery, key: string): Promise<PlaceHit[]> {
  const res = await fetch(`${GOOGLE_BASE}/places:searchText`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Goog-Api-Key": key, "X-Goog-FieldMask": SEARCH_MASK },
    body: JSON.stringify({
      textQuery: q.query,
      pageSize: q.limit ?? 5,
      languageCode: q.languageCode ?? "en",
      ...(q.near && {
        locationBias: { circle: { center: { latitude: q.near.lat, longitude: q.near.lng }, radius: q.radiusM ?? 2000 } },
      }),
    }),
    signal: AbortSignal.timeout(8000),
    cache: "no-store",
  });
  if (!res.ok) throw new Error(`places_search_${res.status}`);
  const json = (await res.json()) as { places?: GooglePlace[] };
  return (json.places ?? []).map((p) => ({
    id: p.id,
    displayName: p.displayName?.text ?? "",
    address: p.formattedAddress ?? "",
    lat: p.location.latitude,
    lng: p.location.longitude,
    googleMapsUri: p.googleMapsUri ?? null,
  }));
}

interface PhotonFeature {
  geometry: { coordinates: [number, number] };
  properties: { name?: string; street?: string; city?: string; country?: string };
}

// OpenStreetMap fallback. No Google place id, so the phone can only pin coordinates for these.
async function searchPhoton(q: PlaceQuery): Promise<PlaceHit[]> {
  const url = new URL("https://photon.komoot.io/api/");
  url.searchParams.set("q", q.query);
  url.searchParams.set("limit", String(q.limit ?? 5));
  url.searchParams.set("lang", "en");
  if (q.near) {
    url.searchParams.set("lat", String(q.near.lat));
    url.searchParams.set("lon", String(q.near.lng));
  }
  const res = await fetch(url, { signal: AbortSignal.timeout(8000), cache: "no-store" });
  if (!res.ok) throw new Error(`osm_search_${res.status}`);
  const json = (await res.json()) as { features?: PhotonFeature[] };
  return (json.features ?? [])
    .filter((f) => f.properties.name)
    .map((f) => ({
      id: null,
      displayName: f.properties.name!,
      address: [f.properties.street, f.properties.city, f.properties.country].filter(Boolean).join(", "),
      lat: f.geometry.coordinates[1],
      lng: f.geometry.coordinates[0],
      googleMapsUri: null,
    }));
}

export async function searchPlaces(q: PlaceQuery): Promise<{ source: PlaceSource; hits: PlaceHit[] }> {
  const key = process.env.GOOGLE_PLACES_API_KEY;
  if (key) {
    try {
      return { source: "google", hits: await searchGoogle(q, key) };
    } catch {
      // Quota or outage: fall through to OSM so ingest keeps working.
    }
  }
  return { source: "osm", hits: await searchPhoton(q) };
}

export async function resolveOne(q: PlaceQuery): Promise<PlaceHit | null> {
  try {
    const { hits } = await searchPlaces({ ...q, limit: 1 });
    return hits[0] ?? null;
  } catch {
    return null;
  }
}
