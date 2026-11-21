import { Suspense } from "react";
import { redirect } from "next/navigation";
import { AdminInvites } from "@/components/account/AdminInvites";
import { AdminUsers } from "@/components/account/AdminUsers";
import { AuditTable } from "@/components/account/SecurityPanel";
import { listAuditAction, listInvitesAction, listUsersAction } from "@/server/admin";
import { currentUser } from "@/server/lib/session";

export const metadata = { title: "Admin · Trip Planner" };

async function Users() {
  const user = await currentUser();
  if (!user) redirect("/login");
  if (user.role !== "ADMIN") redirect("/");
  const [users, invites, events] = await Promise.all([listUsersAction(), listInvitesAction(), listAuditAction()]);
  return (
    <div className="space-y-8">
      <AdminUsers initial={users} selfId={user.id} />
      <AdminInvites initial={invites} />
      <section className="mx-auto max-w-5xl space-y-2">
        <h2 className="text-sm font-semibold text-neutral-100">Security log</h2>
        <AuditTable rows={events} showUser />
      </section>
    </div>
  );
}

export default function AdminPage() {
  return (
    <main className="min-h-dvh bg-black px-6 py-5 text-neutral-200">
      <Suspense fallback={<p className="text-sm text-neutral-600">Loading users…</p>}>
        <Users />
      </Suspense>
    </main>
  );
}
