# Trip Planner

Plan a trip on desktop, then follow it offline on Android with no location or background battery use.

Two parts:

- **Web builder** (`web/`): Next.js workspace for collecting places, parsing map links and blogs, and dragging activities, shopping items and food stops into a day-by-day timeline.
- **Android companion** (`android/`): offline Kotlin + Compose + Room app that shows the day plan, morning checklist, nearby errands, local-script flashcards and one-tap navigation.

## Status

- Web: workspace, accounts with an admin page, database, and the phone sync endpoints. See [web/README.md](web/README.md).
- Android: offline app that pairs with the web app, pulls the trip and pushes check-offs and delays. See [android/README.md](android/README.md). Signed APKs are published under Releases.
- Not built yet: ticket and PDF attachments, password reset.

## Docs

- [Architecture blueprint](docs/architecture-blueprint.md): edge-case audit, sync protocol contracts, Drizzle schema, server actions, Room entities, DAO and Compose screens.
- Decisions taken so far are in section 5 of the blueprint.

## Stack

| Part | Choice |
| --- | --- |
| Web | Next.js App Router, TypeScript, Tailwind, shadcn/ui, dnd-kit, Zustand + zundo |
| Database | Turso (LibSQL) with Drizzle |
| Auth | Auth.js, `users.role` of `ADMIN` or `USER` |
| Models | OpenRouter, one `OPENROUTER_API_KEY` |
| Places | Google Places API (New), OSM fallback |
| Android | Kotlin, Jetpack Compose, Room, WorkManager, OkHttp |

## Layout

```
docs/      design documents
web/       Next.js app
android/   Android app
```
