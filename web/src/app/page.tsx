import { Suspense } from "react";
import { redirect } from "next/navigation";
import Workspace from "@/components/workspace/Workspace";
import { currentUser } from "@/server/lib/session";

const Loading = () => <div className="flex h-dvh items-center justify-center bg-black text-sm text-neutral-600">Loading trip…</div>;

async function SignedIn() {
  const user = await currentUser();
  if (!user) redirect("/login");
  return <Workspace user={user} />;
}

export default function Page() {
  return (
    <Suspense fallback={<Loading />}>
      <SignedIn />
    </Suspense>
  );
}
