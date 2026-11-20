"use client";

import { create } from "zustand";
import type { AttachmentMeta } from "@/server/lib/attachments";
import { listAttachmentsAction } from "@/server/trip";

/** Files attached to blocks. Loaded with the trip and again whenever the server's copy changes. */
export const useAttachmentStore = create<{ files: AttachmentMeta[] }>(() => ({ files: [] }));

export async function refreshAttachments(): Promise<void> {
  try {
    useAttachmentStore.setState({ files: await listAttachmentsAction() });
  } catch {
    // Kept as they were; the next trip refresh tries again.
  }
}
