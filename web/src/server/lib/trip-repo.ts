import "server-only";
import { and, eq, gt } from "drizzle-orm";
import { z } from "zod";
import { getDb, schema, type Db } from "@/db/client";
import { ENTITY, type EntityName } from "@/db/schema";
import type { Block, Day, Item, TripDoc } from "@/lib/types";

const { trips, tripDays, timelineBlocks, contextualItems, tombstones } = schema;

type Tx = Parameters<Parameters<Db["transaction"]>[0]>[0];
type DayRow = typeof tripDays.$inferSelect;
type BlockRow = typeof timelineBlocks.$inferSelect;
type ItemRow = typeof contextualItems.$inferSelect;

const id = z.string().min(1).max(80);
const text = (max: number) => z.string().max(max);
const minute = z.number().int().min(0).max(1439);
const coord = z.number().min(-180).max(180);
const money = z.number().min(0).max(1e9);
const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

const DaySchema = z.object({
  id,
  dayNumber: z.number().int().min(1).max(60),
  dateIso: isoDate,
  timezone: text(64).min(1),
});

const BlockSchema = z.object({
  id,
  dayId: id.nullable(),
  sortOrder: z.number().int(),
  startMinute: minute.nullable(),
  durationMinutes: z.number().int().min(5).max(1440),
  isFixedTime: z.boolean(),
  blockType: z.enum(["ACTIVITY", "TRANSIT", "HOTEL", "FOOD_STOP"]),
  title: text(200),
  subtitleOrTip: text(2000).nullable(),
  lat: coord.nullable(),
  lng: coord.nullable(),
  googlePlaceId: text(300).nullable(),
  googleMapsUri: text(2000).nullable(),
  estimatedCostLocal: money.nullable(),
  bookingRef: text(200).nullable(),
  opensMinute: minute.nullable(),
  closesMinute: minute.nullable(),
  localScriptName: text(200).nullable(),
  phoneticPronunciation: text(200).nullable(),
  status: z.enum(["PENDING", "DONE", "SKIPPED"]),
});

const ItemSchema = z.object({
  id,
  linkedBlockId: id.nullable(),
  linkSource: z.enum(["AUTO", "MANUAL"]).nullable(),
  dayId: id.nullable(),
  sortOrder: z.number().int(),
  category: z.enum(["TO_BUY", "EAT_SUGGESTION", "MORNING_PREP"]),
  title: text(200),
  shopOrVenueName: text(200).nullable(),
  priceBenchmarkNote: text(2000).nullable(),
  priceSource: z.enum(["AI_ESTIMATE", "USER"]).nullable(),
  estimatedCostLocal: money.nullable(),
  lat: coord.nullable(),
  lng: coord.nullable(),
  geoVerified: z.boolean(),
  googlePlaceId: text(300).nullable(),
  distanceFromBlockMeters: z.number().int().min(0).nullable(),
  localScriptText: text(500).nullable(),
  phoneticPronunciation: text(500).nullable(),
  isChecked: z.boolean(),
});

export const TripDocSchema = z.object({
  trip: z.object({
    id: z.string().max(80),
    title: text(200).min(1),
    destinationLabel: text(200),
    destinationTimezone: text(64).min(1),
    homeCurrency: text(8),
    localCurrency: text(8),
    exchangeRate: z.number().positive().max(1e9),
    startDate: isoDate,
    endDate: isoDate,
  }),
  days: z.array(DaySchema).min(1).max(60),
  blocks: z.record(id, BlockSchema).refine((r) => Object.keys(r).length <= 2000, "Too many blocks."),
  items: z.record(id, ItemSchema).refine((r) => Object.keys(r).length <= 5000, "Too many items."),
});

const dayOf = ({ id, dayNumber, dateIso, timezone }: DayRow): Day => ({ id, dayNumber, dateIso, timezone });

function blockOf(r: BlockRow): Block {
  return {
    id: r.id,
    dayId: r.dayId,
    sortOrder: r.sortOrder,
    startMinute: r.startMinute,
    durationMinutes: r.durationMinutes,
    isFixedTime: r.isFixedTime,
    blockType: r.blockType,
    title: r.title,
    subtitleOrTip: r.subtitleOrTip,
    lat: r.lat,
    lng: r.lng,
    googlePlaceId: r.googlePlaceId,
    googleMapsUri: r.googleMapsUri,
    estimatedCostLocal: r.estimatedCostLocal,
    bookingRef: r.bookingRef,
    opensMinute: r.opensMinute,
    closesMinute: r.closesMinute,
    localScriptName: r.localScriptName,
    phoneticPronunciation: r.phoneticPronunciation,
    status: r.status,
  };
}

function itemOf(r: ItemRow): Item {
  return {
    id: r.id,
    linkedBlockId: r.linkedBlockId,
    linkSource: r.linkSource,
    dayId: r.dayId,
    sortOrder: r.sortOrder,
    category: r.category,
    title: r.title,
    shopOrVenueName: r.shopOrVenueName,
    priceBenchmarkNote: r.priceBenchmarkNote,
    priceSource: r.priceSource,
    estimatedCostLocal: r.estimatedCostLocal,
    lat: r.lat,
    lng: r.lng,
    geoVerified: r.geoVerified,
    googlePlaceId: r.googlePlaceId,
    distanceFromBlockMeters: r.distanceFromBlockMeters,
    localScriptText: r.localScriptText,
    phoneticPronunciation: r.phoneticPronunciation,
    isChecked: r.isChecked,
  };
}

const same = (a: object, b: object) => {
  const x = a as Record<string, unknown>;
  const y = b as Record<string, unknown>;
  return Object.keys(x).every((k) => x[k] === y[k]);
};

async function readDoc(tx: Tx | Db, trip: typeof trips.$inferSelect): Promise<TripDoc> {
  const [days, blocks, items] = await Promise.all([
    tx.select().from(tripDays).where(eq(tripDays.tripId, trip.id)).orderBy(tripDays.dayNumber),
    tx.select().from(timelineBlocks).where(eq(timelineBlocks.tripId, trip.id)),
    tx.select().from(contextualItems).where(eq(contextualItems.tripId, trip.id)),
  ]);
  return {
    trip: {
      id: trip.id,
      title: trip.title,
      destinationLabel: trip.destinationLabel,
      destinationTimezone: trip.destinationTimezone,
      homeCurrency: trip.homeCurrency,
      localCurrency: trip.localCurrency,
      exchangeRate: trip.exchangeRate,
      startDate: trip.startDate,
      endDate: trip.endDate,
    },
    days: days.map(dayOf),
    blocks: Object.fromEntries(blocks.map((b) => [b.id, blockOf(b)])),
    items: Object.fromEntries(items.map((i) => [i.id, itemOf(i)])),
  };
}

export async function loadTrip(userId: string): Promise<{ doc: TripDoc; rev: number } | null> {
  const db = await getDb();
  const trip = await db.query.trips.findFirst({ where: eq(trips.ownerId, userId) });
  if (!trip) return null;
  return { doc: await readDoc(db, trip), rev: trip.rev };
}

export async function tripRev(userId: string): Promise<number | null> {
  const db = await getDb();
  const trip = await db.query.trips.findFirst({ where: eq(trips.ownerId, userId), columns: { rev: true } });
  return trip?.rev ?? null;
}

export interface SaveResult {
  rev: number;
  /** Set when the server held changes the browser had not seen. The browser replaces its copy with this one. */
  merged: TripDoc | null;
}

/**
 * Writes the browser's copy of the trip. Only rows that differ are touched, and each gets the new revision,
 * so a phone pulling with an older cursor receives exactly what changed.
 *
 * `baseRev` is the revision the browser last saw. Rows changed on the server after it (a phone check-off,
 * a delay shift, another browser tab) keep the server's `status`, `is_checked` and `start_minute`,
 * and are not deleted just because this browser's copy lacks them.
 */
export async function saveTrip(userId: string, doc: TripDoc, baseRev: number | null): Promise<SaveResult> {
  const db = await getDb();
  return db.transaction(async (tx) => {
    const now = Date.now();
    let trip = await tx.query.trips.findFirst({ where: eq(trips.ownerId, userId) });
    const meta = {
      title: doc.trip.title,
      destinationLabel: doc.trip.destinationLabel,
      destinationTimezone: doc.trip.destinationTimezone,
      homeCurrency: doc.trip.homeCurrency,
      localCurrency: doc.trip.localCurrency,
      exchangeRate: doc.trip.exchangeRate,
      startDate: doc.trip.startDate,
      endDate: doc.trip.endDate,
    };
    let changed = false;
    if (!trip) {
      [trip] = await tx
        .insert(trips)
        .values({ id: "t-" + crypto.randomUUID(), ownerId: userId, ...meta, rev: 0, metaRev: 1, updatedAt: now })
        .returning();
      changed = true;
    }
    const tripId = trip.id;
    const rev = trip.rev + 1;
    const base = baseRev ?? trip.rev;
    const stale = base < trip.rev;

    const [oldDays, oldBlocks, oldItems, recentTombs] = await Promise.all([
      tx.select().from(tripDays).where(eq(tripDays.tripId, tripId)),
      tx.select().from(timelineBlocks).where(eq(timelineBlocks.tripId, tripId)),
      tx.select().from(contextualItems).where(eq(contextualItems.tripId, tripId)),
      stale ? tx.select().from(tombstones).where(and(eq(tombstones.tripId, tripId), gt(tombstones.rev, base))) : [],
    ]);
    const deletedElsewhere = new Set(recentTombs.map((t) => t.entity + ":" + t.entityId));
    const stamp = { tripId, rev, updatedAt: now };

    async function bury(entity: EntityName, entityId: string) {
      await tx
        .insert(tombstones)
        .values({ tripId, entity, entityId, rev, deletedAt: now })
        .onConflictDoUpdate({ target: [tombstones.tripId, tombstones.entity, tombstones.entityId], set: { rev, deletedAt: now } });
    }
    const unbury = (entity: EntityName, entityId: string) =>
      tx.delete(tombstones).where(and(eq(tombstones.tripId, tripId), eq(tombstones.entity, entity), eq(tombstones.entityId, entityId)));

    // Days
    const dayById = new Map(oldDays.map((d) => [d.id, d]));
    for (const day of doc.days) {
      const old = dayById.get(day.id);
      dayById.delete(day.id);
      if (old && same(day, old)) continue;
      if (!old && deletedElsewhere.has(ENTITY.day + ":" + day.id)) continue;
      await tx.insert(tripDays).values({ ...day, ...stamp }).onConflictDoUpdate({ target: [tripDays.tripId, tripDays.id], set: { ...day, ...stamp } });
      if (!old) await unbury(ENTITY.day, day.id);
      changed = true;
    }
    for (const old of dayById.values()) {
      if (stale && old.rev > base) continue;
      await tx.delete(tripDays).where(and(eq(tripDays.tripId, tripId), eq(tripDays.id, old.id)));
      await bury(ENTITY.day, old.id);
      changed = true;
    }

    // Blocks
    const blockById = new Map(oldBlocks.map((b) => [b.id, b]));
    for (const incoming of Object.values(doc.blocks)) {
      const old = blockById.get(incoming.id);
      blockById.delete(incoming.id);
      let block = incoming;
      if (old && stale && old.rev > base) {
        block = { ...incoming, status: old.status };
        if (incoming.dayId === old.dayId && old.startMinute !== null) block.startMinute = old.startMinute;
      }
      if (old && same(block, old)) continue;
      if (!old && deletedElsewhere.has(ENTITY.block + ":" + block.id)) continue;
      await tx
        .insert(timelineBlocks)
        .values({ ...block, ...stamp })
        .onConflictDoUpdate({ target: [timelineBlocks.tripId, timelineBlocks.id], set: { ...block, ...stamp } });
      if (!old) await unbury(ENTITY.block, block.id);
      changed = true;
    }
    for (const old of blockById.values()) {
      if (stale && old.rev > base) continue;
      await tx.delete(timelineBlocks).where(and(eq(timelineBlocks.tripId, tripId), eq(timelineBlocks.id, old.id)));
      await bury(ENTITY.block, old.id);
      changed = true;
    }

    // Items
    const itemById = new Map(oldItems.map((i) => [i.id, i]));
    for (const incoming of Object.values(doc.items)) {
      const old = itemById.get(incoming.id);
      itemById.delete(incoming.id);
      const item = old && stale && old.rev > base ? { ...incoming, isChecked: old.isChecked } : incoming;
      if (old && same(item, old)) continue;
      if (!old && deletedElsewhere.has(ENTITY.item + ":" + item.id)) continue;
      await tx
        .insert(contextualItems)
        .values({ ...item, ...stamp })
        .onConflictDoUpdate({ target: [contextualItems.tripId, contextualItems.id], set: { ...item, ...stamp } });
      if (!old) await unbury(ENTITY.item, item.id);
      changed = true;
    }
    for (const old of itemById.values()) {
      if (stale && old.rev > base) continue;
      await tx.delete(contextualItems).where(and(eq(contextualItems.tripId, tripId), eq(contextualItems.id, old.id)));
      await bury(ENTITY.item, old.id);
      changed = true;
    }

    const metaChanged = !same(meta, trip);
    if (metaChanged) changed = true;
    if (changed) {
      [trip] = await tx
        .update(trips)
        .set({ ...meta, rev, metaRev: metaChanged ? rev : trip.metaRev, updatedAt: now })
        .where(eq(trips.id, tripId))
        .returning();
    }
    return { rev: trip.rev, merged: stale ? await readDoc(tx, trip) : null };
  });
}
