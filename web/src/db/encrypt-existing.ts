import "server-only";
import type { Client, InStatement } from "@libsql/client";
import { encryptBlob, encryptionEnabled, encryptText } from "./field-crypto";

type Kind = "text" | "lookup" | "real" | "blob";

// Every encrypted column, by table. Keep in step with the enc* columns in schema.ts.
const COLUMNS: Record<string, Record<string, Kind>> = {
  users: { email: "lookup", name: "text" },
  trips: { title: "text", destination_label: "text" },
  timeline_blocks: {
    title: "text", subtitle_or_tip: "text", lat: "real", lng: "real", google_place_id: "text", google_maps_uri: "text",
    booking_ref: "text", local_script_name: "text", phonetic_pronunciation: "text",
  },
  contextual_items: {
    title: "text", shop_or_venue_name: "text", price_benchmark_note: "text", lat: "real", lng: "real", google_place_id: "text",
    local_script_text: "text", phonetic_pronunciation: "text",
  },
  attachments: { file_name: "text", data: "blob" },
  devices: { name: "text" },
  connector_keys: { name: "text" },
  invites: { note: "text" },
};

// SQL that is true when the column still holds a plain value.
const plain = (col: string, kind: Kind) =>
  kind === "blob" ? `(${col} IS NOT NULL AND substr(${col}, 1, 4) != X'54504531')` : kind === "real" ? `typeof(${col}) IN ('real', 'integer')` : `(${col} IS NOT NULL AND ${col} NOT LIKE 'enc:v1:%')`;

function seal(value: unknown, kind: Kind): unknown {
  if (value === null || value === undefined) return null;
  if (kind === "blob") return encryptBlob(Buffer.from(value as ArrayBuffer));
  return encryptText(String(value), kind === "lookup");
}

/**
 * Encrypts rows written before DATA_ENCRYPTION_KEY was set. Runs at startup and only touches rows that are still plain,
 * one row at a time for files so a 4 MB blob is never held twice per row in a batch. Returns how many values it sealed.
 */
export async function encryptExistingRows(client: Client): Promise<number> {
  if (!encryptionEnabled()) return 0;
  let sealed = 0;
  for (const [table, columns] of Object.entries(COLUMNS)) {
    for (const [col, kind] of Object.entries(columns)) {
      const pending = await client.execute(`SELECT rowid AS rid FROM ${table} WHERE ${plain(col, kind)}`);
      for (const row of pending.rows) {
        const current = await client.execute({ sql: `SELECT ${col} AS v FROM ${table} WHERE rowid = ?`, args: [row.rid] });
        const stmt: InStatement = { sql: `UPDATE ${table} SET ${col} = ? WHERE rowid = ? AND ${plain(col, kind)}`, args: [seal(current.rows[0]?.v, kind) as never, row.rid] };
        await client.execute(stmt);
        sealed++;
      }
    }
  }
  if (sealed > 0) console.info(`encrypted ${sealed} existing values`);
  return sealed;
}
