# Web builder

Three-pane trip workspace: staging cart, day-by-day timeline, and an ingest and co-pilot drawer. Accounts, a database and the sync endpoints for the Android app are built in.

## Run

```bash
npm install
cp .env.example .env.local   # then set AUTH_SECRET
npm run dev
```

Open http://localhost:3000 and create an account. The first account is the admin. A sample Tbilisi trip loads for a new account.

With no database settings the app keeps its data in `local.db`, a SQLite file next to `package.json`. Tables are created on first start.

## Environment

Only `AUTH_SECRET` is required.

| Variable | Needed for |
| --- | --- |
| `AUTH_SECRET` | Signing session cookies. Any long random string |
| `ADMIN_EMAIL` | Optional. The account with this email is the admin. Without it, the first account is |
| `TURSO_DATABASE_URL`, `TURSO_AUTH_TOKEN` | A hosted Turso database. Without them, a local SQLite file |
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

## Accounts

- `/signup` and `/login` use Auth.js with email and password. Passwords are stored as scrypt hashes.
- Each account has one trip. The board saves it about a second after every edit and shows "Saved" in the top bar.
- `/admin` is for admins only: it lists accounts, changes roles, and disables an account. Disabling signs the user out and stops their phones from syncing.

## Phone sync

"Phone" in the top bar creates a 6-digit code and a QR deep link. The code works once and for ten minutes. Paired phones are listed there and can be unpaired.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/pair` | Trades a code for a trip-scoped token. Limited to 10 attempts per IP per 10 minutes |
| `GET /api/trips/{id}/sync?since={rev}` | Rows changed after `rev`, plus ids of deleted rows. `since=0` sends everything |
| `POST /api/trips/{id}/sync/push` | The phone's batch of check-offs, status changes and time shifts |

How edits from both sides meet:

- The phone may change only `status`, `is_checked` and `start_minute`. Anything else is answered `rejected`.
- `start_minute` is compare-and-swap. If the web moved the block meanwhile, the phone gets `conflict` with the server's value and adopts it.
- A resent batch is answered `duplicate` and changes nothing.
- The board fetches phone changes every 20 seconds and when the tab regains focus. If it saves before it has seen them, the server keeps the phone's values for those rows and sends the merged trip back.

The release build of the Android app refuses plain `http`, so pair against an `https` deployment. A debug build can pair with a dev server on the same network.

## Deploying

Any Node host works. Set `AUTH_SECRET`, `TURSO_DATABASE_URL` and `TURSO_AUTH_TOKEN`. On a host with a read-only or temporary disk, such as Vercel, Turso is required because the local file would not persist. Migrations in `drizzle/` run on first start.

After changing `src/db/schema.ts`, run `npm run db:generate` and commit the new file in `drizzle/`.

## Not built yet

- Attachments (tickets and PDFs). The sync answer always has an empty `attachments` list.
- Password reset and email verification.
- YouTube transcripts, opening hours lookup.

## Layout

```
src/lib/          types, time helpers, linker, logistics checks, sample trip
src/store/        trip document store (undo, persistence) and view state
src/db/           Drizzle schema and database client
src/auth.ts       Auth.js setup
src/server/       server actions and the trip, sync and session logic behind them
src/app/api/      Auth.js handler and the three phone sync endpoints
src/components/   workspace UI, sign-in forms, admin table
drizzle/          SQL migrations
```
