import { Suspense } from "react";
import { redirect } from "next/navigation";
import { SecurityPanel } from "@/components/account/SecurityPanel";
import { securityStateAction } from "@/server/security";
import { currentUser } from "@/server/lib/session";

export const metadata = { title: "Security · Trip Planner" };

async function Panel() {
  if (!(await currentUser())) redirect("/login");
  return <SecurityPanel initial={await securityStateAction()} />;
}

export default function SecurityPage() {
  return (
    <main className="min-h-dvh bg-black px-6 py-5 text-neutral-200">
      <Suspense fallback={<p className="text-sm text-neutral-600">Loading…</p>}>
        <Panel />
      </Suspense>
    </main>
  );
}
