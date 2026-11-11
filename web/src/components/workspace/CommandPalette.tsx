"use client";

import { Command } from "cmdk";
import { sampleDoc } from "@/lib/sample";
import { redo, undo, useTripStore } from "@/store/trip-store";
import { useUiStore } from "@/store/ui-store";

export function CommandPalette() {
  const open = useUiStore((s) => s.paletteOpen);
  const days = useTripStore((s) => s.days);
  const ui = useUiStore.getState().set;
  const run = (fn: () => void) => () => {
    fn();
    ui({ paletteOpen: false });
  };

  const item = "flex cursor-pointer items-center justify-between rounded px-2 py-1.5 text-sm text-neutral-200 data-[selected=true]:bg-neutral-800";
  const heading = "[&_[cmdk-group-heading]]:px-2 [&_[cmdk-group-heading]]:py-1 [&_[cmdk-group-heading]]:text-[10px] [&_[cmdk-group-heading]]:uppercase [&_[cmdk-group-heading]]:tracking-wider [&_[cmdk-group-heading]]:text-neutral-500";

  return (
    <Command.Dialog
      open={open}
      onOpenChange={(v) => ui({ paletteOpen: v })}
      label="Command palette"
      className="fixed left-1/2 top-24 z-50 w-[520px] max-w-[90vw] -translate-x-1/2 rounded-lg border border-neutral-700 bg-neutral-900 p-2 shadow-2xl shadow-black"
    >
      <Command.Input placeholder="Type a command" className="mb-2 h-9 w-full rounded-md border border-neutral-800 bg-neutral-950 px-2 text-sm text-neutral-100 focus:outline-none" />
      <Command.List className="max-h-80 overflow-y-auto">
        <Command.Empty className="px-2 py-4 text-sm text-neutral-500">No matching command.</Command.Empty>
        <Command.Group heading="Edit" className={heading}>
          <Command.Item className={item} onSelect={run(undo)}>Undo</Command.Item>
          <Command.Item className={item} onSelect={run(redo)}>Redo</Command.Item>
          <Command.Item className={item} onSelect={run(() => ui({ tripDialogOpen: true }))}>Trip settings</Command.Item>
        </Command.Group>
        <Command.Group heading="Go to" className={heading}>
          {days.map((d) => (
            <Command.Item key={d.id} className={item} onSelect={run(() => ui({ focusedDayId: d.id }))}>
              Day {d.dayNumber} <span className="text-xs text-neutral-500">{d.dateIso}</span>
            </Command.Item>
          ))}
          <Command.Item className={item} onSelect={run(() => ui({ cartTab: "ACTIVITIES" }))}>Cart: Activities</Command.Item>
          <Command.Item className={item} onSelect={run(() => ui({ cartTab: "TO_BUY" }))}>Cart: To-Buy</Command.Item>
          <Command.Item className={item} onSelect={run(() => ui({ cartTab: "FOOD" }))}>Cart: Food &amp; Drink</Command.Item>
          <Command.Item className={item} onSelect={run(() => ui({ drawerTab: "ADD" }))}>Drawer: Add link or place</Command.Item>
          <Command.Item className={item} onSelect={run(() => ui({ drawerTab: "COPILOT" }))}>Drawer: Co-Pilot</Command.Item>
          <Command.Item className={item} onSelect={run(() => ui({ drawerTab: "CHECKS" }))}>Drawer: Logistics checks</Command.Item>
        </Command.Group>
        <Command.Group heading="Trip" className={heading}>
          <Command.Item className={item} onSelect={run(() => useTripStore.getState().replaceDoc(sampleDoc()))}>Load the sample Tbilisi trip</Command.Item>
          <Command.Item
            className={item}
            onSelect={run(() => {
              const s = useTripStore.getState();
              s.replaceDoc({ trip: s.trip, days: s.days, blocks: {}, items: {} });
            })}
          >
            Clear all blocks and items
          </Command.Item>
        </Command.Group>
      </Command.List>
    </Command.Dialog>
  );
}
