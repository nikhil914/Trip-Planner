"use client";

import { useState } from "react";
import { useTripStore } from "@/store/trip-store";
import { useUiStore } from "@/store/ui-store";
import { Button, Input } from "./ui";

function isTimezone(tz: string): boolean {
  try {
    new Intl.DateTimeFormat("en", { timeZone: tz });
    return true;
  } catch {
    return false;
  }
}

function Form({ onClose }: { onClose: () => void }) {
  const initial = useTripStore.getState();
  const [form, setForm] = useState({
    title: initial.trip.title,
    destinationLabel: initial.trip.destinationLabel,
    destinationTimezone: initial.trip.destinationTimezone,
    startDate: initial.trip.startDate,
    dayCount: String(initial.days.length),
    localCurrency: initial.trip.localCurrency,
    homeCurrency: initial.trip.homeCurrency,
    exchangeRate: String(initial.trip.exchangeRate),
  });
  const [error, setError] = useState<string | null>(null);
  const field = (key: keyof typeof form) => ({
    value: form[key],
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => setForm((f) => ({ ...f, [key]: e.target.value })),
  });

  function save() {
    const count = Number(form.dayCount);
    const rate = Number(form.exchangeRate);
    if (!form.title.trim()) return setError("Give the trip a title.");
    if (!/^\d{4}-\d{2}-\d{2}$/.test(form.startDate)) return setError("Start date must be YYYY-MM-DD.");
    if (!Number.isInteger(count) || count < 1 || count > 60) return setError("Days must be between 1 and 60.");
    if (!isTimezone(form.destinationTimezone)) return setError("Timezone must be an IANA name such as Asia/Tbilisi.");
    if (!(rate > 0)) return setError("Exchange rate must be a positive number.");
    const removed = initial.days.length - count;
    useTripStore.getState().updateTrip(
      {
        title: form.title.trim(),
        destinationLabel: form.destinationLabel.trim(),
        destinationTimezone: form.destinationTimezone,
        startDate: form.startDate,
        localCurrency: form.localCurrency.trim().toUpperCase(),
        homeCurrency: form.homeCurrency.trim().toUpperCase(),
        exchangeRate: rate,
      },
      count,
    );
    if (removed > 0) useUiStore.getState().set({ cartTab: "ACTIVITIES" });
    onClose();
  }

  const label = "text-[10px] uppercase tracking-wide text-neutral-500";
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        save();
      }}
      className="space-y-3"
    >
      <h2 className="text-sm font-semibold text-neutral-100">Trip settings</h2>
      <label className={label}>Title<Input {...field("title")} /></label>
      <label className={label}>Destination (used for search and suggestions)<Input {...field("destinationLabel")} placeholder="Tbilisi, Georgia" /></label>
      <div className="grid grid-cols-2 gap-3">
        <label className={label}>Start date<Input type="date" {...field("startDate")} /></label>
        <label className={label}>Days<Input type="number" min={1} max={60} {...field("dayCount")} /></label>
        <label className={label}>Timezone for new days<Input {...field("destinationTimezone")} /></label>
        <label className={label}>Local currency<Input {...field("localCurrency")} maxLength={3} /></label>
        <label className={label}>Home currency<Input {...field("homeCurrency")} maxLength={3} /></label>
        <label className={label}>Home per 1 local<Input {...field("exchangeRate")} inputMode="decimal" /></label>
      </div>
      <p className="text-[11px] text-neutral-500">Shortening the trip moves blocks from removed days back to the cart.</p>
      {error && <p className="text-xs text-red-400" role="alert">{error}</p>}
      <div className="flex justify-end gap-2">
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="solid" type="submit">Save</Button>
      </div>
    </form>
  );
}

export function TripDialog() {
  const open = useUiStore((s) => s.tripDialogOpen);
  const close = () => useUiStore.getState().set({ tripDialogOpen: false });
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/70 pt-24" onClick={(e) => e.target === e.currentTarget && close()}>
      <div role="dialog" aria-modal="true" aria-label="Trip settings" className="w-[440px] max-w-[92vw] rounded-lg border border-neutral-700 bg-neutral-900 p-4 shadow-2xl shadow-black">
        <Form onClose={close} />
      </div>
    </div>
  );
}
