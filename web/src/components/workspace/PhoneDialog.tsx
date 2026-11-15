"use client";

import { useEffect, useState } from "react";
import QRCode from "qrcode";
import { createPairingCodeAction, listDevicesAction, revokeDeviceAction, type PairedDevice } from "@/server/trip";
import { useUiStore } from "@/store/ui-store";
import { Button } from "./ui";

interface Pairing {
  code: string;
  expiresAt: number;
  deepLink: string;
  qrSvg: string;
}

const ago = (ms: number | null) => {
  if (ms === null) return "never synced";
  const minutes = Math.round((Date.now() - ms) / 60_000);
  if (minutes < 1) return "synced just now";
  if (minutes < 60) return `synced ${minutes} min ago`;
  if (minutes < 1440) return `synced ${Math.round(minutes / 60)} h ago`;
  return `synced ${Math.round(minutes / 1440)} d ago`;
};

function Body({ onClose }: { onClose: () => void }) {
  const [pairing, setPairing] = useState<Pairing | null>(null);
  const [devices, setDevices] = useState<PairedDevice[] | null>(null);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const insecure = typeof window !== "undefined" && window.location.protocol !== "https:";

  useEffect(() => {
    listDevicesAction().then(setDevices, (e: Error) => setError(e.message));
  }, []);

  // While a code is showing, count it down and watch for the phone that redeems it.
  useEffect(() => {
    if (!pairing) return;
    const tick = setInterval(() => setSecondsLeft(Math.max(0, Math.round((pairing.expiresAt - Date.now()) / 1000))), 1000);
    const watch = setInterval(() => listDevicesAction().then(setDevices, () => {}), 4000);
    return () => {
      clearInterval(tick);
      clearInterval(watch);
    };
  }, [pairing]);

  async function createCode() {
    setBusy(true);
    setError(null);
    try {
      const created = await createPairingCodeAction();
      if (!created) return setError("Save the trip first, then try again.");
      const deepLink = `fieldguide://pair?u=${encodeURIComponent(window.location.origin)}&c=${created.code}`;
      const qrSvg = await QRCode.toString(deepLink, { type: "svg", margin: 1, errorCorrectionLevel: "M" });
      setPairing({ ...created, deepLink, qrSvg });
      setSecondsLeft(Math.round((created.expiresAt - Date.now()) / 1000));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function revoke(id: string) {
    try {
      setDevices(await revokeDeviceAction(id));
    } catch (e) {
      setError((e as Error).message);
    }
  }

  const expired = pairing !== null && secondsLeft <= 0;
  return (
    <div className="space-y-4">
      <h2 className="text-sm font-semibold text-neutral-100">Pair a phone</h2>

      {pairing && !expired ? (
        <div className="flex gap-4">
          <div className="h-36 w-36 shrink-0 overflow-hidden rounded bg-white p-1" aria-label="Pairing QR code" dangerouslySetInnerHTML={{ __html: pairing.qrSvg }} />
          <div className="min-w-0 space-y-2 text-xs text-neutral-400">
            <p>Scan with the phone camera, or open Field Guide and type this code with the server address.</p>
            <p className="font-mono text-2xl tracking-[0.3em] text-amber-300">{pairing.code}</p>
            <p className="truncate font-mono text-[11px] text-neutral-300">{window.location.origin}</p>
            <p className="tabular-nums">
              Works once. Expires in {Math.floor(secondsLeft / 60)}:{String(secondsLeft % 60).padStart(2, "0")}.
            </p>
          </div>
        </div>
      ) : (
        <div className="space-y-2">
          <p className="text-xs text-neutral-400">
            {expired ? "That code expired." : "Create a one-time code, then scan it with the phone that has Field Guide installed."}
          </p>
          <Button variant="solid" onClick={createCode} disabled={busy}>
            {busy ? "Creating…" : expired ? "Create a new code" : "Create pairing code"}
          </Button>
        </div>
      )}

      {insecure && (
        <p className="text-[11px] text-amber-400">
          This site is not on https. The release build of the app only talks to https servers, so pair against the deployed site.
        </p>
      )}
      {error && (
        <p className="text-xs text-red-400" role="alert">
          {error}
        </p>
      )}

      <div>
        <h3 className="text-[10px] uppercase tracking-wide text-neutral-500">Paired phones</h3>
        {devices === null ? (
          <p className="mt-1 text-xs text-neutral-600">Loading…</p>
        ) : devices.length === 0 ? (
          <p className="mt-1 text-xs text-neutral-600">None yet.</p>
        ) : (
          <ul className="mt-1 divide-y divide-neutral-800">
            {devices.map((d) => (
              <li key={d.id} className="flex items-center gap-2 py-1.5">
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm text-neutral-200">{d.name}</div>
                  <div className="text-[11px] text-neutral-500">
                    {ago(d.lastSeenAt)}
                    {d.appVersion && ` · app ${d.appVersion}`}
                  </div>
                </div>
                <Button variant="danger" onClick={() => revoke(d.id)}>
                  Unpair
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

export function PhoneDialog() {
  const open = useUiStore((s) => s.phoneDialogOpen);
  const close = () => useUiStore.getState().set({ phoneDialogOpen: false });
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/70 pt-24" onClick={(e) => e.target === e.currentTarget && close()}>
      <div role="dialog" aria-modal="true" aria-label="Pair a phone" className="w-[460px] max-w-[92vw] rounded-lg border border-neutral-700 bg-neutral-900 p-4 shadow-2xl shadow-black">
        <Body onClose={close} />
      </div>
    </div>
  );
}
