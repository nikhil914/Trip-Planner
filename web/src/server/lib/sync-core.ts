import "server-only";
import { createHash, randomBytes, randomInt } from "node:crypto";
import { and, count, eq, gt, isNull, lt } from "drizzle-orm";
import { z } from "zod";
import { getDb, schema } from "@/db/client";
import { ENTITY } from "@/db/schema";

const { trips, tripDays, timelineBlocks, contextualItems, tombstones, devices, pairingCodes, pairAttempts, appliedMutations, users } = schema;

export const CODE_TTL_MS = 10 * 60_000;
const ATTEMPT_WINDOW_MS = 10 * 60_000;
const ATTEMPT_LIMIT = 10;

const sha256 = (value: string) => createHash("sha256").update(value).digest("hex");

/** An HTTP answer the phone understands: a status and a short machine-readable `error`. */
export class SyncError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    readonly retryAfterSeconds?: number,
  ) {
    super(code);
  }
}

export async function createPairingCode(userId: string): Promise<{ code: string; expiresAt: number } | null> {
  const db = await getDb();
  const trip = await db.query.trips.findFirst({ where: eq(trips.ownerId, userId), columns: { id: true } });
  if (!trip) return null;
  const now = Date.now();
  await db.delete(pairingCodes).where(eq(pairingCodes.userId, userId));
  for (let attempt = 0; attempt < 5; attempt++) {
    const code = String(randomInt(0, 1_000_000)).padStart(6, "0");
    const expiresAt = now + CODE_TTL_MS;
    const inserted = await db
      .insert(pairingCodes)
      .values({ codeHash: sha256(code), tripId: trip.id, userId, expiresAt })
      .onConflictDoNothing()
      .returning({ codeHash: pairingCodes.codeHash });
    if (inserted.length > 0) return { code, expiresAt };
  }
  throw new Error("Could not create a pairing code. Try again.");
}

const PairBody = z.object({
  code: z.string().trim().regex(/^\d{6}$/),
  device: z.object({
    install_id: z.string().min(1).max(100),
    name: z.string().max(100).default("Android phone"),
    platform: z.string().max(40).default("android"),
    app_version: z.string().max(40).default(""),
  }),
});

export async function redeemPairingCode(body: unknown, ip: string) {
  const db = await getDb();
  const now = Date.now();

  await db.delete(pairAttempts).where(lt(pairAttempts.at, now - ATTEMPT_WINDOW_MS));
  const [{ recent }] = await db
    .select({ recent: count() })
    .from(pairAttempts)
    .where(and(eq(pairAttempts.ip, ip), gt(pairAttempts.at, now - ATTEMPT_WINDOW_MS)));
  if (recent >= ATTEMPT_LIMIT) throw new SyncError(429, "rate_limited", Math.ceil(ATTEMPT_WINDOW_MS / 1000));
  await db.insert(pairAttempts).values({ ip, at: now });

  const parsed = PairBody.safeParse(body);
  if (!parsed.success) throw new SyncError(404, "invalid_code");
  const { code, device } = parsed.data;

  return db.transaction(async (tx) => {
    const row = await tx.query.pairingCodes.findFirst({ where: eq(pairingCodes.codeHash, sha256(code)) });
    if (!row) throw new SyncError(404, "invalid_code");
    if (row.usedAt !== null || row.expiresAt < now) throw new SyncError(410, "code_expired");
    await tx.update(pairingCodes).set({ usedAt: now }).where(eq(pairingCodes.codeHash, row.codeHash));

    // Pairing the same install again replaces its old token.
    await tx
      .update(devices)
      .set({ revokedAt: now })
      .where(and(eq(devices.tripId, row.tripId), eq(devices.installId, device.install_id), isNull(devices.revokedAt)));

    const token = randomBytes(32).toString("base64url");
    const deviceId = "dev-" + crypto.randomUUID();
    await tx.insert(devices).values({
      id: deviceId,
      tripId: row.tripId,
      userId: row.userId,
      installId: device.install_id,
      name: device.name,
      platform: device.platform,
      appVersion: device.app_version,
      tokenHash: sha256(token),
      createdAt: now,
    });
    return { trip_id: row.tripId, device_id: deviceId, sync_token: token, server_time: now };
  });
}

export async function authenticateDevice(authorization: string | null, tripId: string) {
  const token = /^Bearer\s+(.+)$/i.exec(authorization ?? "")?.[1];
  if (!token) throw new SyncError(401, "token_missing");
  const db = await getDb();
  const [row] = await db
    .select({ device: devices, disabled: users.disabled })
    .from(devices)
    .innerJoin(users, eq(users.id, devices.userId))
    .where(eq(devices.tokenHash, sha256(token)));
  if (!row || row.device.revokedAt !== null || row.disabled) throw new SyncError(401, "token_revoked");
  if (row.device.tripId !== tripId) throw new SyncError(403, "wrong_trip");
  await db.update(devices).set({ lastSeenAt: Date.now() }).where(eq(devices.id, row.device.id));
  return row.device;
}

export async function pull(tripId: string, since: number) {
  const db = await getDb();
  const trip = await db.query.trips.findFirst({ where: eq(trips.id, tripId) });
  if (!trip) throw new SyncError(404, "trip_not_found");
  // A cursor ahead of the server means the phone was paired against other data. Send everything.
  const full = since <= 0 || since > trip.rev;
  const after = full ? -1 : since;

  const [days, blocks, items, tombs] = await Promise.all([
    db.select().from(tripDays).where(and(eq(tripDays.tripId, tripId), gt(tripDays.rev, after))),
    db.select().from(timelineBlocks).where(and(eq(timelineBlocks.tripId, tripId), gt(timelineBlocks.rev, after))),
    db.select().from(contextualItems).where(and(eq(contextualItems.tripId, tripId), gt(contextualItems.rev, after))),
    full ? [] : db.select().from(tombstones).where(and(eq(tombstones.tripId, tripId), gt(tombstones.rev, after))),
  ]);
  const deleted = (entity: string) => tombs.filter((t) => t.entity === entity).map((t) => t.entityId);

  return {
    trip_id: tripId,
    rev: trip.rev,
    full,
    server_time: Date.now(),
    trip:
      full || trip.metaRev > since
        ? {
            id: trip.id,
            title: trip.title,
            destination_timezone: trip.destinationTimezone,
            home_currency: trip.homeCurrency,
            local_currency: trip.localCurrency,
            exchange_rate: trip.exchangeRate,
            start_date: trip.startDate,
            end_date: trip.endDate,
            wake_minute: trip.wakeMinute,
            updated_at: trip.updatedAt,
          }
        : null,
    trip_days: days.map((d) => ({
      id: d.id,
      rev: d.rev,
      day_number: d.dayNumber,
      date_iso: d.dateIso,
      timezone: d.timezone,
      morning_prep_notes: null,
      weather_summary_cache: null,
      updated_at: d.updatedAt,
    })),
    timeline_blocks: blocks.map((b) => ({
      id: b.id,
      rev: b.rev,
      day_id: b.dayId,
      sort_order: b.sortOrder,
      start_minute: b.startMinute,
      duration_minutes: b.durationMinutes,
      is_fixed_time: b.isFixedTime,
      block_type: b.blockType,
      title: b.title,
      subtitle_or_tip: b.subtitleOrTip,
      lat: b.lat,
      lng: b.lng,
      google_place_id: b.googlePlaceId,
      google_maps_uri: b.googleMapsUri,
      estimated_cost_local: b.estimatedCostLocal,
      booking_ref: b.bookingRef,
      opens_minute: b.opensMinute,
      closes_minute: b.closesMinute,
      end_timezone: null,
      local_script_name: b.localScriptName,
      phonetic_pronunciation: b.phoneticPronunciation,
      status: b.status,
      updated_at: b.updatedAt,
    })),
    contextual_items: items.map((i) => ({
      id: i.id,
      rev: i.rev,
      linked_block_id: i.linkedBlockId,
      day_id: i.dayId,
      category: i.category,
      title: i.title,
      shop_or_venue_name: i.shopOrVenueName,
      price_benchmark_note: i.priceBenchmarkNote,
      estimated_cost_local: i.estimatedCostLocal,
      lat: i.lat,
      lng: i.lng,
      google_place_id: i.googlePlaceId,
      distance_from_block_meters: i.distanceFromBlockMeters,
      local_script_text: i.localScriptText,
      phonetic_pronunciation: i.phoneticPronunciation,
      is_checked: i.isChecked,
      updated_at: i.updatedAt,
    })),
    attachments: [],
    deleted: {
      trip_days: deleted(ENTITY.day),
      timeline_blocks: deleted(ENTITY.block),
      contextual_items: deleted(ENTITY.item),
      attachments: [],
    },
  };
}

const PushBody = z.object({
  batch_id: z.string().min(1).max(100),
  mutations: z
    .array(
      z.object({
        mutation_id: z.string().min(1).max(300),
        entity: z.string(),
        id: z.string().min(1).max(80),
        field: z.string(),
        value: z.unknown(),
        base: z.unknown().optional(),
      }),
    )
    .max(500),
});

type MutationStatus = "applied" | "duplicate" | "conflict" | "gone" | "rejected";
interface MutationResult {
  mutation_id: string;
  status: MutationStatus;
  field?: string;
  server_value?: unknown;
}

const Status = z.enum(["PENDING", "DONE", "SKIPPED"]);
const StartMinute = z.number().int().min(0).max(1439);

/**
 * Applies a phone's outbox batch in one transaction. The phone may write `status`, `is_checked` and `start_minute`
 * and nothing else. `start_minute` is compare-and-swap against `base`, so a block the web moved meanwhile is not
 * overwritten: the phone gets the server's value back and adopts it.
 */
export async function push(tripId: string, body: unknown) {
  const parsed = PushBody.safeParse(body);
  if (!parsed.success) throw new SyncError(400, "invalid_batch");
  const db = await getDb();

  return db.transaction(async (tx) => {
    const trip = await tx.query.trips.findFirst({ where: eq(trips.id, tripId), columns: { rev: true } });
    if (!trip) throw new SyncError(404, "trip_not_found");
    const rev = trip.rev + 1;
    const now = Date.now();
    const stamp = { rev, updatedAt: now };
    const results: MutationResult[] = [];
    let applied = false;

    for (const m of parsed.data.mutations) {
      const answer = (status: MutationStatus, extra: Partial<MutationResult> = {}) => results.push({ mutation_id: m.mutation_id, status, ...extra });

      const seen = await tx.query.appliedMutations.findFirst({
        where: and(eq(appliedMutations.tripId, tripId), eq(appliedMutations.mutationId, m.mutation_id)),
      });
      if (seen) {
        answer("duplicate");
        continue;
      }

      let status: MutationStatus = "rejected";
      let serverValue: unknown;

      if (m.entity === ENTITY.block && (m.field === "status" || m.field === "start_minute")) {
        const where = and(eq(timelineBlocks.tripId, tripId), eq(timelineBlocks.id, m.id));
        const block = await tx.query.timelineBlocks.findFirst({ where });
        if (!block) {
          status = "gone";
        } else if (m.field === "status") {
          const value = Status.safeParse(m.value);
          if (value.success) {
            await tx.update(timelineBlocks).set({ status: value.data, ...stamp }).where(where);
            status = "applied";
          }
        } else {
          const value = StartMinute.safeParse(m.value);
          if (value.success) {
            if (block.dayId !== null && block.startMinute === (m.base ?? null)) {
              await tx.update(timelineBlocks).set({ startMinute: value.data, ...stamp }).where(where);
              status = "applied";
            } else {
              status = "conflict";
              serverValue = block.startMinute;
            }
          }
        }
      } else if (m.entity === ENTITY.item && m.field === "is_checked") {
        const where = and(eq(contextualItems.tripId, tripId), eq(contextualItems.id, m.id));
        const item = await tx.query.contextualItems.findFirst({ where, columns: { id: true } });
        if (!item) {
          status = "gone";
        } else if (typeof m.value === "boolean") {
          await tx.update(contextualItems).set({ isChecked: m.value, ...stamp }).where(where);
          status = "applied";
        }
      }

      if (status === "applied") applied = true;
      await tx.insert(appliedMutations).values({ tripId, mutationId: m.mutation_id, appliedAt: now });
      answer(status, status === "conflict" ? { field: m.field, server_value: serverValue } : {});
    }

    if (applied) await tx.update(trips).set(stamp).where(eq(trips.id, tripId));
    return { rev: applied ? rev : trip.rev, results };
  });
}

export async function listDevices(userId: string) {
  const db = await getDb();
  const rows = await db.select().from(devices).where(and(eq(devices.userId, userId), isNull(devices.revokedAt))).orderBy(devices.createdAt);
  return rows.map((d) => ({ id: d.id, name: d.name, appVersion: d.appVersion, createdAt: d.createdAt, lastSeenAt: d.lastSeenAt }));
}

export async function revokeDevice(userId: string, deviceId: string): Promise<void> {
  const db = await getDb();
  await db.update(devices).set({ revokedAt: Date.now() }).where(and(eq(devices.id, deviceId), eq(devices.userId, userId)));
}
