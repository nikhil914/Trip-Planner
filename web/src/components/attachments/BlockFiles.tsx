"use client";

import { useRef, useState } from "react";
import { useShallow } from "zustand/react/shallow";
import { Button } from "@/components/workspace/ui";
import { deleteAttachmentAction } from "@/server/trip";
import { useTripStore } from "@/store/trip-store";
import { useAttachmentStore } from "./store";
import { ACCEPT, formatSize, uploadFile } from "./upload";

/** Tickets and vouchers on one block. They download to the paired phone and open offline. */
export function BlockFiles({ blockId }: { blockId: string }) {
  const tripId = useTripStore((s) => s.trip.id);
  const files = useAttachmentStore(useShallow((s) => s.files.filter((f) => f.blockId === blockId)));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);

  async function upload(list: FileList | null) {
    if (!list?.length) return;
    setBusy(true);
    setError(null);
    for (const file of Array.from(list)) {
      try {
        const saved = await uploadFile("/api/attachments", file, { blockId });
        useAttachmentStore.setState((s) => ({ files: [...s.files, saved] }));
      } catch (e) {
        setError((e as Error).message);
      }
    }
    setBusy(false);
    if (input.current) input.current.value = "";
  }

  async function remove(id: string) {
    setError(null);
    try {
      useAttachmentStore.setState({ files: await deleteAttachmentAction(id) });
    } catch (e) {
      setError((e as Error).message);
    }
  }

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <span className="text-[10px] text-neutral-500">Tickets and vouchers</span>
        <Button disabled={busy || !tripId} onClick={() => input.current?.click()} title={tripId ? undefined : "Wait for the trip to save first"}>
          {busy ? "Uploading…" : "Attach file"}
        </Button>
      </div>
      <input ref={input} type="file" accept={ACCEPT} multiple className="hidden" aria-label="Attach file" onChange={(e) => void upload(e.target.files)} />
      {files.length === 0 ? (
        <p className="text-[11px] text-neutral-600">PDF or image, up to 4 MB. The phone keeps a copy for offline use.</p>
      ) : (
        <ul className="space-y-1">
          {files.map((f) => (
            <li key={f.id} className="flex items-center gap-2 rounded border border-neutral-800 px-2 py-1 text-xs">
              <a
                href={`/api/trips/${encodeURIComponent(tripId)}/attachments/${encodeURIComponent(f.id)}`}
                target="_blank"
                rel="noopener"
                className="min-w-0 flex-1 truncate text-amber-300 hover:underline"
              >
                {f.fileName}
              </a>
              <span className="shrink-0 text-neutral-500">{formatSize(f.sizeBytes)}</span>
              <button type="button" onClick={() => void remove(f.id)} className="shrink-0 text-neutral-500 hover:text-red-400" aria-label={`Remove ${f.fileName}`}>
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
      {error && (
        <p className="text-[11px] text-red-400" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
