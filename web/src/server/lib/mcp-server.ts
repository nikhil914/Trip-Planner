import "server-only";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { relink } from "@/lib/linker";
import { blankBlock } from "@/lib/sample";
import { addDays, hhmm } from "@/lib/time";
import type { Block, BlockType, TripDoc } from "@/lib/types";
import { AttachmentError, createUploadLink, deleteAttachment, listAttachments, tripIdOf, UPLOAD_LINK_TTL_MS } from "./attachments";
import { resolveOne } from "./places-core";
import { loadTrip, saveTrip, TripDocSchema } from "./trip-repo";

/** A message returned to the caller as is. Anything else is logged and reported as a generic failure. */
class ToolError extends Error {}

const SORT_GAP = 1000;
const DEFAULT_DURATION: Record<BlockType, number> = { TRANSIT: 120, HOTEL: 30, FOOD_STOP: 60, ACTIVITY: 90 };

const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Use YYYY-MM-DD.");
const clock = z.string().regex(/^([01]?\d|2[0-3]):[0-5]\d$/, "Use 24-hour HH:MM.");
const toMinute = (value: string) => {
  const [h, m] = value.split(":").map(Number);
  return h * 60 + m;
};

function isTimezone(tz: string): boolean {
  try {
    new Intl.DateTimeFormat("en", { timeZone: tz });
    return true;
  } catch {
    return false;
  }
}

const daysBetween = (from: string, to: string) => Math.round((Date.parse(to + "T00:00:00Z") - Date.parse(from + "T00:00:00Z")) / 86_400_000);

const ok = (value: unknown) => ({ content: [{ type: "text" as const, text: JSON.stringify(value, null, 2) }] });
const fail = (message: string) => ({ content: [{ type: "text" as const, text: message }], isError: true });

async function run(task: () => Promise<unknown>) {
  try {
    return ok(await task());
  } catch (e) {
    if (e instanceof ToolError || e instanceof AttachmentError) return fail(e.message);
    console.error("connector tool failed", e);
    return fail("The trip planner hit an error. Try again in a moment.");
  }
}

/** Loads the trip, applies one change and saves it at the revision it was read at. */
async function mutate<T>(userId: string, change: (doc: TripDoc) => { doc: TripDoc; result: T }): Promise<T> {
  const loaded = await loadTrip(userId);
  if (!loaded) throw new ToolError("There is no trip yet. Call set_trip with a title, dates and timezone first.");
  const { doc, result } = change(structuredClone(loaded.doc));
  const parsed = TripDocSchema.safeParse(relink(doc));
  if (!parsed.success) {
    const issue = parsed.error.issues[0];
    throw new ToolError(`That change is not valid: ${issue?.path.join(".")} ${issue?.message ?? ""}`.trim());
  }
  await saveTrip(userId, parsed.data, loaded.rev);
  return result;
}

const blockFields = {
  type: z
    .enum(["ACTIVITY", "TRANSIT", "HOTEL", "FOOD_STOP"])
    .describe("TRANSIT for flights, trains, buses, ferries and transfers. HOTEL for check-in or check-out. FOOD_STOP for restaurant bookings. ACTIVITY for tours, tickets and anything else."),
  title: z.string().min(1).max(200).describe('Short title shown on the card, such as "Flight 6E 1801 DEL → TBS" or "Check in: Hotel Rustaveli".'),
  date: isoDate.describe("Local date of the start, YYYY-MM-DD. It must fall inside the trip; extend the trip with set_trip first if needed."),
  start_time: clock.describe("Local 24-hour start time, HH:MM, in that day's timezone (departure time for transport)."),
  duration_minutes: z.number().int().min(5).max(1440).describe("How long it takes. Defaults: transport 120, hotel 30, food 60, activity 90."),
  end_time: clock.describe("Local end time on the same day, HH:MM. Use instead of duration_minutes."),
  booking_ref: z.string().max(200).describe("Booking reference, PNR or confirmation number."),
  notes: z
    .string()
    .max(2000)
    .describe("Details the traveller needs on the day: terminal, seat, platform, address, check-in rules, arrival time if it is on another day, phone numbers."),
  cost: z.number().min(0).max(1e9).describe("Price in the trip's local currency."),
  place: z
    .string()
    .max(200)
    .describe('Name and city to look up on the map, such as "Hotel Rustaveli, Tbilisi" or "Tbilisi International Airport". Sets the map pin used for navigation.'),
  local_name: z.string().max(200).describe("The place's name in the local script, for showing to a driver."),
  pronunciation: z.string().max(200).describe("How to pronounce the local name."),
  fixed_time: z.boolean().describe("True when the time cannot move (default for bookings). The phone's delay shift skips fixed blocks."),
};

type BlockInput = Partial<{ [K in keyof typeof blockFields]: z.infer<(typeof blockFields)[K]> }>;

/** Turns tool input into block fields. Collects notes about anything it adjusted. */
async function applyInput(doc: TripDoc, block: Block, input: BlockInput, notes: string[]): Promise<Block> {
  const next: Block = { ...block };
  if (input.type) next.blockType = input.type;
  if (input.title !== undefined) next.title = input.title.trim();
  if (input.booking_ref !== undefined) next.bookingRef = input.booking_ref.trim() || null;
  if (input.notes !== undefined) next.subtitleOrTip = input.notes.trim() || null;
  if (input.cost !== undefined) next.estimatedCostLocal = input.cost;
  if (input.local_name !== undefined) next.localScriptName = input.local_name.trim() || null;
  if (input.pronunciation !== undefined) next.phoneticPronunciation = input.pronunciation.trim() || null;
  if (input.fixed_time !== undefined) next.isFixedTime = input.fixed_time;

  if (input.place) {
    const hit = await resolveOne({ query: input.place, limit: 1 });
    if (hit) {
      Object.assign(next, { lat: hit.lat, lng: hit.lng, googlePlaceId: hit.id, googleMapsUri: hit.googleMapsUri });
      notes.push(`Map pin set to ${hit.displayName}${hit.address ? ", " + hit.address : ""}. Check it is the right place.`);
    } else {
      notes.push(`No map match for "${input.place}". The block has no pin; try a more specific place name.`);
    }
  }

  if (input.date !== undefined) {
    const day = doc.days.find((d) => d.dateIso === input.date);
    if (!day) {
      const first = doc.days[0]?.dateIso;
      const last = doc.days.at(-1)?.dateIso;
      throw new ToolError(`${input.date} is outside the trip (${first} to ${last}). Extend the trip with set_trip, then try again.`);
    }
    next.dayId = day.id;
  }
  if (input.start_time !== undefined) {
    if (next.dayId === null) throw new ToolError("Give a date with start_time so the block lands on a day.");
    next.startMinute = toMinute(input.start_time);
  }
  if (next.dayId !== null && next.startMinute === null) throw new ToolError("Give start_time with the date.");

  if (input.end_time !== undefined) {
    if (next.startMinute === null) throw new ToolError("end_time needs a start_time.");
    const end = toMinute(input.end_time);
    if (end <= next.startMinute) throw new ToolError("end_time is before start_time. For something that ends the next day, use duration_minutes and mention the arrival in notes.");
    next.durationMinutes = end - next.startMinute;
  } else if (input.duration_minutes !== undefined) {
    next.durationMinutes = input.duration_minutes;
  }

  if (next.startMinute !== null) {
    if (next.startMinute > 1435) next.startMinute = 1435;
    const room = 1440 - next.startMinute;
    if (next.durationMinutes > room) {
      notes.push(`The board shows one day per column, so the block was cut at midnight (${next.durationMinutes} → ${room} minutes). Put the arrival time in notes.`);
      next.durationMinutes = Math.max(5, room);
    }
  }
  return next;
}

function describeBlock(doc: TripDoc, b: Block, files: Map<string, { id: string; name: string }[]>) {
  const day = doc.days.find((d) => d.id === b.dayId);
  return {
    id: b.id,
    type: b.blockType,
    title: b.title,
    date: day?.dateIso ?? null,
    start_time: b.startMinute === null ? null : hhmm(b.startMinute),
    end_time: b.startMinute === null ? null : hhmm(b.startMinute + b.durationMinutes),
    duration_minutes: b.durationMinutes,
    fixed_time: b.isFixedTime,
    booking_ref: b.bookingRef,
    notes: b.subtitleOrTip,
    cost: b.estimatedCostLocal,
    has_map_pin: b.lat !== null && b.lng !== null,
    local_name: b.localScriptName,
    status: b.status,
    files: files.get(b.id) ?? [],
  };
}

function clashes(doc: TripDoc, b: Block): string[] {
  if (b.dayId === null || b.startMinute === null) return [];
  const end = b.startMinute + b.durationMinutes;
  return Object.values(doc.blocks)
    .filter((o) => o.id !== b.id && o.dayId === b.dayId && o.startMinute !== null && o.startMinute < end && b.startMinute! < o.startMinute + o.durationMinutes)
    .map((o) => `${o.title} (${hhmm(o.startMinute!)}–${hhmm(o.startMinute! + o.durationMinutes)}, id ${o.id})`);
}

/** Builds days for a date range. Days that keep their date keep their id, so their bookings stay on the same date. */
function setRange(doc: TripDoc, start: string, end: string, timezone: string): TripDoc {
  const count = daysBetween(start, end) + 1;
  if (count < 1) throw new ToolError("end_date is before start_date.");
  if (count > 60) throw new ToolError("A trip can be at most 60 days.");
  const byDate = new Map(doc.days.map((d) => [d.dateIso, d]));
  const days = Array.from({ length: count }, (_, i) => {
    const dateIso = addDays(start, i);
    const old = byDate.get(dateIso);
    return { id: old?.id ?? `day-${crypto.randomUUID()}`, dayNumber: i + 1, dateIso, timezone: old?.timezone ?? timezone };
  });
  const kept = new Set(days.map((d) => d.id));
  const blocks = { ...doc.blocks };
  for (const b of Object.values(blocks)) if (b.dayId && !kept.has(b.dayId)) blocks[b.id] = { ...b, dayId: null, startMinute: null };
  const items = { ...doc.items };
  for (const i of Object.values(items)) if (i.category === "MORNING_PREP" && i.dayId && !kept.has(i.dayId)) delete items[i.id];
  return { trip: { ...doc.trip, startDate: start, endDate: end }, days, blocks, items };
}

const INSTRUCTIONS = `This connects to the user's trip planner. Their one active trip is laid out as days with timed blocks; a paired Android phone shows it offline during the trip.
When the user shares bookings (flights, trains, hotels, tours, restaurant reservations), call get_trip first, extend the dates with set_trip if a booking falls outside them, then add one block per booking with add_booking.
Times are local wall-clock times at the place where the booking starts. For a hotel stay add a HOTEL block at check-in on the arrival date and another at check-out on the departure date.
Put everything the traveller needs on the day (booking reference, terminal, seat, address, rules) into booking_ref and notes. Use update_block instead of adding duplicates.
You cannot attach files yourself. To store a ticket PDF or image on a block, call create_upload_link and give the user the link to open and pick the file.`;

export function buildMcpServer(userId: string, origin: string): McpServer {
  const server = new McpServer({ name: "trip-planner", version: "1.0.0" }, { instructions: INSTRUCTIONS });

  server.registerTool(
    "get_trip",
    {
      title: "Read the trip",
      description: "Returns the trip's dates, currencies and every block by day with its id, time, booking details and attached files. Call this before changing anything.",
      annotations: { readOnlyHint: true },
    },
    () =>
      run(async () => {
        const loaded = await loadTrip(userId);
        if (!loaded) return { trip: null, hint: "No trip yet. Create one with set_trip.", planner_url: origin };
        const { doc } = loaded;
        const tripId = await tripIdOf(userId);
        const files = new Map<string, { id: string; name: string }[]>();
        for (const f of tripId ? await listAttachments(tripId) : []) {
          files.set(f.blockId, [...(files.get(f.blockId) ?? []), { id: f.id, name: f.fileName }]);
        }
        const blocks = Object.values(doc.blocks);
        const byTime = (a: Block, b: Block) => (a.startMinute ?? 0) - (b.startMinute ?? 0);
        return {
          trip: {
            title: doc.trip.title,
            destination: doc.trip.destinationLabel,
            timezone: doc.trip.destinationTimezone,
            start_date: doc.trip.startDate,
            end_date: doc.trip.endDate,
            local_currency: doc.trip.localCurrency,
            home_currency: doc.trip.homeCurrency,
            home_per_local: doc.trip.exchangeRate,
          },
          days: doc.days.map((d) => ({
            day_number: d.dayNumber,
            date: d.dateIso,
            timezone: d.timezone,
            blocks: blocks.filter((b) => b.dayId === d.id).sort(byTime).map((b) => describeBlock(doc, b, files)),
          })),
          unscheduled: blocks.filter((b) => b.dayId === null).sort((a, b) => a.sortOrder - b.sortOrder).map((b) => describeBlock(doc, b, files)),
          planner_url: origin,
        };
      }),
  );

  server.registerTool(
    "set_trip",
    {
      title: "Create or change the trip",
      description:
        "Creates the trip if there is none, or changes its title, destination, dates, timezone or currencies. Changing dates keeps every booking on its calendar date; bookings on dates that fall outside the new range move to the unscheduled list.",
      inputSchema: {
        title: z.string().min(1).max(200).optional(),
        destination: z.string().max(200).optional().describe('City and country, such as "Tbilisi, Georgia".'),
        start_date: isoDate.optional(),
        end_date: isoDate.optional(),
        timezone: z.string().max(64).optional().describe("IANA timezone for new days, such as Asia/Tbilisi."),
        local_currency: z.string().length(3).optional().describe("ISO code of the destination's currency."),
        home_currency: z.string().length(3).optional().describe("ISO code of the traveller's own currency."),
        home_per_local: z.number().positive().max(1e9).optional().describe("How much 1 unit of local currency costs in home currency."),
      },
      annotations: { idempotentHint: true },
    },
    (input) =>
      run(async () => {
        if (input.timezone !== undefined && !isTimezone(input.timezone)) throw new ToolError(`${input.timezone} is not an IANA timezone name.`);
        const meta = {
          ...(input.title !== undefined && { title: input.title.trim() }),
          ...(input.destination !== undefined && { destinationLabel: input.destination.trim() }),
          ...(input.timezone !== undefined && { destinationTimezone: input.timezone }),
          ...(input.local_currency !== undefined && { localCurrency: input.local_currency.toUpperCase() }),
          ...(input.home_currency !== undefined && { homeCurrency: input.home_currency.toUpperCase() }),
          ...(input.home_per_local !== undefined && { exchangeRate: input.home_per_local }),
        };
        const loaded = await loadTrip(userId);
        if (!loaded) {
          if (!input.title || !input.start_date || !input.end_date || !input.timezone) {
            throw new ToolError("To create the trip, give title, start_date, end_date and timezone.");
          }
          const blank: TripDoc = {
            trip: {
              id: "",
              title: input.title,
              destinationLabel: "",
              destinationTimezone: input.timezone,
              homeCurrency: "USD",
              localCurrency: "USD",
              exchangeRate: 1,
              startDate: input.start_date,
              endDate: input.end_date,
              ...meta,
            },
            days: [],
            blocks: {},
            items: {},
          };
          const doc = setRange(blank, input.start_date, input.end_date, input.timezone);
          const parsed = TripDocSchema.safeParse(doc);
          if (!parsed.success) throw new ToolError("That trip is not valid: " + (parsed.error.issues[0]?.message ?? ""));
          await saveTrip(userId, parsed.data, null);
          return { created: true, start_date: input.start_date, end_date: input.end_date, days: doc.days.length };
        }
        return mutate(userId, (doc) => {
          let next: TripDoc = { ...doc, trip: { ...doc.trip, ...meta } };
          const start = input.start_date ?? doc.trip.startDate;
          const end = input.end_date ?? doc.trip.endDate;
          const before = new Set(Object.values(doc.blocks).filter((b) => b.dayId === null).map((b) => b.id));
          if (start !== doc.trip.startDate || end !== doc.trip.endDate) next = setRange(next, start, end, next.trip.destinationTimezone);
          const unscheduled = Object.values(next.blocks).filter((b) => b.dayId === null && !before.has(b.id)).map((b) => b.title);
          return {
            doc: next,
            result: { updated: true, start_date: start, end_date: end, days: next.days.length, moved_to_unscheduled: unscheduled },
          };
        });
      }),
  );

  server.registerTool(
    "add_booking",
    {
      title: "Add a booking",
      description:
        "Adds one block to the trip: a flight, train, bus, hotel check-in or check-out, tour, ticket or restaurant booking. Give date and start_time to place it on the timeline; leave both out to put it in the unscheduled list.",
      inputSchema: {
        type: blockFields.type,
        title: blockFields.title,
        date: blockFields.date.optional(),
        start_time: blockFields.start_time.optional(),
        duration_minutes: blockFields.duration_minutes.optional(),
        end_time: blockFields.end_time.optional(),
        booking_ref: blockFields.booking_ref.optional(),
        notes: blockFields.notes.optional(),
        cost: blockFields.cost.optional(),
        place: blockFields.place.optional(),
        local_name: blockFields.local_name.optional(),
        pronunciation: blockFields.pronunciation.optional(),
        fixed_time: blockFields.fixed_time.optional(),
      },
    },
    (input) =>
      run(async () => {
        if ((input.date === undefined) !== (input.start_time === undefined)) throw new ToolError("Give both date and start_time, or neither.");
        const loaded = await loadTrip(userId);
        if (!loaded) throw new ToolError("There is no trip yet. Call set_trip with a title, dates and timezone first.");
        const id = `b-${crypto.randomUUID()}`;
        const notes: string[] = [];
        const lastOrder = Math.max(0, ...Object.values(loaded.doc.blocks).map((b) => b.sortOrder));
        const base: Block = {
          ...blankBlock(id),
          blockType: input.type,
          durationMinutes: DEFAULT_DURATION[input.type],
          isFixedTime: true,
          sortOrder: lastOrder + SORT_GAP,
        };
        const block = await applyInput(loaded.doc, base, input, notes);
        return mutate(userId, (doc) => {
          if (block.dayId && !doc.days.some((d) => d.id === block.dayId)) throw new ToolError("The trip changed meanwhile. Call get_trip and try again.");
          doc.blocks[id] = block;
          const overlaps = clashes(doc, block);
          if (overlaps.length) notes.push("Overlaps: " + overlaps.join("; "));
          return { doc, result: { added: describeBlock(doc, block, new Map()), notes } };
        });
      }),
  );

  server.registerTool(
    "update_block",
    {
      title: "Change a block",
      description:
        "Changes fields of an existing block by id (from get_trip). Only the fields you give change. Give date and start_time to move it, or unschedule: true to take it off the timeline.",
      inputSchema: {
        id: z.string().min(1).max(80),
        unschedule: z.boolean().optional().describe("Moves the block to the unscheduled list."),
        type: blockFields.type.optional(),
        title: blockFields.title.optional(),
        date: blockFields.date.optional(),
        start_time: blockFields.start_time.optional(),
        duration_minutes: blockFields.duration_minutes.optional(),
        end_time: blockFields.end_time.optional(),
        booking_ref: blockFields.booking_ref.optional(),
        notes: blockFields.notes.optional(),
        cost: blockFields.cost.optional(),
        place: blockFields.place.optional(),
        local_name: blockFields.local_name.optional(),
        pronunciation: blockFields.pronunciation.optional(),
        fixed_time: blockFields.fixed_time.optional(),
      },
      annotations: { idempotentHint: true },
    },
    ({ id, unschedule, ...input }) =>
      run(async () => {
        const loaded = await loadTrip(userId);
        const current = loaded?.doc.blocks[id];
        if (!loaded || !current) throw new ToolError(`No block with id ${id}. Call get_trip for current ids.`);
        const notes: string[] = [];
        let block = unschedule ? { ...current, dayId: null, startMinute: null } : current;
        block = await applyInput(loaded.doc, block, unschedule ? { ...input, date: undefined, start_time: undefined } : input, notes);
        const changed = block;
        return mutate(userId, (doc) => {
          const now = doc.blocks[id];
          if (!now) throw new ToolError(`Block ${id} was deleted meanwhile.`);
          // Keep a check-off or delay the phone made while this call ran.
          doc.blocks[id] = { ...changed, status: now.status, startMinute: input.start_time !== undefined || unschedule ? changed.startMinute : now.startMinute };
          const overlaps = clashes(doc, doc.blocks[id]);
          if (overlaps.length) notes.push("Overlaps: " + overlaps.join("; "));
          return { doc, result: { updated: describeBlock(doc, doc.blocks[id], new Map()), notes } };
        });
      }),
  );

  server.registerTool(
    "remove_block",
    {
      title: "Delete a block",
      description: "Deletes a block by id, together with its attached files. Use it for cancelled bookings or duplicates.",
      inputSchema: { id: z.string().min(1).max(80) },
      annotations: { destructiveHint: true },
    },
    ({ id }) =>
      run(() =>
        mutate(userId, (doc) => {
          const block = doc.blocks[id];
          if (!block) throw new ToolError(`No block with id ${id}. Call get_trip for current ids.`);
          delete doc.blocks[id];
          for (const item of Object.values(doc.items)) {
            if (item.linkedBlockId === id) doc.items[item.id] = { ...item, linkedBlockId: null, linkSource: null, distanceFromBlockMeters: null, dayId: null };
          }
          return { doc, result: { removed: block.title } };
        }),
      ),
  );

  server.registerTool(
    "create_upload_link",
    {
      title: "Get a link to attach files",
      description:
        "Returns a private link the user opens to pick ticket or voucher files (PDF, PNG, JPEG, WebP, up to 4 MB each) for one block. The files show on the board and download to the phone for offline use. The link works for 1 hour.",
      inputSchema: { block_id: z.string().min(1).max(80) },
    },
    ({ block_id }) =>
      run(async () => {
        const tripId = await tripIdOf(userId);
        if (!tripId) throw new ToolError("There is no trip yet.");
        const { token, expiresAt } = await createUploadLink(tripId, block_id);
        return {
          url: `${origin}/upload/${token}`,
          expires_at: new Date(expiresAt).toISOString(),
          valid_hours: UPLOAD_LINK_TTL_MS / 3_600_000,
          tell_user: "Open this link and choose the file. Anyone with the link can add files to this block until it expires.",
        };
      }),
  );

  server.registerTool(
    "remove_file",
    {
      title: "Delete an attached file",
      description: "Deletes one attached file by its id (from get_trip). The phone drops it on its next sync.",
      inputSchema: { file_id: z.string().min(1).max(80) },
      annotations: { destructiveHint: true },
    },
    ({ file_id }) =>
      run(async () => {
        const tripId = await tripIdOf(userId);
        if (!tripId) throw new ToolError("There is no trip yet.");
        const files = await listAttachments(tripId);
        if (!files.some((f) => f.id === file_id)) throw new ToolError(`No file with id ${file_id}.`);
        await deleteAttachment(tripId, file_id);
        return { removed: file_id };
      }),
  );

  return server;
}
