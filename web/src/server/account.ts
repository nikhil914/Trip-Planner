"use server";

import { count, eq } from "drizzle-orm";
import { AuthError } from "next-auth";
import { z } from "zod";
import { signIn, signOut } from "@/auth";
import { getDb, schema } from "@/db/client";
import { hashPassword } from "./lib/passwords";

export interface FormState {
  error: string | null;
  /** Echoed back so a failed attempt does not clear what was typed. */
  email?: string;
  name?: string;
}

const Credentials = z.object({
  email: z.string().trim().toLowerCase().pipe(z.email("Enter a valid email address.").max(200)),
  password: z.string().min(8, "Password must be at least 8 characters.").max(200),
});

const SignUp = Credentials.extend({
  name: z.string().trim().min(1, "Enter your name.").max(80),
});

async function startSession(email: string, password: string): Promise<FormState> {
  try {
    await signIn("credentials", { email, password, redirectTo: "/" });
  } catch (e) {
    if (e instanceof AuthError) return { error: "Wrong email or password.", email };
    throw e;
  }
  return { error: null };
}

export async function loginAction(_prev: FormState, form: FormData): Promise<FormState> {
  const parsed = Credentials.safeParse({ email: form.get("email"), password: form.get("password") });
  if (!parsed.success) return { error: "Wrong email or password.", email: String(form.get("email") ?? "") };
  return startSession(parsed.data.email, parsed.data.password);
}

export async function signUpAction(_prev: FormState, form: FormData): Promise<FormState> {
  const parsed = SignUp.safeParse({ name: form.get("name"), email: form.get("email"), password: form.get("password") });
  const typed = { email: String(form.get("email") ?? ""), name: String(form.get("name") ?? "") };
  if (!parsed.success) return { error: parsed.error.issues[0]?.message ?? "Check the form.", ...typed };
  const { name, email, password } = parsed.data;

  const db = await getDb();
  if (await db.query.users.findFirst({ where: eq(schema.users.email, email) })) {
    return { error: "An account with this email already exists.", ...typed };
  }
  // ADMIN_EMAIL names the admin. Without it the first account to sign up is the admin.
  const adminEmail = process.env.ADMIN_EMAIL?.trim().toLowerCase();
  const [{ total }] = await db.select({ total: count() }).from(schema.users);
  const role = adminEmail ? (email === adminEmail ? "ADMIN" : "USER") : total === 0 ? "ADMIN" : "USER";
  await db.insert(schema.users).values({
    id: "u-" + crypto.randomUUID(),
    email,
    name,
    passwordHash: await hashPassword(password),
    role,
    createdAt: Date.now(),
  });
  return startSession(email, password);
}

export async function signOutAction(): Promise<void> {
  await signOut({ redirectTo: "/login" });
}
