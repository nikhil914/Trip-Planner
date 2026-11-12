"use client";

import { memo } from "react";
import { useDraggable } from "@dnd-kit/core";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { hhmm } from "@/lib/time";
import type { Block, DragData, Item } from "@/lib/types";
import { useTripStore } from "@/store/trip-store";
import { useUiStore } from "@/store/ui-store";
import { cn, TYPE_COLORS } from "./ui";

export function BlockFace({ block, compact }: { block: Block; compact?: boolean }) {
  return (
    <div className="min-w-0">
      <div className="flex items-center gap-1.5">
        {block.startMinute !== null && <span className="font-mono text-[11px] text-amber-400">{hhmm(block.startMinute)}</span>}
        <span className="truncate text-[13px] font-medium text-neutral-100">{block.title || "Untitled"}</span>
        {block.isFixedTime && <span className="rounded bg-red-950 px-1 text-[9px] font-semibold tracking-wide text-red-300">FIXED</span>}
      </div>
      {!compact && (
        <div className="mt-0.5 flex gap-2 text-[11px] text-neutral-500">
          <span>{block.durationMinutes} min</span>
          {block.lat === null && <span title="No coordinates, so nothing can auto-link here">no pin</span>}
          {block.estimatedCostLocal !== null && <span>{block.estimatedCostLocal}</span>}
        </div>
      )}
    </div>
  );
}

export function ItemFace({ item }: { item: Item }) {
  return (
    <div className="min-w-0">
      <div className="flex items-center gap-1.5">
        <span className={cn("h-1.5 w-1.5 shrink-0 rounded-full", item.category === "TO_BUY" ? "bg-fuchsia-400" : "bg-emerald-400")} />
        <span className="truncate text-[12px] text-neutral-200">{item.title}</span>
        {item.distanceFromBlockMeters !== null && (
          <span className="shrink-0 text-[10px] text-neutral-500">{item.distanceFromBlockMeters} m</span>
        )}
      </div>
      {item.shopOrVenueName && <div className="truncate pl-3 text-[10px] text-neutral-500">{item.shopOrVenueName}</div>}
    </div>
  );
}

export const CartBlockCard = memo(function CartBlockCard({ id }: { id: string }) {
  const block = useTripStore((s) => s.blocks[id]);
  const selected = useUiStore((s) => s.selectedBlockId === id);
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: `cart-block:${id}`,
    data: { kind: "block", id, inCart: true } satisfies DragData,
  });
  if (!block) return null;
  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      onClick={() => useUiStore.getState().set({ selectedBlockId: id })}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={cn(
        "cursor-grab rounded-md border border-l-2 border-neutral-800 bg-neutral-900 px-2.5 py-2",
        TYPE_COLORS[block.blockType],
        selected && "ring-1 ring-amber-400",
        isDragging && "opacity-30",
      )}
    >
      <BlockFace block={block} />
    </div>
  );
});

export const CartItemCard = memo(function CartItemCard({ id }: { id: string }) {
  const item = useTripStore((s) => s.items[id]);
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: `cart-item:${id}`,
    data: { kind: "item", id, inCart: true } satisfies DragData,
  });
  if (!item) return null;
  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      style={{ transform: CSS.Translate.toString(transform), transition }}
      className={cn("group cursor-grab rounded-md border border-neutral-800 bg-neutral-900 px-2.5 py-2", isDragging && "opacity-30")}
    >
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <ItemFace item={item} />
          {item.priceBenchmarkNote && <div className="mt-1 pl-3 text-[10px] text-amber-300/80">{item.priceBenchmarkNote}</div>}
          {item.lat === null && <div className="mt-1 pl-3 text-[10px] text-neutral-600">No location. Drag onto an activity to link it.</div>}
        </div>
        <button
          type="button"
          aria-label={`Delete ${item.title}`}
          onPointerDown={(e) => e.stopPropagation()}
          onClick={() => useTripStore.getState().removeItem(id)}
          className="text-neutral-600 opacity-0 hover:text-red-400 group-hover:opacity-100"
        >
          ×
        </button>
      </div>
    </div>
  );
});

export const LinkedChip = memo(function LinkedChip({ id }: { id: string }) {
  const item = useTripStore((s) => s.items[id]);
  const { attributes, listeners, setNodeRef, isDragging } = useDraggable({
    id: `chip:${id}`,
    data: { kind: "item", id } satisfies DragData,
  });
  if (!item) return null;
  return (
    <div
      ref={setNodeRef}
      {...attributes}
      {...listeners}
      onClick={(e) => e.stopPropagation()}
      title={item.linkSource === "MANUAL" ? "Linked by hand" : "Linked automatically (within 800 m)"}
      className={cn("cursor-grab rounded border border-neutral-700/70 bg-black/40 px-1.5 py-1", isDragging && "opacity-30")}
    >
      <ItemFace item={item} />
    </div>
  );
});

export function OverlayCard({ data }: { data: DragData }) {
  const block = useTripStore((s) => (data.kind === "block" ? s.blocks[data.id] : undefined));
  const item = useTripStore((s) => (data.kind === "item" ? s.items[data.id] : undefined));
  return (
    <div className="w-56 cursor-grabbing rounded-md border border-amber-400/60 bg-neutral-900 px-2.5 py-2 shadow-2xl shadow-black">
      {block && <BlockFace block={block} />}
      {item && <ItemFace item={item} />}
    </div>
  );
}
