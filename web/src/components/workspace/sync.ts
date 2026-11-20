"use client";

import { useEffect, useState } from "react";
import { create } from "zustand";
import { refreshAttachments } from "@/components/attachments/store";
import { relink } from "@/lib/linker";
import { sampleDoc } from "@/lib/sample";
import type { TripDoc } from "@/lib/types";
import { loadTripAction, saveTripAction, tripRevAction } from "@/server/trip";
import { useTripStore } from "@/store/trip-store";

export type SaveStatus = "saved" | "saving" | "error";

export const useSyncStore = create<{ status: SaveStatus; error: string | null }>(() => ({ status: "saved", error: null }));

const OWNER_KEY = "trip-planner:owner";
const SAVE_DELAY_MS = 1200;
const RETRY_DELAY_MS = 8000;
const POLL_MS = 20_000;

const pickDoc = ({ trip, days, blocks, items }: TripDoc): TripDoc => ({ trip, days, blocks, items });

/**
 * Keeps the browser's trip and the server's copy in step.
 * Edits save after a short pause. Changes made on a phone are fetched every 20 seconds and when the tab regains focus.
 */
export function useTripSync(userId: string): boolean {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let disposed = false;
    let rev: number | null = null;
    let dirty = false;
    let saving = false;
    let applyingRemote = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    // A server copy replaces local state without entering undo history and without echoing back as a save.
    function applyRemote(doc: TripDoc) {
      const temporal = useTripStore.temporal.getState();
      applyingRemote = true;
      temporal.pause();
      useTripStore.setState(doc);
      temporal.resume();
      applyingRemote = false;
    }

    function schedule(delay: number) {
      clearTimeout(timer);
      timer = setTimeout(save, delay);
    }

    async function save() {
      if (disposed || saving || !dirty) return;
      saving = true;
      dirty = false;
      useSyncStore.setState({ status: "saving", error: null });
      try {
        const result = await saveTripAction(pickDoc(useTripStore.getState()), rev);
        if (disposed) return;
        rev = result.rev;
        // Edits made while the request was in flight are newer than the merged copy, so they are saved first.
        if (result.merged && !dirty) applyRemote(result.merged);
        useSyncStore.setState({ status: dirty ? "saving" : "saved", error: null });
      } catch (e) {
        dirty = true;
        useSyncStore.setState({ status: "error", error: (e as Error).message });
      } finally {
        saving = false;
        if (dirty && !disposed) schedule(useSyncStore.getState().status === "error" ? RETRY_DELAY_MS : SAVE_DELAY_MS);
      }
    }

    async function refresh() {
      if (disposed || dirty || saving || rev === null || document.visibilityState !== "visible") return;
      try {
        const serverRev = await tripRevAction();
        if (disposed || dirty || saving || serverRev === null || serverRev <= rev) return;
        const remote = await loadTripAction();
        if (disposed || dirty || saving || !remote) return;
        rev = remote.rev;
        applyRemote(remote.doc);
        void refreshAttachments();
      } catch {
        // The next poll tries again.
      }
    }

    async function start() {
      await useTripStore.persist.rehydrate();
      let remote: Awaited<ReturnType<typeof loadTripAction>> = null;
      try {
        remote = await loadTripAction();
      } catch (e) {
        useSyncStore.setState({ status: "error", error: (e as Error).message });
      }
      if (disposed) return;
      if (remote) {
        rev = remote.rev;
        applyRemote(remote.doc);
        void refreshAttachments();
      } else {
        // A trip left in this browser by another account is not uploaded into this one.
        let owner: string | null = null;
        try {
          owner = localStorage.getItem(OWNER_KEY);
        } catch {}
        if (owner !== null && owner !== userId) applyRemote(relink(sampleDoc()));
        dirty = true;
        schedule(0);
      }
      try {
        localStorage.setItem(OWNER_KEY, userId);
      } catch {}
      useTripStore.temporal.getState().clear();
      setReady(true);
    }

    const unsubscribe = useTripStore.subscribe((state, prev) => {
      if (applyingRemote) return;
      if (state.trip === prev.trip && state.days === prev.days && state.blocks === prev.blocks && state.items === prev.items) return;
      dirty = true;
      useSyncStore.setState({ status: "saving", error: null });
      schedule(SAVE_DELAY_MS);
    });

    const poll = setInterval(refresh, POLL_MS);
    const onVisible = () => void refresh();
    const onLeave = (e: BeforeUnloadEvent) => {
      if (dirty || saving) e.preventDefault();
    };
    window.addEventListener("focus", onVisible);
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("beforeunload", onLeave);
    void start();

    return () => {
      disposed = true;
      clearTimeout(timer);
      clearInterval(poll);
      unsubscribe();
      window.removeEventListener("focus", onVisible);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("beforeunload", onLeave);
    };
  }, [userId]);

  return ready;
}
