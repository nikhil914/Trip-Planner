import { createOpenRouter } from "@openrouter/ai-sdk-provider";

export type ModelChoice = "primary" | "secondary";

export class ModelNotConfiguredError extends Error {}

// One key, every model. Slugs are OpenRouter model ids in "vendor/model" form.
export function resolveModel(choice: ModelChoice = "primary", apiKey?: string) {
  const key = apiKey ?? process.env.OPENROUTER_API_KEY;
  const slug =
    choice === "secondary"
      ? (process.env.OPENROUTER_MODEL_SECONDARY ?? process.env.OPENROUTER_MODEL_PRIMARY)
      : process.env.OPENROUTER_MODEL_PRIMARY;
  if (!key || !slug) throw new ModelNotConfiguredError("Set OPENROUTER_API_KEY and OPENROUTER_MODEL_PRIMARY in web/.env.local");
  return createOpenRouter({ apiKey: key }).chat(slug);
}
