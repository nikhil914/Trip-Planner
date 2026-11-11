# Web builder

Three-pane trip workspace: staging cart, day-by-day timeline, and an ingest and co-pilot drawer.

## Run

```bash
npm install
cp .env.example .env.local   # optional, see below
npm run dev
```

Open http://localhost:3000. A sample Tbilisi trip loads on first run.

## Environment

Everything works without keys except the two model features.

| Variable | Needed for |
| --- | --- |
| `OPENROUTER_API_KEY` | Co-Pilot suggestions and blog extraction |
| `OPENROUTER_MODEL_PRIMARY` | Model slug for gap filling, in `vendor/model` form |
| `OPENROUTER_MODEL_SECONDARY` | Optional cheaper slug for blog extraction |
| `GOOGLE_PLACES_API_KEY` | Google place ids. Without it, search falls back to OpenStreetMap |

## Using the board

- Drag a card from the cart onto a day. Its top edge sets the start time, snapped to 15 minutes.
- Drag a To-Buy or Food card onto a scheduled activity to link it by hand.
- Items with a location link themselves to the nearest scheduled stop within 800 m.
- Drag anything back to the cart to unschedule or unlink it.

| Key | Action |
| --- | --- |
| `Cmd/Ctrl+K` | Command palette |
| `Cmd/Ctrl+Z`, `Shift+Cmd/Ctrl+Z` | Undo, redo |
| `1` `2` `3` | Cart tabs |
| `[` `]` | Previous, next day |
| `Up` `Down` | Move the selected block by 15 minutes |
| `F` | Toggle fixed time on the selected block |
| `Backspace` | Delete the selected block |

## Not built yet

- Database, sign-in, and phone sync. The trip is saved in this browser's localStorage.
- Server actions are unauthenticated. Do not deploy publicly until sign-in lands.
- YouTube transcripts, opening hours lookup, attachments.

## Layout

```
src/lib/          types, time helpers, linker, logistics checks, sample trip
src/store/        trip document store (undo, persistence) and view state
src/server/       server actions: link unroller, place search, co-pilot
src/components/   workspace UI
```
