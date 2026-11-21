import { Suspense } from "react";
import Link from "next/link";
import { connection } from "next/server";
import { AuthForm } from "@/components/account/AuthForm";
import { checkInvite, signupOpenWithoutInvite } from "@/server/lib/invites";

export const metadata = { title: "Create account · Trip Planner" };

async function Gate({ searchParams }: { searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  await connection();
  const { invite } = await searchParams;
  const token = typeof invite === "string" ? invite : "";
  if ((token && (await checkInvite(token))) || (await signupOpenWithoutInvite())) return <AuthForm mode="signup" invite={token} />;
  return (
    <main className="flex min-h-dvh items-center justify-center bg-black px-4 text-neutral-200">
      <div className="w-[360px] max-w-full space-y-3 rounded-lg border border-neutral-800 bg-neutral-950 p-5">
        <h1 className="text-base font-semibold text-neutral-100">Sign-up is by invitation</h1>
        <p className="text-sm text-neutral-400">
          {token ? "This invite link has expired or was already used. Ask the admin for a new one." : "Ask the admin of this trip planner for an invite link."}
        </p>
        <Link href="/login" className="text-xs text-amber-300 hover:underline">
          Sign in instead
        </Link>
      </div>
    </main>
  );
}

export default function SignUpPage(props: PageProps<"/signup">) {
  return (
    <Suspense fallback={null}>
      <Gate searchParams={props.searchParams} />
    </Suspense>
  );
}
