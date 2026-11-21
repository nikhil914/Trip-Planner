import "server-only";
import type { Client } from "@libsql/client";

/**
 * Removes ticket files and booking references from trips that ended more than RETENTION_DAYS ago (default 30).
 * Data that is gone cannot leak. The trip, its days and blocks stay. Phones learn of the change through the
 * usual revs and tombstones on their next pull. RETENTION_DAYS=0 turns this off.
 */
export async function purgeEndedTrips(client: Client): Promise<number> {
  const days = Number(process.env.RETENTION_DAYS ?? 30);
  if (!Number.isFinite(days) || days <= 0) return 0;
  const cutoff = new Date(Date.now() - days * 86_400_000).toISOString().slice(0, 10);
  const due = await client.execute({
    sql: `SELECT id, rev FROM trips WHERE end_date < ? AND (
            EXISTS (SELECT 1 FROM attachments a WHERE a.trip_id = trips.id) OR
            EXISTS (SELECT 1 FROM timeline_blocks b WHERE b.trip_id = trips.id AND b.booking_ref IS NOT NULL))`,
    args: [cutoff],
  });
  for (const row of due.rows) {
    const tripId = String(row.id);
    const rev = Number(row.rev) + 1;
    const now = Date.now();
    await client.batch(
      [
        {
          sql: `INSERT INTO tombstones (trip_id, entity, entity_id, rev, deleted_at)
                SELECT trip_id, 'attachment', id, ?, ? FROM attachments WHERE trip_id = ?
                ON CONFLICT (trip_id, entity, entity_id) DO UPDATE SET rev = excluded.rev, deleted_at = excluded.deleted_at`,
          args: [rev, now, tripId],
        },
        { sql: "DELETE FROM attachments WHERE trip_id = ?", args: [tripId] },
        { sql: "UPDATE timeline_blocks SET booking_ref = NULL, rev = ?, updated_at = ? WHERE trip_id = ? AND booking_ref IS NOT NULL", args: [rev, now, tripId] },
        { sql: "UPDATE trips SET rev = ?, updated_at = ? WHERE id = ?", args: [rev, now, tripId] },
      ],
      "write",
    );
  }
  if (due.rows.length > 0) console.info(`removed tickets and booking refs from ${due.rows.length} ended trip(s)`);
  return due.rows.length;
}
