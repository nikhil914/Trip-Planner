"use client";

import { useState } from "react";
import { useDroppable } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import { useShallow } from "zustand/react/shallow";
import type { CartTab } from "@/lib/types";
import { cartBlocks, cartItems, useTripStore } from "@/store/trip-store";
import { useUiStore } from "@/store/ui-store";
import { CartBlockCard, CartItemCard } from "./cards";
import { cn, Input } from "./ui";

const TABS: { id: CartTab; label: string; placeholder: string }[] = [
  { id: "ACTIVITIES", label: "Activities", placeholder: "Add an activity" },
  { id: "TO_BUY", label: "To-Buy", placeholder: "Add something to buy" },
  { id: "FOOD", label: "Food & Drink", placeholder: "Add a dish or drink" },
];

export function StagingCart() {
  const tab = useUiStore((s) => s.cartTab);
  const draggingKind = useUiStore((s) => s.dragging?.kind);
  const { setNodeRef, isOver } = useDroppable({ id: "cart", data: { kind: "cart" } });
  const [text, setText] = useState("");

  const blockIds = useTripStore(useShallow((s) => cartBlocks(s).map((b) => b.id)));
  const buyIds = useTripStore(useShallow((s) => cartItems(s, "TO_BUY").map((i) => i.id)));
  const foodIds = useTripStore(useShallow((s) => cartItems(s, "FOOD").map((i) => i.id)));
  const counts: Record<CartTab, number> = { ACTIVITIES: blockIds.length, TO_BUY: buyIds.length, FOOD: foodIds.length };

  const ids = tab === "ACTIVITIES" ? blockIds : tab === "TO_BUY" ? buyIds : foodIds;
  const sortableIds = ids.map((id) => (tab === "ACTIVITIES" ? `cart-block:${id}` : `cart-item:${id}`));
  const current = TABS.find((t) => t.id === tab)!;

  function add() {
    const title = text.trim();
    if (!title) return;
    const store = useTripStore.getState();
    if (tab === "ACTIVITIES") store.addBlock({ title });
    else store.addItem({ title, category: tab === "FOOD" ? "EAT_SUGGESTION" : "TO_BUY", priceSource: "USER" });
    setText("");
  }

  return (
    <aside ref={setNodeRef} className={cn("flex w-72 shrink-0 flex-col border-r border-neutral-800 bg-neutral-950", isOver && "bg-neutral-900/60")}>
      <div className="flex border-b border-neutral-800 px-2 pt-2" role="tablist">
        {TABS.map((t, i) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => useUiStore.getState().set({ cartTab: t.id })}
            className={cn(
              "flex-1 border-b-2 px-1 pb-2 text-xs",
              tab === t.id ? "border-amber-400 text-neutral-100" : "border-transparent text-neutral-500 hover:text-neutral-300",
            )}
            title={`Shortcut: ${i + 1}`}
          >
            {t.label} <span className="text-neutral-600">{counts[t.id]}</span>
          </button>
        ))}
      </div>

      <div className="flex-1 space-y-1.5 overflow-y-auto p-2">
        <SortableContext items={sortableIds} strategy={verticalListSortingStrategy}>
          {ids.map((id) => (tab === "ACTIVITIES" ? <CartBlockCard key={id} id={id} /> : <CartItemCard key={id} id={id} />))}
        </SortableContext>
        {ids.length === 0 && (
          <p className="px-2 py-6 text-center text-xs text-neutral-600">
            {draggingKind ? "Drop here to unschedule" : "Nothing unscheduled here."}
          </p>
        )}
      </div>

      <form
        className="border-t border-neutral-800 p-2"
        onSubmit={(e) => {
          e.preventDefault();
          add();
        }}
      >
        <Input value={text} onChange={(e) => setText(e.target.value)} placeholder={current.placeholder} aria-label={current.placeholder} />
      </form>
    </aside>
  );
}
