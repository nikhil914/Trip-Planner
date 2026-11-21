import NextAuth, { CredentialsSignin } from "next-auth";
import Credentials from "next-auth/providers/credentials";
import { eq } from "drizzle-orm";
import { getDb, schema } from "@/db/client";
import { clearLoginFailures, isLoginLimited, recordLoginFailure, requestIp } from "@/server/lib/login-limit";
import { audit } from "@/server/lib/audit";
import { verifyPassword } from "@/server/lib/passwords";
import { verifyTotp } from "@/server/lib/totp";

/** Too many wrong passwords from this IP address in the last 15 minutes. */
export class LoginLimitedError extends CredentialsSignin {
  code = "rate_limited";
}

/** The password was right but the account has two-factor sign-in and the code was missing or wrong. */
export class TotpRequiredError extends CredentialsSignin {
  code = "totp_required";
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
      credentials: { email: {}, password: {}, code: {} },
      async authorize(credentials, request) {
        const email = String(credentials?.email ?? "").trim().toLowerCase().slice(0, 200);
        const password = String(credentials?.password ?? "");
        const code = String(credentials?.code ?? "").trim();
        if (!email || !password) return null;
        const ip = requestIp(request);
        if (await isLoginLimited(ip)) {
          await audit("login_blocked", null, { detail: email, ip });
          throw new LoginLimitedError();
        }
        const db = await getDb();
        const user = await db.query.users.findFirst({ where: eq(schema.users.email, email) });
        const ok = Boolean(user && !user.disabled && (await verifyPassword(password, user.passwordHash)));
        if (!ok || !user) {
          await recordLoginFailure(ip);
          await audit("login_failed", user?.id ?? null, { detail: email, ip });
          return null;
        }
        if (user.totpEnabledAt && user.totpSecret) {
          const step = code ? verifyTotp(user.totpSecret, code) : null;
          // A code is accepted once: a step at or before the last accepted one is a replay.
          if (step === null || (user.totpLastStep !== null && step <= user.totpLastStep)) {
            if (code) {
              await recordLoginFailure(ip);
              await audit("login_failed", user.id, { detail: "wrong two-factor code", ip });
            }
            throw new TotpRequiredError();
          }
          await db.update(schema.users).set({ totpLastStep: step }).where(eq(schema.users.id, user.id));
        }
        await clearLoginFailures(ip);
        await audit("login", user.id, { ip });
        return { id: user.id, email: user.email, name: user.name, sv: user.sessionVersion } as { id: string; email: string; name: string };
      },
    }),
  ],
  callbacks: {
    jwt({ token, user }) {
      if (user?.id) {
        token.sub = user.id;
        token.sv = (user as { sv?: number }).sv ?? 0;
      }
      return token;
    },
    session({ session, token }) {
      if (token.sub) session.user.id = token.sub;
      // The session version is checked against the database on every request (see session.ts).
      (session as { sv?: number }).sv = typeof token.sv === "number" ? token.sv : 0;
      return session;
    },
  },
});
