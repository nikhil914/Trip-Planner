"use client";

import { useRef, useState } from "react";
import { Button } from "@/components/workspace/ui";
import { ACCEPT, formatSize, uploadFile } from "./upload";

interface Row {
  id: string;
  fileName: string;
  sizeBytes: number;
}

export function UploadForm({ token, blockTitle, expiresAt, initialFiles }: { token: string; blockTitle: string; expiresAt: number; initialFiles: Row[] }) {
  const [files, setFiles] = useState(initialFiles);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const input = useRef<HTMLInputElement>(null);

  async function upload(list: FileList | null) {
    if (!list?.length) return;
    setBusy(true);
    setError(null);
    for (const file of Array.from(list)) {
      try {
        const saved = await uploadFile(`/api/upload/${encodeURIComponent(token)}`, file);
        setFiles((rows) => [...rows, saved]);
      } catch (e) {
        setError((e as Error).message);
      }
    }
    setBusy(false);
    if (input.current) input.current.value = "";
  }

  return (
    <div className="space-y-3">
      <h1 className="text-base font-semibold text-neutral-100">Attach tickets</h1>
      <p className="text-sm text-neutral-400">
        Files go to <span className="text-neutral-100">{blockTitle}</span> and reach the phone on its next sync. PDF, PNG, JPEG or WebP, up to 4 MB each.
      </p>
      <input ref={input} type="file" accept={ACCEPT} multiple className="hidden" onChange={(e) => void upload(e.target.files)} />
      <Button variant="solid" className="h-9 w-full" disabled={busy} onClick={() => input.current?.click()}>
        {busy ? "Uploading…" : "Choose files"}
      </Button>
      {error && (
        <p className="text-xs text-red-400" role="alert">
          {error}
        </p>
      )}
      {files.length > 0 && (
        <ul className="space-y-1 text-sm" aria-label="Attached files">
          {files.map((f) => (
            <li key={f.id} className="flex justify-between gap-2 rounded border border-neutral-800 px-2 py-1">
              <span className="truncate text-neutral-200">{f.fileName}</span>
              <span className="shrink-0 text-neutral-500">{formatSize(f.sizeBytes)}</span>
            </li>
          ))}
        </ul>
      )}
      <p className="text-[11px] text-neutral-600">
        This link works until {new Date(expiresAt).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" })}. Anyone with it can add files to this block, so do not share it.
      </p>
    </div>
  );
}
