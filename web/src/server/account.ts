"use server";

import { eq } from "drizzle-orm";
import { AuthError } from "next-auth";
import { z } from "zod";
import { LoginLimitedError, signIn, signOut, TotpRequiredError } from "@/auth";
import { getDb, schema } from "@/db/client";
import { claimInvite, finishInvite, openSignupRole, releaseInvite } from "./lib/invites";
import { audit } from "./lib/audit";
import { hashPassword } from "./lib/passwords";

export interface FormState {
  error: string | null;
  /** Echoed back so a failed attempt does not clear what was typed. */
  email?: string;
  name?: string;
  /** The account has two-factor sign-in, so the form should show the code field. */
  needsCode?: boolean;
}

const Credentials = z.object({
  email: z.string().trim().toLowerCase().pipe(z.email("Enter a valid email address.").max(200)),
  password: z.string().min(8, "Password must be at least 8 characters.").max(200),
});

const SignUp = Credentials.extend({
  name: z.string().trim().min(1, "Enter your name.").max(80),
});

async function startSession(email: string, password: string, code = ""): Promise<FormState> {
  try {
    await signIn("credentials", { email, password, code, redirectTo: "/" });
  } catch (e) {
    if (e instanceof LoginLimitedError || (e as { code?: string }).code === "rate_limited") {
      return { error: "Too many attempts. Wait 15 minutes and try again.", email };
    }
    if (e instanceof TotpRequiredError || (e as { code?: string }).code === "totp_required") {
      return { error: code ? "That code is wrong or was already used. Wait for the next one." : "Enter the 6-digit code from your authenticator app.", email, needsCode: true };
    }
    if (e instanceof AuthError) return { error: "Wrong email or password.", email };
    throw e;
  }
  return { error: null };
}

export async function loginAction(_prev: FormState, form: FormData): Promise<FormState> {
  const parsed = Credentials.safeParse({ email: form.get("email"), password: form.get("password") });
  if (!parsed.success) return { error: "Wrong email or password.", email: String(form.get("email") ?? "") };
  return startSession(parsed.data.email, parsed.data.password, String(form.get("code") ?? "").slice(0, 12));
}

export async function signUpAction(_prev: FormState, form: FormData): Promise<FormState> {
  const parsed = SignUp.safeParse({ name: form.get("name"), email: form.get("email"), password: form.get("password") });
  const typed = { email: String(form.get("email") ?? ""), name: String(form.get("name") ?? "") };
  if (!parsed.success) return { error: parsed.error.issues[0]?.message ?? "Check the form.", ...typed };
  const { name, email, password } = parsed.data;
  const invite = String(form.get("invite") ?? "").trim();

  const db = await getDb();
  if (await db.query.users.findFirst({ where: eq(schema.users.email, email) })) {
    return { error: "An account with this email already exists.", ...typed };
  }
  // Sign-up is closed except for the first account, the ADMIN_EMAIL bootstrap, and holders of an invite link.
  let role: "ADMIN" | "USER" | null = await openSignupRole(email);
  const usingInvite = !role && Boolean(invite);
  if (usingInvite) role = await claimInvite(invite);
  if (!role) {
    return { error: invite ? "This invite link has expired or was already used. Ask for a new one." : "Sign-up needs an invite link from the admin.", ...typed };
  }
  const id = "u-" + crypto.randomUUID();
  try {
    await db.insert(schema.users).values({ id, email, name, passwordHash: await hashPassword(password), role, createdAt: Date.now() });
  } catch (e) {
    if (usingInvite) await releaseInvite(invite);
    throw e;
  }
  if (usingInvite) await finishInvite(invite, id);
  await audit("signup", id, { detail: usingInvite ? "with invite" : "first account" });
  return startSession(email, password);
}

export async function signOutAction(): Promise<void> {
  await signOut({ redirectTo: "/login" });
}
