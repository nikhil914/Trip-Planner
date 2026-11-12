"use client";

import { useEffect, useState } from "react";
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  type Active,
  type DragEndEvent,
  type DragMoveEvent,
  type DragStartEvent,
  type Over,
} from "@dnd-kit/core";
import { sortableKeyboardCoordinates } from "@dnd-kit/sortable";
import { formatDay, PX_PER_MIN, SNAP_MIN, snapMinute } from "@/lib/time";
import type { CartTab, DragData, DropData } from "@/lib/types";
import { redo, undo, useTripStore } from "@/store/trip-store";
import { useUiStore } from "@/store/ui-store";
import { OverlayCard } from "./cards";
import { layeredCollision } from "./collision";
import { CommandPalette } from "./CommandPalette";
import { CopilotDrawer } from "./CopilotDrawer";
import { StagingCart } from "./StagingCart";
import { TimelineGrid } from "./TimelineGrid";
import { TripDialog } from "./TripDialog";
import { Button, Kbd } from "./ui";

// A block's start time is where its top edge lands inside the day column.
function dropMinute(active: Active, over: Over, durationMinutes: number): number | null {
  const rect = active.rect.current.translated;
  if (!rect) return null;
  return snapMinute((rect.top - over.rect.top) / PX_PER_MIN, durationMinutes);
}

function onDragStart({ active }: DragStartEvent) {
  useUiStore.getState().set({ dragging: active.data.current as DragData });
}

function onDragMove({ active, over }: DragMoveEvent) {
  const ui = useUiStore.getState();
  const a = active.data.current as DragData;
  const o = over?.data.current as DropData | undefined;
  let preview: { dayId: string; minute: number } | null = null;
  if (over && a.kind === "block" && o?.kind === "day") {
    const minute = dropMinute(active, over, useTripStore.getState().blocks[a.id]?.durationMinutes ?? 60);
    if (minute !== null) preview = { dayId: o.dayId, minute };
  }
  // Only touch the store when the snapped slot changes, not on every pointer move.
  if (preview?.dayId !== ui.dropPreview?.dayId || preview?.minute !== ui.dropPreview?.minute) ui.set({ dropPreview: preview });
}

function onDragEnd({ active, over }: DragEndEvent) {
  useUiStore.getState().set({ dragging: null, dropPreview: null });
  if (!over) return;
  const s = useTripStore.getState();
  const a = active.data.current as DragData;
  const o = over.data.current as DropData;

  if (a.kind === "item") {
    const item = s.items[a.id];
    if (!item) return;
    if (o.kind === "block-drop") {
      if (item.linkedBlockId !== o.blockId) s.linkItem(a.id, o.blockId);
    } else if (o.kind === "item" && o.inCart) {
      if (item.linkedBlockId) s.unlinkItem(a.id, o.id);
      else s.reorderCartItem(a.id, o.id);
    } else if (o.kind === "cart" && item.linkedBlockId) {
      s.unlinkItem(a.id);
    }
    return;
  }

  const block = s.blocks[a.id];
  if (!block) return;
  if (o.kind === "day") {
    const minute = dropMinute(active, over, block.durationMinutes);
    if (minute !== null) s.scheduleBlock(a.id, o.dayId, minute);
    useUiStore.getState().set({ selectedBlockId: a.id, focusedDayId: o.dayId });
  } else if (o.kind === "block" && o.inCart) {
    if (block.dayId) s.unscheduleBlock(a.id, o.id);
    else s.reorderCartBlock(a.id, o.id);
  } else if (o.kind === "cart" && block.dayId) {
    s.unscheduleBlock(a.id);
  }
}

function onDragCancel() {
  useUiStore.getState().set({ dragging: null, dropPreview: null });
}

const TAB_KEYS: Record<string, CartTab> = { "1": "ACTIVITIES", "2": "TO_BUY", "3": "FOOD" };

function onKeyDown(e: KeyboardEvent) {
  const ui = useUiStore.getState();
  const mod = e.metaKey || e.ctrlKey;
  const key = e.key.toLowerCase();

  if (mod && key === "k") {
    e.preventDefault();
    return ui.set({ paletteOpen: !ui.paletteOpen });
  }
  const target = e.target as HTMLElement | null;
  if (target?.closest("input, textarea, select, [contenteditable=true]") || ui.paletteOpen || ui.tripDialogOpen) return;

  if (mod && key === "z") {
    e.preventDefault();
    return e.shiftKey ? redo() : undo();
  }
  if (mod && key === "y") {
    e.preventDefault();
    return redo();
  }
  if (mod || e.altKey) return;

  if (TAB_KEYS[e.key]) return ui.set({ cartTab: TAB_KEYS[e.key] });

  const s = useTripStore.getState();
  if (e.key === "[" || e.key === "]") {
    const index = s.days.findIndex((d) => d.id === ui.focusedDayId);
    const next = s.days[Math.max(0, Math.min(s.days.length - 1, (index === -1 ? 0 : index) + (e.key === "]" ? 1 : -1)))];
    if (next) ui.set({ focusedDayId: next.id });
    return;
  }

  const id = ui.selectedBlockId;
  if (e.key === "Escape") return ui.set({ selectedBlockId: null });
  if (!id || !s.blocks[id]) return;
  if (key === "f") return s.updateBlock(id, { isFixedTime: !s.blocks[id].isFixedTime });
  if (e.key === "ArrowUp" || e.key === "ArrowDown") {
    e.preventDefault();
    return s.nudgeBlock(id, e.key === "ArrowDown" ? SNAP_MIN : -SNAP_MIN);
  }
  if (e.key === "Backspace" || e.key === "Delete") {
    e.preventDefault();
    s.removeBlock(id);
    ui.set({ selectedBlockId: null });
  }
}

function TopBar() {
  const trip = useTripStore((s) => s.trip);
  const dayCount = useTripStore((s) => s.days.length);
  return (
    <header className="flex h-11 shrink-0 items-center gap-3 border-b border-neutral-800 bg-neutral-950 px-3">
      <button type="button" onClick={() => useUiStore.getState().set({ tripDialogOpen: true })} className="truncate text-sm font-semibold text-neutral-100 hover:text-amber-300">
        {trip.title}
      </button>
      <span className="truncate text-xs text-neutral-500">
        {trip.destinationLabel} · {formatDay(trip.startDate)} · {dayCount} {dayCount === 1 ? "day" : "days"}
      </span>
      <div className="ml-auto flex items-center gap-2">
        <Button onClick={undo} aria-label="Undo">Undo</Button>
        <Button onClick={redo} aria-label="Redo">Redo</Button>
        <Button onClick={() => useUiStore.getState().set({ paletteOpen: true })}>
          Commands <Kbd>⌘K</Kbd>
        </Button>
      </div>
    </header>
  );
}

export default function Workspace() {
  const [ready, setReady] = useState(false);
  const dragging = useUiStore((s) => s.dragging);
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );

  // The saved trip lives in localStorage, so it is loaded after the first paint to keep server and client markup equal.
  useEffect(() => {
    void Promise.resolve(useTripStore.persist.rehydrate()).then(() => {
      useTripStore.temporal.getState().clear();
      setReady(true);
    });
  }, []);

  useEffect(() => {
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  if (!ready) return <div className="flex h-dvh items-center justify-center bg-black text-sm text-neutral-600">Loading trip…</div>;

  return (
    <div className="flex h-dvh flex-col overflow-hidden bg-black text-neutral-200">
      <TopBar />
      <DndContext
        id="trip-workspace"
        sensors={sensors}
        collisionDetection={layeredCollision}
        onDragStart={onDragStart}
        onDragMove={onDragMove}
        onDragEnd={onDragEnd}
        onDragCancel={onDragCancel}
      >
        <div className="flex min-h-0 flex-1">
          <StagingCart />
          <TimelineGrid />
          <CopilotDrawer />
        </div>
        <DragOverlay dropAnimation={null}>{dragging && <OverlayCard data={dragging} />}</DragOverlay>
      </DndContext>
      <CommandPalette />
      <TripDialog />
    </div>
  );
}
