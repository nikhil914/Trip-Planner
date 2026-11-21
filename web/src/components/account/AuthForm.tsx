"use client";

import Link from "next/link";
import { useActionState } from "react";
import { loginAction, signUpAction, type FormState } from "@/server/account";
import { Button, Input } from "@/components/workspace/ui";

const initial: FormState = { error: null };
const label = "block text-[10px] uppercase tracking-wide text-neutral-500";

export function AuthForm({ mode, invite }: { mode: "login" | "signup"; invite?: string }) {
  const signup = mode === "signup";
  const [state, action, pending] = useActionState(signup ? signUpAction : loginAction, initial);
  return (
    <main className="flex min-h-dvh items-center justify-center bg-black px-4 text-neutral-200">
      <form action={action} className="w-[360px] max-w-full space-y-3 rounded-lg border border-neutral-800 bg-neutral-950 p-5">
        <h1 className="text-base font-semibold text-neutral-100">{signup ? "Create your account" : "Sign in to Trip Planner"}</h1>
        {signup && invite && <input type="hidden" name="invite" value={invite} />}
        {signup && (
          <label className={label}>
            Name
            <Input name="name" autoComplete="name" required maxLength={80} defaultValue={state.name} />
          </label>
        )}
        <label className={label}>
          Email
          <Input name="email" type="email" autoComplete="email" required defaultValue={state.email} />
        </label>
        <label className={label}>
          Password
          <Input name="password" type="password" autoComplete={signup ? "new-password" : "current-password"} required minLength={8} />
        </label>
        {state.error && (
          <p className="text-xs text-red-400" role="alert">
            {state.error}
          </p>
        )}
        <Button variant="solid" type="submit" disabled={pending} className="h-8 w-full">
          {pending ? "Please wait…" : signup ? "Create account" : "Sign in"}
        </Button>
        {signup ? (
          <p className="text-xs text-neutral-500">
            Already have an account?{" "}
            <Link href="/login" className="text-amber-300 hover:underline">
              Sign in
            </Link>
          </p>
        ) : (
          <p className="text-xs text-neutral-500">New accounts are by invitation from the admin.</p>
        )}
      </form>
    </main>
  );
}
