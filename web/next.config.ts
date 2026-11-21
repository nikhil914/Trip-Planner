import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  cacheComponents: true,
  // The SQL migrations are read from disk at startup, so they must ship with the server bundle.
  outputFileTracingIncludes: { "/**": ["./drizzle/**"] },
  partialPrefetching: true,
  // Private site: keep it out of search engines, never framed, and never leak secret URLs (upload links) in the Referer.
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "X-Robots-Tag", value: "noindex, nofollow" },
          { key: "Referrer-Policy", value: "no-referrer" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
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
