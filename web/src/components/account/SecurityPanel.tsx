"use client";

import Link from "next/link";
import { useState, useTransition } from "react";
import type { AuditRow } from "@/server/lib/audit";
import { confirmTotpAction, disableTotpAction, signOutEverywhereAction, startTotpSetupAction, type SecurityState } from "@/server/security";
import { Button, Input } from "@/components/workspace/ui";

const when = (ms: number) => new Date(ms).toISOString().replace("T", " ").slice(0, 16) + " UTC";

export function AuditTable({ rows, showUser = false }: { rows: AuditRow[]; showUser?: boolean }) {
  if (rows.length === 0) return <p className="text-xs text-neutral-500">No events yet.</p>;
  return (
    <table className="w-full border-collapse text-left text-sm">
      <thead className="text-[10px] uppercase tracking-wide text-neutral-500">
        <tr className="border-b border-neutral-800">
          <th className="py-2 pr-3 font-medium">When</th>
          {showUser && <th className="py-2 pr-3 font-medium">Account</th>}
          <th className="py-2 pr-3 font-medium">Event</th>
          <th className="py-2 pr-3 font-medium">IP</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.id} className="border-b border-neutral-900">
            <td className="py-1.5 pr-3 text-xs tabular-nums text-neutral-400">{when(r.at)}</td>
            {showUser && <td className="py-1.5 pr-3 text-xs text-neutral-400">{r.userEmail ?? "Unknown"}</td>}
            <td className={"py-1.5 pr-3 text-xs " + (/failed|blocked/.test(r.kind) ? "text-red-300" : "text-neutral-200")}>
              {r.label}
              {r.detail && <span className="text-neutral-500"> · {r.detail}</span>}
            </td>
            <td className="py-1.5 pr-3 font-mono text-xs text-neutral-400">{r.ip ?? "—"}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function SecurityPanel({ initial }: { initial: SecurityState }) {
  const [state, setState] = useState(initial);
  const [setup, setSetup] = useState<{ secret: string; qrDataUrl: string } | null>(null);
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();

  const run = (work: () => Promise<void>) =>
    start(async () => {
      try {
        await work();
        setError(null);
      } catch (e) {
        setError((e as Error).message);
      }
    });

  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <div className="flex items-center gap-3">
        <h1 className="text-base font-semibold text-neutral-100">Security</h1>
        <Link href="/" className="ml-auto text-xs text-amber-300 hover:underline">
          Back to the planner
        </Link>
      </div>

      <section className="space-y-3">
        <h2 className="text-sm font-semibold text-neutral-100">Two-factor sign-in</h2>
        {state.totpEnabled ? (
          <>
            <p className="text-xs text-emerald-300">On. Sign-in asks for a code from your authenticator app.</p>
            <div className="flex items-center gap-2">
              <Input aria-label="Code to turn off" placeholder="Current code" inputMode="numeric" maxLength={7} value={code} onChange={(e) => setCode(e.target.value)} className="w-36" />
              <Button variant="danger" disabled={pending} onClick={() => run(async () => { setState(await disableTotpAction(code)); setCode(""); })}>
                Turn off
              </Button>
            </div>
          </>
        ) : setup ? (
          <div className="space-y-3">
            <p className="text-xs text-neutral-400">Scan this with an authenticator app (Google Authenticator, Aegis, 1Password), then enter the code it shows.</p>
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src={setup.qrDataUrl} alt="QR code for the authenticator app" className="h-44 w-44 rounded bg-white p-2" />
            <p className="text-xs text-neutral-500">
              Or type this key: <code className="select-all font-mono text-neutral-200" data-testid="totp-secret">{setup.secret}</code>
            </p>
            <div className="flex items-center gap-2">
              <Input aria-label="Code from the app" placeholder="123456" inputMode="numeric" maxLength={7} value={code} onChange={(e) => setCode(e.target.value)} className="w-36" />
              <Button variant="solid" disabled={pending} onClick={() => run(async () => { setState(await confirmTotpAction(code)); setSetup(null); setCode(""); })}>
                Turn on
              </Button>
            </div>
          </div>
        ) : (
          <>
            <p className="text-xs text-neutral-400">Off. Turn it on so a stolen password is not enough to get in.</p>
            <Button variant="solid" disabled={pending} onClick={() => run(async () => setSetup(await startTotpSetupAction()))}>
              Set up two-factor sign-in
            </Button>
          </>
        )}
      </section>

      <section className="space-y-2">
        <h2 className="text-sm font-semibold text-neutral-100">Sessions</h2>
        <p className="text-xs text-neutral-400">Signs out every browser, this one too. Paired phones and connector links keep working; revoke those in Phone and Connector.</p>
        <form action={signOutEverywhereAction}>
          <Button type="submit" variant="danger">Sign out everywhere</Button>
        </form>
      </section>

      {error && (
        <p className="text-xs text-red-400" role="alert">
          {error}
        </p>
      )}

      <section className="space-y-2">
        <h2 className="text-sm font-semibold text-neutral-100">Recent activity</h2>
        <AuditTable rows={state.events} />
      </section>
    </div>
  );
}
