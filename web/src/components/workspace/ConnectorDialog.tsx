"use client";

import { useEffect, useState } from "react";
import type { ConnectorKeyInfo } from "@/server/lib/connector-keys";
import { createConnectorKeyAction, listConnectorKeysAction, revokeConnectorKeyAction } from "@/server/trip";
import { useUiStore } from "@/store/ui-store";
import { Button, Input } from "./ui";

const used = (ms: number | null) => {
  if (ms === null) return "never used";
  const minutes = Math.round((Date.now() - ms) / 60_000);
  if (minutes < 2) return "used just now";
  if (minutes < 60) return `used ${minutes} min ago`;
  if (minutes < 1440) return `used ${Math.round(minutes / 60)} h ago`;
  return `used ${Math.round(minutes / 1440)} d ago`;
};

function Body({ onClose }: { onClose: () => void }) {
  const [keys, setKeys] = useState<ConnectorKeyInfo[] | null>(null);
  const [name, setName] = useState("");
  const [created, setCreated] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listConnectorKeysAction().then(setKeys, (e: Error) => setError(e.message));
  }, []);

  async function create() {
    setBusy(true);
    setError(null);
    try {
      const result = await createConnectorKeyAction(name);
      setCreated(result.url);
      setKeys(result.keys);
      setName("");
      setCopied(false);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function revoke(id: string) {
    try {
      setKeys(await revokeConnectorKeyAction(id));
    } catch (e) {
      setError((e as Error).message);
    }
  }

  async function copy() {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created);
      setCopied(true);
    } catch {
      setError("Copy failed. Select the link and copy it by hand.");
    }
  }

  return (
    <div className="space-y-4">
      <h2 className="text-sm font-semibold text-neutral-100">Connector</h2>
      <p className="text-xs text-neutral-400">
        A connector link lets a chat app read this trip, add bookings to it and send you upload links for tickets. Add the link as a custom connector (MCP server URL) in the chat app&apos;s connector settings.
      </p>

      {created ? (
        <div className="space-y-2 rounded border border-amber-400/40 bg-amber-400/5 p-3">
          <p className="text-xs text-amber-300">Copy this link now. It is shown only once.</p>
          <Input readOnly value={created} onFocus={(e) => e.target.select()} aria-label="Connector link" className="font-mono text-[11px]" />
          <div className="flex gap-2">
            <Button variant="solid" onClick={copy}>
              {copied ? "Copied" : "Copy link"}
            </Button>
            <Button onClick={() => setCreated(null)}>Done</Button>
          </div>
          <p className="text-[11px] text-neutral-500">The link works like a password for this trip. Do not share it. Revoke it below if it leaks.</p>
        </div>
      ) : (
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            void create();
          }}
        >
          <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Name, such as Laptop chat" maxLength={60} aria-label="Connector name" />
          <Button variant="solid" type="submit" disabled={busy} className="h-8 shrink-0">
            {busy ? "Creating…" : "Create link"}
          </Button>
        </form>
      )}

      {error && (
        <p className="text-xs text-red-400" role="alert">
          {error}
        </p>
      )}

      <div>
        <h3 className="text-[10px] uppercase tracking-wide text-neutral-500">Active links</h3>
        {keys === null ? (
          <p className="mt-1 text-xs text-neutral-600">Loading…</p>
        ) : keys.length === 0 ? (
          <p className="mt-1 text-xs text-neutral-600">None yet.</p>
        ) : (
          <ul className="mt-1 divide-y divide-neutral-800">
            {keys.map((k) => (
              <li key={k.id} className="flex items-center gap-2 py-1.5">
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm text-neutral-200">{k.name}</div>
                  <div className="text-[11px] text-neutral-500">{used(k.lastUsedAt)}</div>
                </div>
                <Button variant="danger" onClick={() => revoke(k.id)}>
                  Revoke
                </Button>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="flex justify-end">
        <Button onClick={onClose}>Close</Button>
      </div>
    </div>
  );
}

export function ConnectorDialog() {
  const open = useUiStore((s) => s.connectorDialogOpen);
  const close = () => useUiStore.getState().set({ connectorDialogOpen: false });
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/70 pt-24" onClick={(e) => e.target === e.currentTarget && close()}>
      <div role="dialog" aria-modal="true" aria-label="Connector" className="w-[480px] max-w-[92vw] rounded-lg border border-neutral-700 bg-neutral-900 p-4 shadow-2xl shadow-black">
        <Body onClose={close} />
      </div>
    </div>
  );
}
