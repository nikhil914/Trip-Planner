import NextAuth, { CredentialsSignin } from "next-auth";
import Credentials from "next-auth/providers/credentials";
import { eq } from "drizzle-orm";
import { getDb, schema } from "@/db/client";
import { clearLoginFailures, isLoginLimited, recordLoginFailure, requestIp } from "@/server/lib/login-limit";
import { verifyPassword } from "@/server/lib/passwords";

/** Too many wrong passwords from this IP address in the last 15 minutes. */
export class LoginLimitedError extends CredentialsSignin {
  code = "rate_limited";
}

export const { handlers, auth, signIn, signOut } = NextAuth({
  trustHost: true,
  // A session lasts 7 days and is renewed while in use.
  session: { strategy: "jwt", maxAge: 7 * 24 * 60 * 60, updateAge: 24 * 60 * 60 },
  pages: { signIn: "/login" },
  logger: {
    // A wrong password is an everyday event, not a server error worth a stack trace.
    error(error) {
      if ((error as { type?: string }).type !== "CredentialsSignin") console.error(error);
    },
  },
  providers: [
    Credentials({
      credentials: { email: {}, password: {} },
      async authorize(credentials, request) {
        const email = String(credentials?.email ?? "").trim().toLowerCase().slice(0, 200);
        const password = String(credentials?.password ?? "");
        if (!email || !password) return null;
        const ip = requestIp(request);
        if (await isLoginLimited(ip)) throw new LoginLimitedError();
        const db = await getDb();
        const user = await db.query.users.findFirst({ where: eq(schema.users.email, email) });
        const ok = Boolean(user && !user.disabled && (await verifyPassword(password, user.passwordHash)));
        if (!ok || !user) {
          await recordLoginFailure(ip);
          return null;
        }
        await clearLoginFailures(ip);
        return { id: user.id, email: user.email, name: user.name };
      },
    }),
  ],
  callbacks: {
    jwt({ token, user }) {
      if (user?.id) token.sub = user.id;
      return token;
    },
    session({ session, token }) {
      if (token.sub) session.user.id = token.sub;
      return session;
    },
  },
});
