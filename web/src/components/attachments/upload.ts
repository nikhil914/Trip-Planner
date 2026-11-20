export const ACCEPT = "application/pdf,image/png,image/jpeg,image/webp,.pdf,.png,.jpg,.jpeg,.webp";
export const MAX_BYTES = 4 * 1024 * 1024;

export interface UploadedFile {
  id: string;
  blockId: string;
  fileName: string;
  mimeType: string;
  sizeBytes: number;
  createdAt: number;
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

/** Posts one file and returns what the server stored, or throws with the server's message. */
export async function uploadFile(url: string, file: File, fields: Record<string, string> = {}): Promise<UploadedFile> {
  if (file.size > MAX_BYTES) throw new Error(`${file.name} is larger than 4 MB.`);
  const body = new FormData();
  for (const [key, value] of Object.entries(fields)) body.set(key, value);
  body.set("file", file);
  const response = await fetch(url, { method: "POST", body });
  const json = (await response.json().catch(() => null)) as (UploadedFile & { error?: string }) | null;
  if (!response.ok || !json) throw new Error(json?.error ?? `Upload failed (${response.status}).`);
  return json;
}
