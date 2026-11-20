import NextAuth from "next-auth";
import Credentials from "next-auth/providers/credentials";
import { eq } from "drizzle-orm";
import { getDb, schema } from "@/db/client";
import { verifyPassword } from "@/server/lib/passwords";

export const { handlers, auth, signIn, signOut } = NextAuth({
  trustHost: true,
  session: { strategy: "jwt" },
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
      async authorize(credentials) {
        const email = String(credentials?.email ?? "").trim().toLowerCase();
        const password = String(credentials?.password ?? "");
        if (!email || !password) return null;
        const db = await getDb();
        const user = await db.query.users.findFirst({ where: eq(schema.users.email, email) });
        if (!user || user.disabled) return null;
        if (!(await verifyPassword(password, user.passwordHash))) return null;
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
