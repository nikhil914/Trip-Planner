import { audit } from "@/server/lib/audit";
import { redeemPairingCode } from "@/server/lib/sync-core";
import { answer, clientIp, readJson } from "@/server/lib/sync-http";

export async function POST(request: Request) {
  return answer(async () => {
    const ip = clientIp(request);
    const body = await readJson(request);
    const paired = await redeemPairingCode(body, ip);
    const name = (body as { device?: { name?: unknown } } | null)?.device?.name;
    await audit("phone_paired", paired.user_id, { detail: typeof name === "string" ? name : null, ip });
    return { trip_id: paired.trip_id, device_id: paired.device_id, sync_token: paired.sync_token, server_time: paired.server_time };
  });
}
