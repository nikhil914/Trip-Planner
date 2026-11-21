# Web builder

Three-pane trip workspace: staging cart, day-by-day timeline, and an ingest and co-pilot drawer. Accounts, a database and the sync endpoints for the Android app are built in.

## Run

```bash
npm install
cp .env.example .env.local   # then set AUTH_SECRET
npm run dev
```

Open http://localhost:3000 and create an account. The first account is the admin. After that, sign-up needs an invite link from `/admin`. A sample Tbilisi trip loads for a new account.

With no database settings the app keeps its data in `local.db`, a SQLite file next to `package.json`. Tables are created on first start.

## Environment

Only `AUTH_SECRET` is required.

| Variable | Needed for |
| --- | --- |
| `AUTH_SECRET` | Signing session cookies. Any long random string |
| `ADMIN_EMAIL` | Optional. This email may sign up as admin without an invite while the site has no admin. Without it, the first account is |
| `TURSO_DATABASE_URL`, `TURSO_AUTH_TOKEN` | A hosted Turso database. Without them, a local SQLite file |
| `DATA_ENCRYPTION_KEY` | Encrypts personal data in the database. 32 random bytes in base64 (`openssl rand -base64 32`). Back it up: without it the encrypted data cannot be read |
| `ALLOWED_IPS` | Optional VPN gate. Comma-separated IPs or IPv4 CIDR ranges. When set, every other address gets a plain 404, except the MCP connector at `/api/mcp/*`. Unset means the gate is off |
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
- **Field encryption** (`src/db/field-crypto.ts`): with `DATA_ENCRYPTION_KEY` set, personal fields are encrypted with AES-256-GCM before they reach the database: names and emails, trip title and destination, block and item titles, notes, booking refs, venue names, local names, map coordinates and place ids, device and connector names, invite notes, and attachment file names and contents. Ids, dates, times, timezones, currencies and costs stay plain so the app can sort and sync. Email uses deterministic encryption so sign-in can look it up. At startup, rows still in plain text are encrypted in place. Changing or losing the key makes the data unreadable; there is no rotation yet.
- **VPN gate** (`src/proxy.ts`): with `ALLOWED_IPS` set, only those addresses reach pages, sign-in, phone sync and upload links. On Vercel the client IP comes from `x-real-ip`, which the platform sets. `/api/mcp/*` stays reachable because the assistant calls it from its own servers, and its secret URL is the credential. If the VPN server goes down, add your current IP to `ALLOWED_IPS` (or clear it) and redeploy.
- `/admin` is for admins only: it lists accounts, changes roles, and disables an account. Disabling signs the user out and stops their phones from syncing.
- Sign-up is closed. Only the first account, or someone holding an invite link, can create an account. An admin creates invite links on `/admin`. Each link works once and expires after 7 days. Only its hash is stored.
- After 10 wrong passwords from one IP address within 15 minutes, sign-in from that address is refused for 15 minutes. Other addresses are not affected.
- Every response carries `X-Robots-Tag: noindex`, `Referrer-Policy: no-referrer` (so upload links never leak in the Referer), `X-Frame-Options: DENY`, `nosniff` and HSTS. `robots.txt` disallows everything.

## Phone sync

"Phone" in the top bar creates a 6-digit code and a QR deep link. The code works once and for ten minutes. Paired phones are listed there and can be unpaired.

| Endpoint | Purpose |
| --- | --- |
| `POST /api/pair` | Trades a code for a trip-scoped token. Limited to 10 attempts per IP per 10 minutes |
| `GET /api/trips/{id}/sync?since={rev}` | Rows changed after `rev`, plus ids of deleted rows. `since=0` sends everything |
| `POST /api/trips/{id}/sync/push` | The phone's batch of check-offs, status changes and time shifts |
| `GET /api/trips/{id}/attachments/{file}` | One attached file, for the phone's token or the signed-in owner |

How edits from both sides meet:

- The phone may change only `status`, `is_checked` and `start_minute`. Anything else is answered `rejected`.
- `start_minute` is compare-and-swap. If the web moved the block meanwhile, the phone gets `conflict` with the server's value and adopts it.
- A resent batch is answered `duplicate` and changes nothing.
- The board fetches phone changes every 20 seconds and when the tab regains focus. If it saves before it has seen them, the server keeps the phone's values for those rows and sends the merged trip back.

The release build of the Android app refuses plain `http`, so pair against an `https` deployment. A debug build can pair with a dev server on the same network.

## Tickets and vouchers

Select a block and use "Attach file" in the side panel. PDF, PNG, JPEG and WebP files up to 4 MB each are stored in the database, at most 10 per block. The type is read from the file's bytes, not its name. A paired phone downloads them on its next pull and opens them offline. Deleting a block deletes its files.

An upload link (`/upload/{token}`) lets someone attach files to one block without signing in. It works for 24 hours. The connector creates these.

## Connector (MCP)

"Connector" in the top bar creates a secret URL of the form `/api/mcp/tpk_…`. Add it as a custom connector (remote MCP server URL, no authentication) in a chat app that supports MCP. The URL is the credential, so treat it like a password. It is shown once, only its hash is stored, and it can be revoked from the same dialog.

| Tool | What it does |
| --- | --- |
| `get_trip` | Dates, currencies and every block by day, with ids, booking details and attached files |
| `set_trip` | Creates the trip or changes title, destination, dates, timezone and currencies. Bookings keep their calendar dates when the range moves |
| `add_booking` | Adds a flight, train, hotel check-in or check-out, tour or restaurant booking at a local date and time, with reference, notes, cost and an optional place lookup for the map pin |
| `update_block` | Changes or moves a block, or takes it off the timeline |
| `remove_block` | Deletes a block and its files |
| `create_upload_link` | Returns a 24-hour upload link for one block, for ticket files the chat app cannot send itself |
| `remove_file` | Deletes one attached file |

The board picks up connector changes on its 20-second refresh or when the tab regains focus. Set `PUBLIC_URL` if the links the connector hands out should use a different domain than the one it was called on.

## Deploying

Any Node host works. Set `AUTH_SECRET`, `TURSO_DATABASE_URL` and `TURSO_AUTH_TOKEN`. On a host with a read-only or temporary disk, such as Vercel, Turso is required because the local file would not persist. Migrations in `drizzle/` run on first start.

After changing `src/db/schema.ts`, run `npm run db:generate` and commit the new file in `drizzle/`.

## Not built yet

- Password reset and email verification.
- YouTube transcripts, opening hours lookup.

## Layout

```
src/lib/          types, time helpers, linker, logistics checks, sample trip
src/store/        trip document store (undo, persistence) and view state
src/db/           Drizzle schema and database client
src/auth.ts       Auth.js setup
src/server/       server actions and the trip, sync and session logic behind them
src/app/api/      Auth.js handler, phone sync, attachments, upload links and the MCP endpoint
src/components/   workspace UI, sign-in forms, admin table
drizzle/          SQL migrations
```
