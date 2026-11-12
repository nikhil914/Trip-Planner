# Field Guide (Android companion)

Offline day-of-travel companion for a trip planned in the web builder. No location permission, no map SDK, no analytics.

## What is in it

- **Room database** (`data/`): trips, days, timeline blocks, contextual items, attachments and the mutation outbox. Today's agenda, the delay cascade and the collision check are single SQL statements in `ItineraryDao`.
- **Sync** (`sync/`): pairing by code or `fieldguide://pair?u=<server>&c=<code>` deep link, pull by `rev` cursor, and `OutboxWorker`, which pushes folded check-offs, status changes and time shifts in idempotent batches.
- **Morning briefing** (`briefing/`): one exact alarm at each day's wake time, re-armed after boot, app update and clock or timezone changes. Falls back to an inexact alarm when the exact-alarm permission is not granted.
- **Screens** (`ui/`): pairing, today (briefing header, delay bar, agenda cards with linked items), flashcard and ticket dialogs, settings.
- **Navigation** (`nav/`): hands a place to Google Maps by place id, with a `geo:` fallback.

The phone only ever writes three fields: `status`, `is_checked` and `start_minute`. Everything else is read-only and comes from the server.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```sh
cd android
./gradlew :app:testDebugUnitTest      # logic tests, no device needed
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug
./gradlew :app:assembleRelease        # minified, signed with the debug key
```

The `Android` workflow in `.github/workflows/android.yml` runs the tests and the release build on every push that touches `android/`, prints the APK size and uploads the APK as a build artifact.

## Try it without a server

The sync endpoints (`/api/pair`, `/api/trips/{id}/sync`, `/api/trips/{id}/sync/push`) are not built yet. On the pairing screen choose **Try the demo trip** to load a three-day sample that starts today. Check-offs and delays work locally and queue in the outbox.

## Settings

- **Sync on Wi-Fi only**: off by default, so changes also sync on mobile data.
- **Exact alarms**: on Android 12 and later the briefing needs the "Alarms & reminders" permission to fire on the minute. Settings links straight to that screen.

## Not done yet

- Release signing. The release build uses the debug key so a CI build installs directly.
- Database migrations. The schema is version 1 with no migrations; add them before the schema changes after a real release.
- Instrumented tests for the DAO and the worker.
