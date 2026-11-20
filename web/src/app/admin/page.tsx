import { Suspense } from "react";
import { redirect } from "next/navigation";
import { AdminUsers } from "@/components/account/AdminUsers";
import { listUsersAction } from "@/server/admin";
import { currentUser } from "@/server/lib/session";

export const metadata = { title: "Admin · Trip Planner" };

async function Users() {
  const user = await currentUser();
  if (!user) redirect("/login");
  if (user.role !== "ADMIN") redirect("/");
  return <AdminUsers initial={await listUsersAction()} selfId={user.id} />;
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
