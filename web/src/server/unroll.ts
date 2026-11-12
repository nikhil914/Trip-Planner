"use server";

import type { BlockDraft } from "@/lib/types";
import { resolveOne } from "./lib/places-core";

// The exact pin. Never the @lat,lng pair, which is the camera viewport centre.
const PIN_RE = /!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)/;
const NAME_RE = /\/maps\/place\/([^/@]+)/;
const MAPS_HOST_RE =
  /^(maps\.app\.goo\.gl|goo\.gl|g\.co|consent\.google\.com|(www\.|maps\.)?google\.(com|[a-z]{2,3}|com?\.[a-z]{2}))$/;

export type UnrollResult =
  | { ok: true; draft: BlockDraft; expandedUrl: string }
  | { ok: false; error: "not_a_maps_link" | "google_throttled" | "too_many_redirects" | "unparseable" | "network" };

async function expandMapsUrl(input: string): Promise<string> {
  let current = input;
  for (let hop = 0; hop < 5; hop++) {
    let u: URL;
    try {
      u = new URL(current);
    } catch {
      throw new Error("not_a_maps_link");
    }
    if (u.protocol !== "https:" || !MAPS_HOST_RE.test(u.hostname)) throw new Error("not_a_maps_link");
    if (u.pathname.startsWith("/sorry")) throw new Error("google_throttled");
    if (u.hostname === "consent.google.com") {
      const next = u.searchParams.get("continue");
      if (!next) throw new Error("google_throttled");
      current = next;
      continue;
    }
    if (u.pathname.startsWith("/maps/") || u.searchParams.has("q")) return current;
    const res = await fetch(current, {
      redirect: "manual",
      headers: {
        "Accept-Language": "en",
        "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36",
      },
      signal: AbortSignal.timeout(5000),
      cache: "no-store",
    }).catch(() => {
      throw new Error("network");
    });
    const location = res.headers.get("location");
    if (res.status === 429) throw new Error("google_throttled");
    if (res.status < 300 || res.status >= 400 || !location) return current;
    current = new URL(location, current).toString();
  }
  throw new Error("too_many_redirects");
}

function parseMapsUrl(expanded: string) {
  const pin = PIN_RE.exec(expanded);
  const name = NAME_RE.exec(expanded);
  const q = new URL(expanded).searchParams.get("q");
  let decoded: string | null = q;
  if (name) {
    try {
      decoded = decodeURIComponent(name[1].replace(/\+/g, " "));
    } catch {
      decoded = name[1].replace(/\+/g, " ");
    }
  }
  return { name: decoded, lat: pin ? Number(pin[1]) : null, lng: pin ? Number(pin[2]) : null };
}

export async function unrollMapsLinkAction(url: string): Promise<UnrollResult> {
  let expanded: string;
  try {
    expanded = await expandMapsUrl(url.trim());
  } catch (e) {
    return { ok: false, error: (e as Error).message as Exclude<UnrollResult, { ok: true }>["error"] };
  }
  const parsed = parseMapsUrl(expanded);
  if (!parsed.name && parsed.lat === null) return { ok: false, error: "unparseable" };

  // The URL never carries a ChIJ place id, so look one up, biased to the pin when there is one.
  const near = parsed.lat !== null && parsed.lng !== null ? { lat: parsed.lat, lng: parsed.lng } : undefined;
  const place = parsed.name ? await resolveOne({ query: parsed.name, near, radiusM: 150 }) : null;

  return {
    ok: true,
    expandedUrl: expanded,
    draft: {
      title: place?.displayName || parsed.name || "Dropped pin",
      subtitleOrTip: place?.address || null,
      lat: parsed.lat ?? place?.lat ?? null,
      lng: parsed.lng ?? place?.lng ?? null,
      googlePlaceId: place?.id ?? null,
      googleMapsUri: place?.googleMapsUri ?? expanded,
    },
  };
}
