// Parses ALLOWED_IPS ("1.2.3.4, 10.0.0.0/24, 2001:db8::1") and checks an address against it.
// IPv4 entries may be CIDR ranges. IPv6 entries must match exactly.

export interface Allowlist {
  exact: Set<string>;
  ranges: { base: number; mask: number }[];
}

const normalize = (ip: string) => ip.trim().toLowerCase().replace(/^::ffff:(?=\d+\.\d+\.\d+\.\d+$)/, "");

function ipv4ToInt(ip: string): number | null {
  const parts = ip.split(".");
  if (parts.length !== 4) return null;
  let n = 0;
  for (const p of parts) {
    if (!/^\d{1,3}$/.test(p) || Number(p) > 255) return null;
    n = n * 256 + Number(p);
  }
  return n >>> 0;
}

/** Returns null when the variable is empty, which means the gate is off. */
export function parseAllowlist(raw: string | undefined): Allowlist | null {
  const entries = (raw ?? "").split(/[\s,]+/).map(normalize).filter(Boolean);
  if (entries.length === 0) return null;
  const list: Allowlist = { exact: new Set(), ranges: [] };
  for (const entry of entries) {
    const [addr, bits] = entry.split("/");
    const v4 = ipv4ToInt(addr);
    if (bits !== undefined && v4 !== null && /^\d{1,2}$/.test(bits) && Number(bits) <= 32) {
      const mask = Number(bits) === 0 ? 0 : (~0 << (32 - Number(bits))) >>> 0;
      list.ranges.push({ base: (v4 & mask) >>> 0, mask });
    } else {
      list.exact.add(addr);
    }
  }
  return list;
}

export function isAllowed(list: Allowlist, ip: string | null): boolean {
  if (!ip) return false;
  const addr = normalize(ip);
  if (list.exact.has(addr)) return true;
  const v4 = ipv4ToInt(addr);
  return v4 !== null && list.ranges.some((r) => ((v4 & r.mask) >>> 0) === r.base);
}
