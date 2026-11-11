import { create } from "zustand";
import { persist } from "zustand/middleware";
import { temporal } from "zundo";
import { haversineM, relink } from "@/lib/linker";
import { blankBlock, blankItem, buildDays, sampleDoc } from "@/lib/sample";
import { addDays, snapMinute } from "@/lib/time";
import type { Block, BlockDraft, CartTab, Item, ItemDraft, Trip, TripDoc } from "@/lib/types";

const SORT_GAP = 1000;
const newId = (prefix: string) => `${prefix}-${crypto.randomUUID()}`;

interface Actions {
  addBlock(draft: BlockDraft): string;
  addItem(draft: ItemDraft): string;
  updateBlock(id: string, patch: Partial<Block>): void;
  updateItem(id: string, patch: Partial<Item>): void;
  removeBlock(id: string): void;
  removeItem(id: string): void;
  scheduleBlock(id: string, dayId: string, minute: number): void;
  nudgeBlock(id: string, deltaMinutes: number): void;
  unscheduleBlock(id: string, beforeId?: string): void;
  reorderCartBlock(id: string, overId: string): void;
  linkItem(id: string, blockId: string): void;
  unlinkItem(id: string, beforeId?: string): void;
  reorderCartItem(id: string, overId: string): void;
  updateTrip(patch: Partial<Trip>, dayCount?: number): void;
  setDayTimezone(dayId: string, timezone: string): void;
  replaceDoc(doc: TripDoc): void;
}

export type TripState = TripDoc & Actions;

const pickDoc = ({ trip, days, blocks, items }: TripDoc): TripDoc => ({ trip, days, blocks, items });

export const cartBlocks = (doc: TripDoc): Block[] =>
  Object.values(doc.blocks)
    .filter((b) => b.dayId === null)
    .sort((a, b) => a.sortOrder - b.sortOrder);

export const cartItems = (doc: TripDoc, tab: CartTab): Item[] =>
  Object.values(doc.items)
    .filter((i) => i.linkedBlockId === null && i.category === (tab === "FOOD" ? "EAT_SUGGESTION" : "TO_BUY"))
    .sort((a, b) => a.sortOrder - b.sortOrder);

const cartCategory = (item: Item): CartTab => (item.category === "EAT_SUGGESTION" ? "FOOD" : "TO_BUY");

// Returns sortOrder updates that place `id` just before `beforeId` (or at the end) in an ordered list.
function placeInList<T extends { id: string; sortOrder: number }>(list: T[], id: string, beforeId?: string): Map<string, number> {
  const ids = list.map((x) => x.id).filter((x) => x !== id);
  const at = beforeId ? ids.indexOf(beforeId) : -1;
  ids.splice(at === -1 ? ids.length : at, 0, id);
  return new Map(ids.map((x, i) => [x, (i + 1) * SORT_GAP]));
}

// dnd-kit sortable semantics: dropping on a later card places the dragged card after it.
function moveInList<T extends { id: string; sortOrder: number }>(list: T[], id: string, overId: string): Map<string, number> {
  const ids = list.map((x) => x.id);
  const from = ids.indexOf(id);
  const to = ids.indexOf(overId);
  if (from === -1 || to === -1) return new Map();
  ids.splice(to, 0, ...ids.splice(from, 1));
  return new Map(ids.map((x, i) => [x, (i + 1) * SORT_GAP]));
}

function applyOrder<T extends { id: string; sortOrder: number }>(rows: Record<string, T>, order: Map<string, number>): Record<string, T> {
  const next = { ...rows };
  for (const [id, sortOrder] of order) if (next[id] && next[id].sortOrder !== sortOrder) next[id] = { ...next[id], sortOrder };
  return next;
}

export const useTripStore = create<TripState>()(
  persist(
    temporal(
      (set, get) => {
        // Every edit runs the linker, so one user action is one undo step including its link changes.
        const commit = (fn: (doc: TripDoc) => TripDoc) => set((s) => relink(fn(pickDoc(s))));

        return {
          ...relink(sampleDoc()),

          addBlock: (draft) => {
            const id = newId("b");
            commit((d) => {
              const last = cartBlocks(d).at(-1)?.sortOrder ?? 0;
              return { ...d, blocks: { ...d.blocks, [id]: { ...blankBlock(id), sortOrder: last + SORT_GAP, ...draft } } };
            });
            return id;
          },

          addItem: (draft) => {
            const id = newId("i");
            commit((d) => {
              const item: Item = { ...blankItem(id), ...draft };
              const last = cartItems(d, cartCategory(item)).at(-1)?.sortOrder ?? 0;
              return { ...d, items: { ...d.items, [id]: { ...item, sortOrder: draft.sortOrder ?? last + SORT_GAP } } };
            });
            return id;
          },

          updateBlock: (id, patch) =>
            commit((d) => (d.blocks[id] ? { ...d, blocks: { ...d.blocks, [id]: { ...d.blocks[id], ...patch } } } : d)),

          updateItem: (id, patch) =>
            commit((d) => (d.items[id] ? { ...d, items: { ...d.items, [id]: { ...d.items[id], ...patch } } } : d)),

          removeBlock: (id) =>
            commit((d) => {
              const blocks = { ...d.blocks };
              delete blocks[id];
              const items = { ...d.items };
              for (const item of Object.values(items)) {
                if (item.linkedBlockId === id) items[item.id] = { ...item, linkedBlockId: null, linkSource: null, distanceFromBlockMeters: null, dayId: null };
              }
              return { ...d, blocks, items };
            }),

          removeItem: (id) =>
            commit((d) => {
              const items = { ...d.items };
              delete items[id];
              return { ...d, items };
            }),

          scheduleBlock: (id, dayId, minute) =>
            commit((d) => {
              const b = d.blocks[id];
              if (!b || (b.dayId === dayId && b.startMinute === minute)) return d;
              return { ...d, blocks: { ...d.blocks, [id]: { ...b, dayId, startMinute: minute } } };
            }),

          nudgeBlock: (id, delta) => {
            const b = get().blocks[id];
            if (!b || b.dayId === null || b.startMinute === null) return;
            get().scheduleBlock(id, b.dayId, snapMinute(b.startMinute + delta, b.durationMinutes));
          },

          unscheduleBlock: (id, beforeId) =>
            commit((d) => {
              const b = d.blocks[id];
              if (!b) return d;
              const blocks = { ...d.blocks, [id]: { ...b, dayId: null, startMinute: null } };
              return { ...d, blocks: applyOrder(blocks, placeInList(cartBlocks({ ...d, blocks }), id, beforeId)) };
            }),

          reorderCartBlock: (id, overId) =>
            commit((d) => ({ ...d, blocks: applyOrder(d.blocks, moveInList(cartBlocks(d), id, overId)) })),

          linkItem: (id, blockId) =>
            commit((d) => {
              const item = d.items[id];
              const b = d.blocks[blockId];
              if (!item || !b) return d;
              const distance =
                item.lat !== null && item.lng !== null && b.lat !== null && b.lng !== null
                  ? Math.round(haversineM(item.lat, item.lng, b.lat, b.lng))
                  : null;
              return {
                ...d,
                items: {
                  ...d.items,
                  [id]: { ...item, linkedBlockId: blockId, linkSource: "MANUAL", distanceFromBlockMeters: distance, dayId: b.dayId },
                },
              };
            }),

          // A manual unlink is remembered, so the linker does not put the item straight back.
          unlinkItem: (id, beforeId) =>
            commit((d) => {
              const item = d.items[id];
              if (!item) return d;
              const items = {
                ...d.items,
                [id]: { ...item, linkedBlockId: null, linkSource: "MANUAL" as const, distanceFromBlockMeters: null, dayId: null },
              };
              const tab = cartCategory(item);
              return { ...d, items: applyOrder(items, placeInList(cartItems({ ...d, items }, tab), id, beforeId)) };
            }),

          reorderCartItem: (id, overId) =>
            commit((d) => {
              const item = d.items[id];
              if (!item) return d;
              return { ...d, items: applyOrder(d.items, moveInList(cartItems(d, cartCategory(item)), id, overId)) };
            }),

          updateTrip: (patch, dayCount) =>
            commit((d) => {
              const trip = { ...d.trip, ...patch };
              const count = Math.max(1, Math.min(60, dayCount ?? d.days.length));
              const days = buildDays(trip.startDate, count, trip.destinationTimezone, d.days);
              trip.endDate = addDays(trip.startDate, count - 1);
              const kept = new Set(days.map((x) => x.id));
              const blocks = { ...d.blocks };
              // Blocks on removed days go back to the cart instead of vanishing.
              for (const b of Object.values(blocks)) {
                if (b.dayId && !kept.has(b.dayId)) blocks[b.id] = { ...b, dayId: null, startMinute: null };
              }
              const items = { ...d.items };
              for (const i of Object.values(items)) {
                if (i.category === "MORNING_PREP" && i.dayId && !kept.has(i.dayId)) delete items[i.id];
              }
              return { trip, days, blocks, items };
            }),

          setDayTimezone: (dayId, timezone) =>
            commit((d) => ({ ...d, days: d.days.map((x) => (x.id === dayId ? { ...x, timezone } : x)) })),

          replaceDoc: (doc) => commit(() => doc),
        };
      },
      { limit: 100, partialize: (s) => pickDoc(s) as TripState },
    ),
    { name: "trip-planner:doc:v1", partialize: (s) => pickDoc(s), skipHydration: true },
  ),
);

export const undo = () => useTripStore.temporal.getState().undo();
export const redo = () => useTripStore.temporal.getState().redo();
