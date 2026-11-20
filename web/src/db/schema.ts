import { blob, index, integer, primaryKey, real, sqliteTable, text } from "drizzle-orm/sqlite-core";

export const users = sqliteTable("users", {
  id: text("id").primaryKey(),
  email: text("email").notNull().unique(),
  name: text("name").notNull(),
  passwordHash: text("password_hash").notNull(),
  role: text("role", { enum: ["ADMIN", "USER"] }).notNull().default("USER"),
  disabled: integer("disabled", { mode: "boolean" }).notNull().default(false),
  createdAt: integer("created_at").notNull(),
});

// One active trip per user. `rev` is the sync cursor: every write stamps the new value on the rows it changed.
export const trips = sqliteTable("trips", {
  id: text("id").primaryKey(),
  ownerId: text("owner_id").notNull().unique().references(() => users.id, { onDelete: "cascade" }),
  title: text("title").notNull(),
  destinationLabel: text("destination_label").notNull(),
  destinationTimezone: text("destination_timezone").notNull(),
  homeCurrency: text("home_currency").notNull(),
  localCurrency: text("local_currency").notNull(),
  exchangeRate: real("exchange_rate").notNull(),
  startDate: text("start_date").notNull(),
  endDate: text("end_date").notNull(),
  wakeMinute: integer("wake_minute").notNull().default(450),
  rev: integer("rev").notNull().default(0),
  metaRev: integer("meta_rev").notNull().default(0),
  updatedAt: integer("updated_at").notNull(),
});

const tripId = () => text("trip_id").notNull().references(() => trips.id, { onDelete: "cascade" });

export const tripDays = sqliteTable(
  "trip_days",
  {
    tripId: tripId(),
    id: text("id").notNull(),
    dayNumber: integer("day_number").notNull(),
    dateIso: text("date_iso").notNull(),
    timezone: text("timezone").notNull(),
    rev: integer("rev").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.tripId, t.id] }), index("trip_days_rev").on(t.tripId, t.rev)],
);

export const timelineBlocks = sqliteTable(
  "timeline_blocks",
  {
    tripId: tripId(),
    id: text("id").notNull(),
    dayId: text("day_id"),
    sortOrder: integer("sort_order").notNull(),
    startMinute: integer("start_minute"),
    durationMinutes: integer("duration_minutes").notNull(),
    isFixedTime: integer("is_fixed_time", { mode: "boolean" }).notNull(),
    blockType: text("block_type", { enum: ["ACTIVITY", "TRANSIT", "HOTEL", "FOOD_STOP"] }).notNull(),
    title: text("title").notNull(),
    subtitleOrTip: text("subtitle_or_tip"),
    lat: real("lat"),
    lng: real("lng"),
    googlePlaceId: text("google_place_id"),
    googleMapsUri: text("google_maps_uri"),
    estimatedCostLocal: real("estimated_cost_local"),
    bookingRef: text("booking_ref"),
    opensMinute: integer("opens_minute"),
    closesMinute: integer("closes_minute"),
    localScriptName: text("local_script_name"),
    phoneticPronunciation: text("phonetic_pronunciation"),
    status: text("status", { enum: ["PENDING", "DONE", "SKIPPED"] }).notNull(),
    rev: integer("rev").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.tripId, t.id] }), index("timeline_blocks_rev").on(t.tripId, t.rev)],
);

export const contextualItems = sqliteTable(
  "contextual_items",
  {
    tripId: tripId(),
    id: text("id").notNull(),
    linkedBlockId: text("linked_block_id"),
    linkSource: text("link_source", { enum: ["AUTO", "MANUAL"] }),
    dayId: text("day_id"),
    sortOrder: integer("sort_order").notNull(),
    category: text("category", { enum: ["TO_BUY", "EAT_SUGGESTION", "MORNING_PREP"] }).notNull(),
    title: text("title").notNull(),
    shopOrVenueName: text("shop_or_venue_name"),
    priceBenchmarkNote: text("price_benchmark_note"),
    priceSource: text("price_source", { enum: ["AI_ESTIMATE", "USER"] }),
    estimatedCostLocal: real("estimated_cost_local"),
    lat: real("lat"),
    lng: real("lng"),
    geoVerified: integer("geo_verified", { mode: "boolean" }).notNull(),
    googlePlaceId: text("google_place_id"),
    distanceFromBlockMeters: integer("distance_from_block_meters"),
    localScriptText: text("local_script_text"),
    phoneticPronunciation: text("phonetic_pronunciation"),
    isChecked: integer("is_checked", { mode: "boolean" }).notNull(),
    rev: integer("rev").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.tripId, t.id] }), index("contextual_items_rev").on(t.tripId, t.rev)],
);

// A ticket, voucher or booking confirmation attached to a block. The phone downloads it during a pull.
export const attachments = sqliteTable(
  "attachments",
  {
    tripId: tripId(),
    id: text("id").notNull(),
    blockId: text("block_id").notNull(),
    fileName: text("file_name").notNull(),
    mimeType: text("mime_type").notNull(),
    sizeBytes: integer("size_bytes").notNull(),
    sha256: text("sha256").notNull(),
    data: blob("data", { mode: "buffer" }).notNull(),
    rev: integer("rev").notNull(),
    createdAt: integer("created_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.tripId, t.id] }), index("attachments_rev").on(t.tripId, t.rev), index("attachments_block").on(t.tripId, t.blockId)],
);

export const ENTITY = { day: "trip_day", block: "timeline_block", item: "contextual_item", attachment: "attachment" } as const;
export type EntityName = (typeof ENTITY)[keyof typeof ENTITY];

// A deleted row leaves its id here so a phone that still holds it learns to drop it.
export const tombstones = sqliteTable(
  "tombstones",
  {
    tripId: tripId(),
    entity: text("entity").$type<EntityName>().notNull(),
    entityId: text("entity_id").notNull(),
    rev: integer("rev").notNull(),
    deletedAt: integer("deleted_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.tripId, t.entity, t.entityId] }), index("tombstones_rev").on(t.tripId, t.rev)],
);

export const devices = sqliteTable(
  "devices",
  {
    id: text("id").primaryKey(),
    tripId: tripId(),
    userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    installId: text("install_id").notNull(),
    name: text("name").notNull(),
    platform: text("platform").notNull(),
    appVersion: text("app_version").notNull(),
    tokenHash: text("token_hash").notNull().unique(),
    createdAt: integer("created_at").notNull(),
    lastSeenAt: integer("last_seen_at"),
    revokedAt: integer("revoked_at"),
  },
  (t) => [index("devices_user").on(t.userId)],
);

// Only the hash of a pairing code is stored. A code works once and for ten minutes.
export const pairingCodes = sqliteTable("pairing_codes", {
  codeHash: text("code_hash").primaryKey(),
  tripId: tripId(),
  userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
  expiresAt: integer("expires_at").notNull(),
  usedAt: integer("used_at"),
});

export const pairAttempts = sqliteTable(
  "pair_attempts",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    ip: text("ip").notNull(),
    at: integer("at").notNull(),
  },
  (t) => [index("pair_attempts_ip").on(t.ip, t.at)],
);

// Remembered so a batch the phone resends after a lost response is answered `duplicate` and changes nothing.
export const appliedMutations = sqliteTable(
  "applied_mutations",
  {
    tripId: tripId(),
    mutationId: text("mutation_id").notNull(),
    appliedAt: integer("applied_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.tripId, t.mutationId] })],
);

// A link that lets someone upload files to one block without signing in. Only the hash is stored.
export const uploadLinks = sqliteTable("upload_links", {
  tokenHash: text("token_hash").primaryKey(),
  tripId: tripId(),
  blockId: text("block_id").notNull(),
  expiresAt: integer("expires_at").notNull(),
  createdAt: integer("created_at").notNull(),
});

// Secret MCP connector URLs, each able to read and edit one user's trip. Only the hash is stored.
export const connectorKeys = sqliteTable(
  "connector_keys",
  {
    id: text("id").primaryKey(),
    userId: text("user_id").notNull().references(() => users.id, { onDelete: "cascade" }),
    name: text("name").notNull(),
    keyHash: text("key_hash").notNull().unique(),
    createdAt: integer("created_at").notNull(),
    lastUsedAt: integer("last_used_at"),
    revokedAt: integer("revoked_at"),
  },
  (t) => [index("connector_keys_user").on(t.userId)],
);
