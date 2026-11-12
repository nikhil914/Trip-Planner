import { closestCenter, pointerWithin, type Collision, type CollisionDetection } from "@dnd-kit/core";

const kindOf = (c: Collision) => c.data?.droppableContainer?.data.current?.kind as string | undefined;

// Items may land on an activity card or back in the cart. Blocks may land on a day column or the cart.
export const layeredCollision: CollisionDetection = (args) => {
  const dragging = args.active.data.current?.kind as "block" | "item" | undefined;
  const hits = pointerWithin(args);
  const first = (kind: string) => hits.find((c) => kindOf(c) === kind);

  if (dragging === "item") {
    const card = first("block-drop"); // the nested zone wins over the column behind it
    if (card) return [card];
  } else {
    const day = first("day");
    if (day) return [day];
  }

  const cart = first("cart");
  if (!cart) return [];
  // Inside the cart, behave like a sortable list among cards of the same kind.
  const cartCards = args.droppableContainers.filter(
    (c) => c.data.current?.inCart && c.data.current?.kind === dragging && c.id !== args.active.id,
  );
  const sorted = closestCenter({ ...args, droppableContainers: cartCards });
  return sorted.length ? [sorted[0]] : [cart];
};
