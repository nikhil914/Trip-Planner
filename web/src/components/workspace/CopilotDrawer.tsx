"use client";

import { useMemo, useState, useTransition } from "react";
import { useShallow } from "zustand/react/shallow";
import { checkDay } from "@/lib/sanity";
import { hhmm } from "@/lib/time";
import type { BlockDraft, BlockType, ItemDraft, PlaceHit } from "@/lib/types";
import { extractFromUrlAction, fillDayGapsAction } from "@/server/copilot";
import { searchPlacesAction } from "@/server/places";
import { unrollMapsLinkAction } from "@/server/unroll";
import { useTripStore } from "@/store/trip-store";
import { useUiStore, type DrawerTab } from "@/store/ui-store";
import { Button, cn, Input } from "./ui";

const TABS: { id: DrawerTab; label: string }[] = [
  { id: "ADD", label: "Add" },
  { id: "COPILOT", label: "Co-Pilot" },
  { id: "CHECKS", label: "Checks" },
];

const UNROLL_ERRORS: Record<string, string> = {
  not_a_maps_link: "That is not a Google Maps link.",
  google_throttled: "Google is rate-limiting link lookups from this server. Try again in a minute.",
  too_many_redirects: "The link redirected too many times.",
  unparseable: "The link has no place name or pin.",
  network: "Could not reach Google.",
};

const isMapsLink = (s: string) => /^https:\/\/(maps\.app\.goo\.gl|goo\.gl|g\.co|(www\.|maps\.)?google\.[a-z.]+)\//.test(s);

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="space-y-2 border-b border-neutral-800 p-3">
      <h3 className="text-[10px] font-semibold uppercase tracking-wider text-neutral-500">{title}</h3>
      {children}
    </section>
  );
}

function placeToBlock(hit: PlaceHit): BlockDraft {
  return { title: hit.displayName, subtitleOrTip: hit.address || null, lat: hit.lat, lng: hit.lng, googlePlaceId: hit.id, googleMapsUri: hit.googleMapsUri };
}

function placeToItem(hit: PlaceHit, category: "TO_BUY" | "EAT_SUGGESTION"): ItemDraft {
  return { category, title: hit.displayName, shopOrVenueName: hit.address || null, lat: hit.lat, lng: hit.lng, geoVerified: true, googlePlaceId: hit.id, priceSource: "USER" };
}

function AddPane() {
  const [text, setText] = useState("");
  const [hits, setHits] = useState<PlaceHit[]>([]);
  const [source, setSource] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [pending, startTransition] = useTransition();
  const destination = useTripStore((s) => s.trip.destinationLabel);

  function submit() {
    const value = text.trim();
    if (!value) return;
    setMessage(null);
    setHits([]);
    startTransition(async () => {
      const store = useTripStore.getState();
      if (isMapsLink(value)) {
        const res = await unrollMapsLinkAction(value);
        if (!res.ok) return setMessage(UNROLL_ERRORS[res.error] ?? res.error);
        store.addBlock(res.draft);
        useUiStore.getState().set({ cartTab: "ACTIVITIES" });
        setMessage(`Added "${res.draft.title}" to Activities${res.draft.lat == null ? " without a pin" : ""}.`);
        setText("");
      } else if (/^https?:\/\//.test(value)) {
        const res = await extractFromUrlAction(value, destination);
        if (!res.ok) return setMessage(res.error);
        res.blocks.forEach((b) => store.addBlock(b));
        res.items.forEach((i) => store.addItem(i));
        setMessage(`Added ${res.blocks.length} activities and ${res.items.length} food or shopping items from that page.`);
        setText("");
      } else {
        const anchor = Object.values(store.blocks).find((b) => b.lat !== null && b.lng !== null);
        const res = await searchPlacesAction(
          destination && !value.toLowerCase().includes(destination.split(",")[0].toLowerCase()) ? `${value}, ${destination}` : value,
          anchor ? { lat: anchor.lat!, lng: anchor.lng! } : undefined,
        );
        if (!res.ok) return setMessage(`Search failed (${res.error}).`);
        setHits(res.hits);
        setSource(res.source);
        if (res.hits.length === 0) setMessage("No places found.");
      }
    });
  }

  return (
    <Section title="Paste a link or search a place">
      <form
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
        className="flex gap-2"
      >
        <Input value={text} onChange={(e) => setText(e.target.value)} placeholder="maps.app.goo.gl/…, a blog URL, or a place name" aria-label="Link or place" />
        <Button variant="solid" type="submit" disabled={pending}>
          {pending ? "…" : "Go"}
        </Button>
      </form>
      {message && <p className="text-xs text-neutral-400" role="status">{message}</p>}
      {hits.length > 0 && (
        <ul className="space-y-1.5">
          {hits.map((hit, i) => (
            <li key={`${hit.id ?? hit.displayName}-${i}`} className="rounded-md border border-neutral-800 p-2">
              <p className="truncate text-[13px] text-neutral-100">{hit.displayName}</p>
              <p className="truncate text-[11px] text-neutral-500">{hit.address}</p>
              <div className="mt-1.5 flex gap-1">
                <Button onClick={() => useTripStore.getState().addBlock(placeToBlock(hit))}>Activity</Button>
                <Button onClick={() => useTripStore.getState().addItem(placeToItem(hit, "EAT_SUGGESTION"))}>Food</Button>
                <Button onClick={() => useTripStore.getState().addItem(placeToItem(hit, "TO_BUY"))}>To-Buy</Button>
              </div>
            </li>
          ))}
          {source === "osm" && (
            <li className="text-[10px] text-neutral-600">
              Results from OpenStreetMap. Set GOOGLE_PLACES_API_KEY to get Google place ids for the business sheet on the phone.
            </li>
          )}
        </ul>
      )}
    </Section>
  );
}

function CopilotPane() {
  const days = useTripStore((s) => s.days);
  const focused = useUiStore((s) => s.focusedDayId);
  const dayId = focused ?? days[0]?.id;
  const [message, setMessage] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<ItemDraft[]>([]);
  const [pending, startTransition] = useTransition();

  function run() {
    const s = useTripStore.getState();
    const day = s.days.find((d) => d.id === dayId);
    if (!day) return;
    setMessage(null);
    setDrafts([]);
    startTransition(async () => {
      const res = await fillDayGapsAction({
        destination: s.trip.destinationLabel,
        dateIso: day.dateIso,
        localCurrency: s.trip.localCurrency,
        blocks: Object.values(s.blocks)
          .filter((b) => b.dayId === day.id && b.blockType !== "TRANSIT")
          .map((b) => ({ id: b.id, title: b.title, type: b.blockType, startMinute: b.startMinute, durationMinutes: b.durationMinutes, lat: b.lat, lng: b.lng })),
      });
      if (!res.ok) return setMessage(res.error);
      setDrafts(res.items);
      if (res.items.length === 0) setMessage("No suggestions came back.");
    });
  }

  function accept(index: number) {
    useTripStore.getState().addItem(drafts[index]);
    setDrafts((d) => d.filter((_, i) => i !== index));
  }

  return (
    <Section title="Fill the gaps in a day">
      <div className="flex gap-2">
        <select
          value={dayId}
          onChange={(e) => useUiStore.getState().set({ focusedDayId: e.target.value })}
          aria-label="Day"
          className="h-8 flex-1 rounded-md border border-neutral-800 bg-neutral-950 px-2 text-sm text-neutral-100"
        >
          {days.map((d) => (
            <option key={d.id} value={d.id}>
              Day {d.dayNumber} · {d.dateIso}
            </option>
          ))}
        </select>
        <Button variant="solid" onClick={run} disabled={pending}>
          {pending ? "Thinking…" : "Suggest"}
        </Button>
      </div>
      <p className="text-[11px] text-neutral-600">
        Suggests food and shopping near that day&apos;s stops. Prices are model estimates. Places are checked against a map search before they can auto-link.
      </p>
      {message && <p className="text-xs text-amber-300" role="status">{message}</p>}
      {drafts.length > 0 && (
        <>
          <ul className="space-y-1.5">
            {drafts.map((d, i) => (
              <li key={`${d.title}-${i}`} className="rounded-md border border-neutral-800 p-2">
                <p className="text-[13px] text-neutral-100">
                  <span className="text-neutral-500">{d.category === "TO_BUY" ? "Buy" : "Eat"}:</span> {d.title}
                </p>
                {d.shopOrVenueName && <p className="text-[11px] text-neutral-500">{d.shopOrVenueName}</p>}
                {d.priceBenchmarkNote && <p className="text-[11px] text-amber-300/80">{d.priceBenchmarkNote}</p>}
                {d.localScriptText && (
                  <p className="text-[11px] text-neutral-400">
                    {d.localScriptText} {d.phoneticPronunciation && <span className="text-neutral-600">({d.phoneticPronunciation})</span>}
                  </p>
                )}
                <div className="mt-1.5 flex items-center gap-2">
                  <Button onClick={() => accept(i)}>Add</Button>
                  {!d.geoVerified && <span className="text-[10px] text-neutral-600">Place not found on the map</span>}
                </div>
              </li>
            ))}
          </ul>
          <Button
            onClick={() => {
              drafts.forEach((d) => useTripStore.getState().addItem(d));
              setDrafts([]);
            }}
          >
            Add all {drafts.length}
          </Button>
        </>
      )}
    </Section>
  );
}

function ChecksPane() {
  const days = useTripStore((s) => s.days);
  const blocks = useTripStore((s) => s.blocks);
  const report = useMemo(
    () =>
      days
        .map((day) => ({ day, warnings: checkDay(Object.values(blocks).filter((b) => b.dayId === day.id)) }))
        .filter((r) => r.warnings.length > 0),
    [days, blocks],
  );

  return (
    <Section title="Logistics checks">
      {report.length === 0 && <p className="text-xs text-neutral-500">No overlaps, tight transfers, or closed venues found.</p>}
      {report.map(({ day, warnings }) => (
        <div key={day.id}>
          <p className="text-xs font-medium text-neutral-300">Day {day.dayNumber}</p>
          <ul className="mt-1 space-y-1">
            {warnings.map((w, i) => (
              <li key={i}>
                <button
                  type="button"
                  onClick={() => useUiStore.getState().set({ selectedBlockId: w.blockId, focusedDayId: day.id })}
                  className="w-full rounded border border-neutral-800 px-2 py-1 text-left text-[11px] text-neutral-400 hover:bg-neutral-900"
                >
                  {w.kind === "OVERLAP" && `${w.title} overlaps ${w.otherTitle} by ${w.minutes} min.`}
                  {w.kind === "TIGHT_TRANSIT" && `${w.title} to ${w.otherTitle} needs about ${w.neededMin} min, buffer is ${w.bufferMin} min.`}
                  {w.kind === "OUTSIDE_HOURS" && `${w.title} is scheduled outside its opening hours.`}
                </button>
              </li>
            ))}
          </ul>
        </div>
      ))}
      <p className="text-[10px] text-neutral-600">Transfer times are straight-line estimates, walking under 2.5 km and riding above.</p>
    </Section>
  );
}

const BLOCK_TYPES: BlockType[] = ["ACTIVITY", "FOOD_STOP", "TRANSIT", "HOTEL"];

function Inspector({ id }: { id: string }) {
  const block = useTripStore((s) => s.blocks[id]);
  const linked = useTripStore(useShallow((s) => Object.values(s.items).filter((i) => i.linkedBlockId === id).map((i) => i.title)));
  if (!block) return null;
  const update = useTripStore.getState().updateBlock;
  const num = (v: string) => (v === "" ? null : Number(v));

  return (
    <Section title="Selected block">
      <Input value={block.title} onChange={(e) => update(id, { title: e.target.value })} aria-label="Title" />
      <Input value={block.subtitleOrTip ?? ""} onChange={(e) => update(id, { subtitleOrTip: e.target.value || null })} placeholder="Tip or note" aria-label="Tip" />
      <div className="grid grid-cols-2 gap-2">
        <label className="text-[10px] text-neutral-500">
          Duration (min)
          <Input type="number" min={5} step={5} value={block.durationMinutes} onChange={(e) => update(id, { durationMinutes: Math.max(5, Number(e.target.value) || 5) })} />
        </label>
        <label className="text-[10px] text-neutral-500">
          Cost (local)
          <Input type="number" min={0} value={block.estimatedCostLocal ?? ""} onChange={(e) => update(id, { estimatedCostLocal: num(e.target.value) })} />
        </label>
        <label className="text-[10px] text-neutral-500">
          Type
          <select
            value={block.blockType}
            onChange={(e) => update(id, { blockType: e.target.value as BlockType })}
            className="h-8 w-full rounded-md border border-neutral-800 bg-neutral-950 px-2 text-sm text-neutral-100"
          >
            {BLOCK_TYPES.map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </label>
        <label className="text-[10px] text-neutral-500">
          Booking ref
          <Input value={block.bookingRef ?? ""} onChange={(e) => update(id, { bookingRef: e.target.value || null })} />
        </label>
        <label className="text-[10px] text-neutral-500">
          Local script
          <Input value={block.localScriptName ?? ""} onChange={(e) => update(id, { localScriptName: e.target.value || null })} />
        </label>
        <label className="text-[10px] text-neutral-500">
          Pronunciation
          <Input value={block.phoneticPronunciation ?? ""} onChange={(e) => update(id, { phoneticPronunciation: e.target.value || null })} />
        </label>
      </div>
      <label className="flex items-center gap-2 text-xs text-neutral-300">
        <input type="checkbox" checked={block.isFixedTime} onChange={(e) => update(id, { isFixedTime: e.target.checked })} className="accent-amber-400" />
        Fixed time (flight, train, timed entry)
      </label>
      <p className="text-[11px] text-neutral-500">
        {block.startMinute !== null ? `Starts ${hhmm(block.startMinute)}, ends ${hhmm(block.startMinute + block.durationMinutes)}.` : "Unscheduled."}{" "}
        {block.lat !== null ? `Pin ${block.lat.toFixed(4)}, ${block.lng!.toFixed(4)}.` : "No pin."}{" "}
        {linked.length > 0 && `Linked: ${linked.join(", ")}.`}
      </p>
      <div className="flex gap-2">
        {block.dayId && <Button onClick={() => useTripStore.getState().unscheduleBlock(id)}>Unschedule</Button>}
        <Button
          variant="danger"
          onClick={() => {
            useTripStore.getState().removeBlock(id);
            useUiStore.getState().set({ selectedBlockId: null });
          }}
        >
          Delete
        </Button>
      </div>
    </Section>
  );
}

export function CopilotDrawer() {
  const tab = useUiStore((s) => s.drawerTab);
  const selected = useUiStore((s) => s.selectedBlockId);

  return (
    <aside className="flex w-80 shrink-0 flex-col border-l border-neutral-800 bg-neutral-950">
      <div className="flex border-b border-neutral-800 px-2 pt-2" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.id}
            type="button"
            role="tab"
            aria-selected={tab === t.id}
            onClick={() => useUiStore.getState().set({ drawerTab: t.id })}
            className={cn("flex-1 border-b-2 pb-2 text-xs", tab === t.id ? "border-amber-400 text-neutral-100" : "border-transparent text-neutral-500 hover:text-neutral-300")}
          >
            {t.label}
          </button>
        ))}
      </div>
      <div className="flex-1 overflow-y-auto">
        {selected && <Inspector key={selected} id={selected} />}
        {tab === "ADD" && <AddPane />}
        {tab === "COPILOT" && <CopilotPane />}
        {tab === "CHECKS" && <ChecksPane />}
      </div>
    </aside>
  );
}
