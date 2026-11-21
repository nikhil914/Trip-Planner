"use client";

import { useState, useTransition } from "react";
import { createInviteAction, revokeInviteAction } from "@/server/admin";
import type { InviteInfo } from "@/server/lib/invites";
import { Button, Input } from "@/components/workspace/ui";

const date = (ms: number) => new Date(ms).toISOString().slice(0, 10);

export function AdminInvites({ initial }: { initial: InviteInfo[] }) {
  const [rows, setRows] = useState(initial);
  const [note, setNote] = useState("");
  const [asAdmin, setAsAdmin] = useState(false);
  const [link, setLink] = useState<{ url: string; days: number } | null>(null);
  const [copied, setCopied] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();

  const create = () =>
    start(async () => {
      try {
        const { url, days, invites } = await createInviteAction(note, asAdmin ? "ADMIN" : "USER");
        setRows(invites);
        setLink({ url, days });
        setCopied(false);
        setNote("");
        setAsAdmin(false);
        setError(null);
      } catch (e) {
        setError((e as Error).message);
      }
    });

  const revoke = (id: string) =>
    start(async () => {
      try {
        setRows(await revokeInviteAction(id));
        setError(null);
      } catch (e) {
        setError((e as Error).message);
      }
    });

  const copy = async () => {
    if (!link) return;
    try {
      await navigator.clipboard.writeText(link.url);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  return (
    <section className="mx-auto max-w-5xl space-y-3">
      <h2 className="text-sm font-semibold text-neutral-100">Invites</h2>
      <p className="text-xs text-neutral-500">
        Nobody can create an account without an invite link. Each link works for one sign-up and expires after 7 days.
      </p>
      <div className="flex flex-wrap items-center gap-2">
        <Input
          aria-label="Who is this invite for"
          placeholder="Who is it for, e.g. Priya"
          value={note}
          maxLength={80}
          onChange={(e) => setNote(e.target.value)}
          className="w-64"
        />
        <label className="flex items-center gap-1 text-xs text-neutral-400">
          <input type="checkbox" checked={asAdmin} onChange={(e) => setAsAdmin(e.target.checked)} />
          Admin
        </label>
        <Button variant="solid" disabled={pending} onClick={create}>
          Create invite link
        </Button>
      </div>
      {link && (
        <div className="space-y-1 rounded border border-amber-900/60 bg-amber-950/30 p-3">
          <p className="text-xs text-amber-200">Copy this link now. It is shown only once and works for {link.days} days.</p>
          <div className="flex items-center gap-2">
            <code className="min-w-0 flex-1 break-all text-xs text-neutral-200" data-testid="invite-url">
              {link.url}
            </code>
            <Button onClick={copy}>{copied ? "Copied" : "Copy"}</Button>
          </div>
        </div>
      )}
      {error && (
        <p className="text-xs text-red-400" role="alert">
          {error}
        </p>
      )}
      {rows.length > 0 && (
        <table className="w-full border-collapse text-left text-sm">
          <thead className="text-[10px] uppercase tracking-wide text-neutral-500">
            <tr className="border-b border-neutral-800">
              <th className="py-2 pr-3 font-medium">For</th>
              <th className="py-2 pr-3 font-medium">Role</th>
              <th className="py-2 pr-3 font-medium">Created</th>
              <th className="py-2 pr-3 font-medium">Expires</th>
              <th className="py-2 font-medium">Actions</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((i) => (
              <tr key={i.id} className="border-b border-neutral-900">
                <td className="py-2 pr-3 text-neutral-100">{i.note}</td>
                <td className="py-2 pr-3 text-xs">{i.role === "ADMIN" ? "Admin" : "User"}</td>
                <td className="py-2 pr-3 text-xs tabular-nums text-neutral-400">{date(i.createdAt)}</td>
                <td className="py-2 pr-3 text-xs tabular-nums text-neutral-400">{date(i.expiresAt)}</td>
                <td className="py-2">
                  <Button variant="danger" disabled={pending} onClick={() => revoke(i.id)}>
                    Revoke
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
