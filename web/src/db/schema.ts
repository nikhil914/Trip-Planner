import { index, integer, primaryKey, real, sqliteTable, text } from "drizzle-orm/sqlite-core";
import { encBlob, encLookupText, encReal, encText } from "./field-crypto";

// Columns typed enc* are encrypted before they reach the database (see field-crypto.ts).

export const users = sqliteTable("users", {
  id: text("id").primaryKey(),
  email: encLookupText("email").notNull().unique(),
  name: encText("name").notNull(),
  passwordHash: text("password_hash").notNull(),
  role: text("role", { enum: ["ADMIN", "USER"] }).notNull().default("USER"),
  disabled: integer("disabled", { mode: "boolean" }).notNull().default(false),
  createdAt: integer("created_at").notNull(),
  // Raising this signs the user out on every browser: a session cookie carries the value it was issued with.
  sessionVersion: integer("session_version").notNull().default(0),
  // Two-factor sign-in with an authenticator app. The secret is set first and counts once totp_enabled_at is set.
  totpSecret: encText("totp_secret"),
  totpEnabledAt: integer("totp_enabled_at"),
  // The last 30-second step a code was accepted for, so a code cannot be used twice.
  totpLastStep: integer("totp_last_step"),
});

// One active trip per user. `rev` is the sync cursor: every write stamps the new value on the rows it changed.
export const trips = sqliteTable("trips", {
  id: text("id").primaryKey(),
  ownerId: text("owner_id").notNull().unique().references(() => users.id, { onDelete: "cascade" }),
  title: encText("title").notNull(),
  destinationLabel: encText("destination_label").notNull(),
  destinationTimezone: text("destination_timezone").notNull(),
  homeCurrency: text("home_currency").notNull(),
  localCurrency: text("local_currency").notNull(),
  exchangeRate: real("exchange_rate").notNull(),
  startDate: text("start_date").notNull(),
  endDate: text("end_date").notNull(),
  wakeMinute: integer("wake_minute").notNull().default(450),
  // Trip budget in minor units of the local currency (4800 = 48.00). Set from the phone.
  budgetMinor: integer("budget_minor"),
  // Off: each phone sees only the spends it logged. On: both travellers see every spend.
  sharedLedger: integer("shared_ledger", { mode: "boolean" }).notNull().default(false),
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
    title: encText("title").notNull(),
    subtitleOrTip: encText("subtitle_or_tip"),
    lat: encReal("lat"),
    lng: encReal("lng"),
    googlePlaceId: encText("google_place_id"),
    googleMapsUri: encText("google_maps_uri"),
    estimatedCostLocal: real("estimated_cost_local"),
    bookingRef: encText("booking_ref"),
    opensMinute: integer("opens_minute"),
    closesMinute: integer("closes_minute"),
    localScriptName: encText("local_script_name"),
    phoneticPronunciation: encText("phonetic_pronunciation"),
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
    title: encText("title").notNull(),
    shopOrVenueName: encText("shop_or_venue_name"),
    priceBenchmarkNote: encText("price_benchmark_note"),
    priceSource: text("price_source", { enum: ["AI_ESTIMATE", "USER"] }),
    estimatedCostLocal: real("estimated_cost_local"),
    lat: encReal("lat"),
    lng: encReal("lng"),
    geoVerified: integer("geo_verified", { mode: "boolean" }).notNull(),
    googlePlaceId: encText("google_place_id"),
    distanceFromBlockMeters: integer("distance_from_block_meters"),
    localScriptText: encText("local_script_text"),
    phoneticPronunciation: encText("phonetic_pronunciation"),
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
    fileName: encText("file_name").notNull(),
    mimeType: text("mime_type").notNull(),
    sizeBytes: integer("size_bytes").notNull(),
    sha256: text("sha256").notNull(),
    data: encBlob("data").notNull(),
    rev: integer("rev").notNull(),
    createdAt: integer("created_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.tripId, t.id] }), index("attachments_rev").on(t.tripId, t.rev), index("attachments_block").on(t.tripId, t.blockId)],
);

// Money logged on a phone: spends, ATM withdrawals, exchanges and cash counts. Only kind SPEND counts as spend.
// The phone makes the id. `owner` is the install id of the phone that logged it; only that phone can change it.
export const expenses = sqliteTable(
  "expenses",
  {
    tripId: tripId(),
    id: text("id").notNull(),
    owner: text("owner").notNull(),
    kind: text("kind", { enum: ["SPEND", "ATM", "EXCHANGE", "OPENING_CASH", "CASH_COUNT"] }).notNull(),
    dayId: text("day_id"),
    blockId: text("block_id"),
    occurredAt: integer("occurred_at").notNull(),
    amountMinor: integer("amount_minor").notNull(),
    currency: text("currency").notNull(),
    rateToHome: real("rate_to_home").notNull(),
    homeMinor: integer("home_minor").notNull(),
    billedHomeMinor: integer("billed_home_minor"),
    method: text("method", { enum: ["CASH", "CARD", "OTHER"] }).notNull(),
    category: text("category").notNull(),
    merchant: encText("merchant"),
    note: encText("note"),
    prepaid: integer("prepaid", { mode: "boolean" }).notNull(),
    paidBy: text("paid_by"),
    clientUpdatedAt: integer("client_updated_at").notNull(),
    deletedAt: integer("deleted_at"),
    rev: integer("rev").notNull(),
    updatedAt: integer("updated_at").notNull(),
  },
  (t) => [primaryKey({ columns: [t.tripId, t.id] }), index("expenses_rev").on(t.tripId, t.rev)],
);

export const ENTITY = {
  day: "trip_day",
  block: "timeline_block",
  item: "contextual_item",
  attachment: "attachment",
  trip: "trip",
  expense: "expense",
} as const;
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
    name: encText("name").notNull(),
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
    name: encText("name").notNull(),
    keyHash: text("key_hash").notNull().unique(),
    createdAt: integer("created_at").notNull(),
    lastUsedAt: integer("last_used_at"),
    revokedAt: integer("revoked_at"),
    // Links stop working after this time. Null on links made before expiry existed: they expire 90 days after creation.
    expiresAt: integer("expires_at"),
  },
  (t) => [index("connector_keys_user").on(t.userId)],
);

// Sign-up is by invitation. An admin creates a link; it works once, until it expires. Only the hash is stored.
export const invites = sqliteTable("invites", {
  tokenHash: text("token_hash").primaryKey(),
  createdBy: text("created_by").notNull().references(() => users.id, { onDelete: "cascade" }),
  note: encText("note").notNull(),
  role: text("role", { enum: ["ADMIN", "USER"] }).notNull().default("USER"),
  createdAt: integer("created_at").notNull(),
  expiresAt: integer("expires_at").notNull(),
  usedAt: integer("used_at"),
  usedBy: text("used_by").references(() => users.id, { onDelete: "set null" }),
});

// Rate-limit events. Keys: "ip:<address>" for failed sign-ins, "mcp:<connector key id>" for connector calls.
export const loginFailures = sqliteTable(
  "login_failures",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    key: text("key").notNull(),
    at: integer("at").notNull(),
  },
  (t) => [index("login_failures_key").on(t.key, t.at)],
);

// Security events: sign-ins, blocked attempts, new phones, new connector links, 2FA changes. Shown on /security and /admin.
export const auditEvents = sqliteTable(
  "audit_events",
  {
    id: integer("id").primaryKey({ autoIncrement: true }),
    userId: text("user_id").references(() => users.id, { onDelete: "cascade" }),
    kind: text("kind").notNull(),
    detail: encText("detail"),
    ip: encText("ip"),
    at: integer("at").notNull(),
  },
  (t) => [index("audit_events_user").on(t.userId, t.at), index("audit_events_at").on(t.at)],
);
