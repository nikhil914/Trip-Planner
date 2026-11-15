"use client";

import Link from "next/link";
import { useState, useTransition } from "react";
import { setUserDisabledAction, setUserRoleAction, type AdminUserRow } from "@/server/admin";
import { Button } from "@/components/workspace/ui";

const date = (ms: number) => new Date(ms).toISOString().slice(0, 10);

export function AdminUsers({ initial, selfId }: { initial: AdminUserRow[]; selfId: string }) {
  const [rows, setRows] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [pending, start] = useTransition();

  const run = (work: () => Promise<AdminUserRow[]>) =>
    start(async () => {
      try {
        setRows(await work());
        setError(null);
      } catch (e) {
        setError((e as Error).message);
      }
    });

  return (
    <div className="mx-auto max-w-5xl space-y-4">
      <div className="flex items-center gap-3">
        <h1 className="text-base font-semibold text-neutral-100">Users</h1>
        <span className="text-xs text-neutral-500">{rows.length} total</span>
        <Link href="/" className="ml-auto text-xs text-amber-300 hover:underline">
          Back to the planner
        </Link>
      </div>
      {error && (
        <p className="text-xs text-red-400" role="alert">
          {error}
        </p>
      )}
      <table className="w-full border-collapse text-left text-sm">
        <thead className="text-[10px] uppercase tracking-wide text-neutral-500">
          <tr className="border-b border-neutral-800">
            <th className="py-2 pr-3 font-medium">User</th>
            <th className="py-2 pr-3 font-medium">Role</th>
            <th className="py-2 pr-3 font-medium">Joined</th>
            <th className="py-2 pr-3 font-medium">Trip</th>
            <th className="py-2 pr-3 font-medium">Phones</th>
            <th className="py-2 font-medium">Actions</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((u) => (
            <tr key={u.id} className="border-b border-neutral-900">
              <td className="py-2 pr-3">
                <div className={u.disabled ? "text-neutral-500 line-through" : "text-neutral-100"}>{u.name}</div>
                <div className="text-xs text-neutral-500">{u.email}</div>
              </td>
              <td className="py-2 pr-3 text-xs">{u.disabled ? "Disabled" : u.role === "ADMIN" ? "Admin" : "User"}</td>
              <td className="py-2 pr-3 text-xs tabular-nums text-neutral-400">{date(u.createdAt)}</td>
              <td className="py-2 pr-3 text-xs text-neutral-400">{u.tripTitle ? `${u.tripTitle} (rev ${u.tripRev})` : "None"}</td>
              <td className="py-2 pr-3 text-xs tabular-nums text-neutral-400">{u.deviceCount}</td>
              <td className="py-2">
                <div className="flex gap-2">
                  <Button disabled={pending} onClick={() => run(() => setUserRoleAction(u.id, u.role === "ADMIN" ? "USER" : "ADMIN"))}>
                    {u.role === "ADMIN" ? "Make user" : "Make admin"}
                  </Button>
                  {u.id !== selfId && (
                    <Button variant={u.disabled ? "ghost" : "danger"} disabled={pending} onClick={() => run(() => setUserDisabledAction(u.id, !u.disabled))}>
                      {u.disabled ? "Enable" : "Disable"}
                    </Button>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="text-xs text-neutral-500">A disabled user is signed out at once and their paired phones stop syncing.</p>
    </div>
  );
}
