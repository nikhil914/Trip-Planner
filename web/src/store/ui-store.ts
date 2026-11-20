import { create } from "zustand";
import type { CartTab, DragData } from "@/lib/types";

export type DrawerTab = "ADD" | "COPILOT" | "CHECKS";

interface UiState {
  cartTab: CartTab;
  drawerTab: DrawerTab;
  selectedBlockId: string | null;
  focusedDayId: string | null;
  paletteOpen: boolean;
  tripDialogOpen: boolean;
  phoneDialogOpen: boolean;
  connectorDialogOpen: boolean;
  dragging: DragData | null;
  dropPreview: { dayId: string; minute: number } | null;
  set(patch: Partial<Omit<UiState, "set">>): void;
}

// View state lives outside the trip store so it never enters undo history or localStorage.
export const useUiStore = create<UiState>((set) => ({
  cartTab: "ACTIVITIES",
  drawerTab: "ADD",
  selectedBlockId: null,
  focusedDayId: null,
  paletteOpen: false,
  tripDialogOpen: false,
  phoneDialogOpen: false,
  connectorDialogOpen: false,
  dragging: null,
  dropPreview: null,
  set: (patch) => set(patch),
}));
