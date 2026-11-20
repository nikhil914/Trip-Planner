import { redeemPairingCode } from "@/server/lib/sync-core";
import { answer, clientIp, readJson } from "@/server/lib/sync-http";

export async function POST(request: Request) {
  return answer(async () => redeemPairingCode(await readJson(request), clientIp(request)));
}
