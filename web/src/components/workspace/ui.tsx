import type { ButtonHTMLAttributes, InputHTMLAttributes } from "react";

export const cn = (...parts: (string | false | null | undefined)[]) => parts.filter(Boolean).join(" ");

export function Button({ className, variant = "ghost", ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "ghost" | "solid" | "danger" }) {
  return (
    <button
      type="button"
      {...props}
      className={cn(
        "inline-flex h-7 items-center justify-center gap-1 rounded-md px-2 text-xs font-medium transition-colors disabled:opacity-40",
        variant === "ghost" && "border border-neutral-800 text-neutral-300 hover:bg-neutral-800",
        variant === "solid" && "bg-amber-400 text-black hover:bg-amber-300",
        variant === "danger" && "border border-red-900 text-red-400 hover:bg-red-950",
        className,
      )}
    />
  );
}

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={cn(
        "h-8 w-full rounded-md border border-neutral-800 bg-neutral-950 px-2 text-sm text-neutral-100 placeholder:text-neutral-600 focus:border-amber-400 focus:outline-none",
        className,
      )}
    />
  );
}

export function Kbd({ children }: { children: React.ReactNode }) {
  return <kbd className="rounded border border-neutral-700 bg-neutral-900 px-1 font-mono text-[10px] text-neutral-400">{children}</kbd>;
}

export const TYPE_COLORS: Record<string, string> = {
  ACTIVITY: "border-l-amber-400",
  TRANSIT: "border-l-sky-400",
  HOTEL: "border-l-violet-400",
  FOOD_STOP: "border-l-emerald-400",
};
