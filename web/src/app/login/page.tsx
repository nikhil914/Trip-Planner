import { AuthForm } from "@/components/account/AuthForm";

export const metadata = { title: "Sign in · Trip Planner" };

export default function LoginPage() {
  return <AuthForm mode="login" />;
}
