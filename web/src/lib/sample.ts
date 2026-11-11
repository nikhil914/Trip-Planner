import { addDays } from "./time";
import type { Block, Day, Item, TripDoc } from "./types";

export function buildDays(startDate: string, count: number, timezone: string, existing: Day[] = []): Day[] {
  return Array.from({ length: count }, (_, i) => ({
    id: existing[i]?.id ?? `day-${crypto.randomUUID()}`,
    dayNumber: i + 1,
    dateIso: addDays(startDate, i),
    timezone: existing[i]?.timezone ?? timezone,
  }));
}

export function blankBlock(id: string): Block {
  return {
    id,
    dayId: null,
    sortOrder: 0,
    startMinute: null,
    durationMinutes: 60,
    isFixedTime: false,
    blockType: "ACTIVITY",
    title: "",
    subtitleOrTip: null,
    lat: null,
    lng: null,
    googlePlaceId: null,
    googleMapsUri: null,
    estimatedCostLocal: null,
    bookingRef: null,
    opensMinute: null,
    closesMinute: null,
    localScriptName: null,
    phoneticPronunciation: null,
    status: "PENDING",
  };
}

export function blankItem(id: string): Item {
  return {
    id,
    linkedBlockId: null,
    linkSource: null,
    dayId: null,
    sortOrder: 0,
    category: "TO_BUY",
    title: "",
    shopOrVenueName: null,
    priceBenchmarkNote: null,
    priceSource: null,
    estimatedCostLocal: null,
    lat: null,
    lng: null,
    geoVerified: false,
    googlePlaceId: null,
    distanceFromBlockMeters: null,
    localScriptText: null,
    phoneticPronunciation: null,
    isChecked: false,
  };
}

// Demo data with fixed ids so server and client render the same first paint. Coordinates are approximate.
export function sampleDoc(): TripDoc {
  const days: Day[] = [1, 2, 3].map((n) => ({
    id: `day-${n}`,
    dayNumber: n,
    dateIso: addDays("2027-04-10", n - 1),
    timezone: "Asia/Tbilisi",
  }));

  const block = (id: string, patch: Partial<Block>): Block => ({ ...blankBlock(id), ...patch });
  const item = (id: string, patch: Partial<Item>): Item => ({ ...blankItem(id), ...patch });

  const blocks: Block[] = [
    block("b-narikala", {
      dayId: "day-1", startMinute: 600, durationMinutes: 90, title: "Narikala Fortress",
      subtitleOrTip: "Take the cable car from Rike Park", lat: 41.688, lng: 44.8086, estimatedCostLocal: 5,
      localScriptName: "ნარიყალა", phoneticPronunciation: "na-ri-KA-la",
    }),
    block("b-baths", {
      dayId: "day-1", startMinute: 720, durationMinutes: 90, title: "Abanotubani sulphur baths",
      lat: 41.6884, lng: 44.811, estimatedCostLocal: 80, isFixedTime: true, bookingRef: "Room 5, 12:00",
    }),
    block("b-drybridge", {
      dayId: "day-2", startMinute: 630, durationMinutes: 75, title: "Dry Bridge Market",
      lat: 41.7006, lng: 44.804, opensMinute: 600, closesMinute: 1020,
    }),
    block("b-train", {
      dayId: "day-3", startMinute: 490, durationMinutes: 330, title: "Train to Kutaisi",
      blockType: "TRANSIT", isFixedTime: true, bookingRef: "Coach 3, seat 14", estimatedCostLocal: 25,
    }),
    block("b-sameba", { sortOrder: 1000, title: "Holy Trinity Cathedral", lat: 41.6977, lng: 44.8166, durationMinutes: 60 }),
    block("b-funicular", { sortOrder: 2000, title: "Mtatsminda funicular", lat: 41.6946, lng: 44.7866, durationMinutes: 120, estimatedCostLocal: 10 }),
  ];

  const items: Item[] = [
    item("i-khinkali", {
      category: "EAT_SUGGESTION", title: "Khinkali", shopOrVenueName: "Dumpling house by Dry Bridge",
      lat: 41.7009, lng: 44.8052, geoVerified: true, estimatedCostLocal: 25,
      priceBenchmarkNote: "Fair price: 1.5 to 2 GEL per piece", priceSource: "USER",
      localScriptText: "ხინკალი", phoneticPronunciation: "khin-KA-li",
    }),
    item("i-khachapuri", {
      category: "EAT_SUGGESTION", title: "Adjaruli khachapuri", shopOrVenueName: "Bakery near the baths",
      lat: 41.689, lng: 44.8095, geoVerified: true, estimatedCostLocal: 18, sortOrder: 1000,
      localScriptText: "ხაჭაპური", phoneticPronunciation: "kha-cha-PU-ri",
    }),
    item("i-churchkhela", {
      category: "TO_BUY", title: "Churchkhela", shopOrVenueName: "Dezerter Bazaar",
      lat: 41.7095, lng: 44.7979, geoVerified: true, estimatedCostLocal: 12,
      priceBenchmarkNote: "Fair price: 3 to 5 GEL each. Tourist stalls ask 8 or more", priceSource: "USER",
      localScriptText: "ჩურჩხელა", phoneticPronunciation: "church-KHE-la",
    }),
    item("i-passport", { category: "MORNING_PREP", title: "Carry physical passport", dayId: "day-3" }),
  ];

  return {
    trip: {
      id: "trip-sample",
      title: "Tbilisi long weekend",
      destinationLabel: "Tbilisi, Georgia",
      destinationTimezone: "Asia/Tbilisi",
      homeCurrency: "USD",
      localCurrency: "GEL",
      exchangeRate: 0.37,
      startDate: days[0].dateIso,
      endDate: days[2].dateIso,
    },
    days,
    blocks: Object.fromEntries(blocks.map((b) => [b.id, b])),
    items: Object.fromEntries(items.map((i) => [i.id, i])),
  };
}
