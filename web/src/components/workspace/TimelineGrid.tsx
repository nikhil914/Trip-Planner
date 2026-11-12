"use client";

import { memo, useEffect, useRef, useState } from "react";
import { useDraggable, useDroppable } from "@dnd-kit/core";
import { useShallow } from "zustand/react/shallow";
import { DAY_MINUTES, formatDay, hhmm, PX_PER_MIN } from "@/lib/time";
import type { Day, DragData } from "@/lib/types";
import { useTripStore } from "@/store/trip-store";
import { useUiStore } from "@/store/ui-store";
import { BlockFace, LinkedChip } from "./cards";
import { cn, TYPE_COLORS } from "./ui";

const HEADER_H = 64;
const HOURS = Array.from({ length: 24 }, (_, h) => h);

const ScheduledBlock = memo(function ScheduledBlock({ id }: { id: string }) {
  const block = useTripStore((s) => s.blocks[id]);
  const itemIds = useTripStore(
    useShallow((s) =>
      Object.values(s.items)
        .filter((i) => i.linkedBlockId === id)
        .sort((a, b) => (a.distanceFromBlockMeters ?? 1e9) - (b.distanceFromBlockMeters ?? 1e9))
        .map((i) => i.id),
    ),
  );
  const selected = useUiStore((s) => s.selectedBlockId === id);
  const drag = useDraggable({ id: `block:${id}`, data: { kind: "block", id } satisfies DragData });
  const drop = useDroppable({ id: `drop:${id}`, data: { kind: "block-drop", blockId: id } });
  if (!block || block.startMinute === null) return null;

  return (
    <div
      ref={(node) => {
        drag.setNodeRef(node);
        drop.setNodeRef(node);
      }}
      {...drag.attributes}
      {...drag.listeners}
      onClick={() => useUiStore.getState().set({ selectedBlockId: id, focusedDayId: block.dayId })}
      style={{ top: block.startMinute * PX_PER_MIN, minHeight: Math.max(30, block.durationMinutes * PX_PER_MIN) }}
      className={cn(
        "absolute left-1 right-1 cursor-grab overflow-hidden rounded-md border border-l-2 border-neutral-700 bg-neutral-900/95 px-2 py-1 hover:z-10",
        TYPE_COLORS[block.blockType],
        selected && "z-10 ring-1 ring-amber-400",
        drop.isOver && "z-10 ring-2 ring-emerald-400",
        drag.isDragging && "opacity-30",
      )}
    >
      <BlockFace block={block} compact={block.durationMinutes < 45} />
      {itemIds.length > 0 && (
        <div className="mt-1 space-y-1">
          {itemIds.map((itemId) => (
            <LinkedChip key={itemId} id={itemId} />
          ))}
        </div>
      )}
    </div>
  );
});

function PrepList({ dayId }: { dayId: string }) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState("");
  const prepIds = useTripStore(
    useShallow((s) =>
      Object.values(s.items)
        .filter((i) => i.category === "MORNING_PREP" && i.dayId === dayId)
        .map((i) => i.id),
    ),
  );
  const items = useTripStore((s) => s.items);

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="rounded px-1 text-[10px] text-neutral-500 hover:bg-neutral-800 hover:text-neutral-200"
      >
        Prep {prepIds.length}
      </button>
      {open && (
        <div className="absolute right-0 top-5 z-40 w-56 rounded-md border border-neutral-700 bg-neutral-900 p-2 shadow-xl shadow-black">
          <p className="mb-1 text-[10px] uppercase tracking-wide text-neutral-500">Morning prep</p>
          {prepIds.map((id) => (
            <div key={id} className="flex items-center justify-between gap-2 py-0.5 text-xs text-neutral-200">
              <span className="truncate">{items[id]?.title}</span>
              <button type="button" aria-label="Remove" className="text-neutral-600 hover:text-red-400" onClick={() => useTripStore.getState().removeItem(id)}>
                ×
              </button>
            </div>
          ))}
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!text.trim()) return;
              useTripStore.getState().addItem({ title: text.trim(), category: "MORNING_PREP", dayId, priceSource: "USER" });
              setText("");
            }}
          >
            <input
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Bring 150 GEL cash"
              aria-label="Add morning prep item"
              className="mt-1 h-7 w-full rounded border border-neutral-700 bg-neutral-950 px-1.5 text-xs text-neutral-100 placeholder:text-neutral-600 focus:border-amber-400 focus:outline-none"
            />
          </form>
        </div>
      )}
    </div>
  );
}

const DayColumn = memo(function DayColumn({ day }: { day: Day }) {
  const { setNodeRef } = useDroppable({ id: `day:${day.id}`, data: { kind: "day", dayId: day.id } });
  const blockIds = useTripStore(
    useShallow((s) =>
      Object.values(s.blocks)
        .filter((b) => b.dayId === day.id)
        .map((b) => b.id),
    ),
  );
  const spend = useTripStore((s) => {
    let total = 0;
    for (const b of Object.values(s.blocks)) if (b.dayId === day.id) total += b.estimatedCostLocal ?? 0;
    for (const i of Object.values(s.items)) if (i.dayId === day.id && i.linkedBlockId) total += i.estimatedCostLocal ?? 0;
    return total;
  });
  const currency = useTripStore((s) => s.trip.localCurrency);
  const focused = useUiStore((s) => s.focusedDayId === day.id);
  const previewMinute = useUiStore((s) => (s.dropPreview?.dayId === day.id ? s.dropPreview.minute : null));
  const previewDuration = useTripStore((s) => {
    const d = useUiStore.getState().dragging;
    return d?.kind === "block" ? (s.blocks[d.id]?.durationMinutes ?? 60) : 60;
  });

  return (
    <section id={`col-${day.id}`} className="w-60 shrink-0 border-l border-neutral-800" aria-label={`Day ${day.dayNumber}`}>
      <header
        style={{ height: HEADER_H }}
        onClick={() => useUiStore.getState().set({ focusedDayId: day.id })}
        className={cn(
          "sticky top-0 z-30 flex cursor-pointer flex-col justify-center border-b border-neutral-800 bg-neutral-950 px-3",
          focused && "border-b-amber-400",
        )}
      >
        <div className="flex items-center justify-between">
          <span className="text-sm font-semibold text-neutral-100">Day {day.dayNumber}</span>
          <PrepList dayId={day.id} />
        </div>
        <div className="flex items-center justify-between text-[11px] text-neutral-500">
          <span>{formatDay(day.dateIso)}</span>
          <span>{spend > 0 ? `${Math.round(spend)} ${currency}` : ""}</span>
        </div>
      </header>
      <div
        ref={setNodeRef}
        style={{
          height: DAY_MINUTES * PX_PER_MIN,
          backgroundImage: `repeating-linear-gradient(to bottom, #232323 0, #232323 1px, transparent 1px, transparent ${60 * PX_PER_MIN}px)`,
        }}
        className="relative"
      >
        {previewMinute !== null && (
          <div
            style={{ top: previewMinute * PX_PER_MIN, height: Math.max(30, previewDuration * PX_PER_MIN) }}
            className="pointer-events-none absolute left-1 right-1 z-20 rounded-md border border-dashed border-amber-400 bg-amber-400/10 px-2 py-1 font-mono text-[11px] text-amber-300"
          >
            {hhmm(previewMinute)}
          </div>
        )}
        {blockIds.map((id) => (
          <ScheduledBlock key={id} id={id} />
        ))}
      </div>
    </section>
  );
});

export function TimelineGrid() {
  const days = useTripStore((s) => s.days);
  const focusedDayId = useUiStore((s) => s.focusedDayId);
  const scroller = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scroller.current) scroller.current.scrollTop = 7 * 60 * PX_PER_MIN;
  }, []);

  useEffect(() => {
    const box = scroller.current;
    const col = focusedDayId ? document.getElementById(`col-${focusedDayId}`) : null;
    if (!box || !col) return;
    const left = col.offsetLeft - 48;
    const right = left + col.offsetWidth + 48;
    if (left < box.scrollLeft) box.scrollTo({ left, behavior: "smooth" });
    else if (right > box.scrollLeft + box.clientWidth) box.scrollTo({ left: right - box.clientWidth, behavior: "smooth" });
  }, [focusedDayId]);

  return (
    <main ref={scroller} className="min-w-0 flex-1 overflow-auto bg-black" onClick={(e) => e.target === e.currentTarget && useUiStore.getState().set({ selectedBlockId: null })}>
      <div className="flex min-w-max">
        <div className="sticky left-0 z-40 w-12 shrink-0 bg-black">
          <div style={{ height: HEADER_H }} className="sticky top-0 z-40 border-b border-neutral-800 bg-black" />
          <div style={{ height: DAY_MINUTES * PX_PER_MIN }} className="relative">
            {HOURS.map((h) => (
              <span key={h} style={{ top: h * 60 * PX_PER_MIN - 6 }} className="absolute right-2 font-mono text-[10px] text-neutral-600">
                {h === 0 ? "" : `${String(h).padStart(2, "0")}:00`}
              </span>
            ))}
          </div>
        </div>
        {days.map((day) => (
          <DayColumn key={day.id} day={day} />
        ))}
        <div className="w-8 shrink-0 border-l border-neutral-800" />
      </div>
    </main>
  );
}
