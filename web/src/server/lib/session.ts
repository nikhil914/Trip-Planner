import "server-only";
import { eq } from "drizzle-orm";
import { connection } from "next/server";
import { auth } from "@/auth";
import { getDb, schema } from "@/db/client";

export interface SessionUser {
  id: string;
  email: string;
  name: string;
  role: "ADMIN" | "USER";
}

// The role and the disabled flag are read from the database on every call, so an admin's change applies at once.
export async function currentUser(): Promise<SessionUser | null> {
  // Reading a session is per request. Saying so first keeps it out of the prerendered shell.
  await connection();
  const session = await auth();
  const id = session?.user?.id;
  if (!id) return null;
  const db = await getDb();
  const user = await db.query.users.findFirst({ where: eq(schema.users.id, id) });
  if (!user || user.disabled) return null;
  return { id: user.id, email: user.email, name: user.name, role: user.role };
}

export class NotSignedInError extends Error {
  constructor() {
    super("Sign in to continue.");
  }
}

export async function requireUser(): Promise<SessionUser> {
  const user = await currentUser();
  if (!user) throw new NotSignedInError();
  return user;
}

export async function requireAdmin(): Promise<SessionUser> {
  const user = await requireUser();
  if (user.role !== "ADMIN") throw new Error("Admins only.");
  return user;
}
