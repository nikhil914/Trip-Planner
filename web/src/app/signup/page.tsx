import { AuthForm } from "@/components/account/AuthForm";

export const metadata = { title: "Create account · Trip Planner" };

export default function SignUpPage() {
  return <AuthForm mode="signup" />;
}
