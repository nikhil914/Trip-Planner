import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  cacheComponents: true,
  // The SQL migrations are read from disk at startup, so they must ship with the server bundle.
  outputFileTracingIncludes: { "/**": ["./drizzle/**"] },
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
