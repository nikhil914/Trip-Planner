export type BlockType = "ACTIVITY" | "TRANSIT" | "HOTEL" | "FOOD_STOP";
export type BlockStatus = "PENDING" | "DONE" | "SKIPPED";
export type ItemCategory = "TO_BUY" | "EAT_SUGGESTION" | "MORNING_PREP";
export type LinkSource = "AUTO" | "MANUAL" | null;

export interface Trip {
  id: string;
  title: string;
  destinationLabel: string;
  destinationTimezone: string;
  homeCurrency: string;
  localCurrency: string;
  exchangeRate: number;
  startDate: string;
  endDate: string;
}

export interface Day {
  id: string;
  dayNumber: number;
  dateIso: string;
  timezone: string;
}

export interface Block {
  id: string;
  dayId: string | null;
  sortOrder: number;
  startMinute: number | null;
  durationMinutes: number;
  isFixedTime: boolean;
  blockType: BlockType;
  title: string;
  subtitleOrTip: string | null;
  lat: number | null;
  lng: number | null;
  googlePlaceId: string | null;
  googleMapsUri: string | null;
  estimatedCostLocal: number | null;
  bookingRef: string | null;
  opensMinute: number | null;
  closesMinute: number | null;
  localScriptName: string | null;
  phoneticPronunciation: string | null;
  status: BlockStatus;
}

export interface Item {
  id: string;
  linkedBlockId: string | null;
  linkSource: LinkSource;
  dayId: string | null;
  sortOrder: number;
  category: ItemCategory;
  title: string;
  shopOrVenueName: string | null;
  priceBenchmarkNote: string | null;
  priceSource: "AI_ESTIMATE" | "USER" | null;
  estimatedCostLocal: number | null;
  lat: number | null;
  lng: number | null;
  geoVerified: boolean;
  googlePlaceId: string | null;
  distanceFromBlockMeters: number | null;
  localScriptText: string | null;
  phoneticPronunciation: string | null;
  isChecked: boolean;
}

export interface TripDoc {
  trip: Trip;
  days: Day[];
  blocks: Record<string, Block>;
  items: Record<string, Item>;
}

export type BlockDraft = Partial<Omit<Block, "id">> & { title: string };
export type ItemDraft = Partial<Omit<Item, "id">> & { title: string; category: ItemCategory };

export interface PlaceHit {
  id: string | null;
  displayName: string;
  address: string;
  lat: number;
  lng: number;
  googleMapsUri: string | null;
}

export type CartTab = "ACTIVITIES" | "TO_BUY" | "FOOD";

export type DragData =
  | { kind: "block"; id: string; inCart?: boolean }
  | { kind: "item"; id: string; inCart?: boolean };

export type DropData =
  | { kind: "day"; dayId: string }
  | { kind: "block-drop"; blockId: string }
  | { kind: "cart" }
  | DragData;
