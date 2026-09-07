/**
 * GTFS-style time helpers.
 *
 * GTFS stop times are stored as an integer number of seconds after noon minus
 * 12h (effectively "seconds since midnight of the service day"), and hours may
 * exceed 24 for trips that run past midnight.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * parseTimeInput(raw, base) — accepted forms (this doc IS the spec):
 *
 * Absolute clock (a colon is REQUIRED; `base` is ignored):
 *   "6:30"       → 23400        (H:MM)
 *   "06:30"      → 23400        (HH:MM)
 *   "06:30:15"   → 23415        (HH:MM:SS)
 *   "25:00"      → 90000        (hours may exceed 24)
 *   "1:2:3"      → 3723         (single-digit MM/SS tolerated on input)
 *   MM and SS must each be 0-59; hours must be >= 0.
 *   A bare integer with no colon (e.g. "630", "23400") is NOT supported
 *   (ambiguous) and returns null.
 *
 * Relative delta (MUST start with '+' or '-'; requires base != null,
 * otherwise returns null). Result is `base ± delta`:
 *   "+90"        → base + 90     (bare number = seconds)
 *   "-90"        → base - 90
 *   "+15m"       → base + 900    (Nm / N m = minutes)
 *   "-2m"        → base - 120
 *   "+1:30"      → base + 90     (mm:ss delta)
 *   "-1:30"      → base - 90
 *   "+1:05:00"   → base + 3900   (h:mm:ss delta)
 *   mm/ss components in a relative delta must each be 0-59.
 *
 * Any other input (empty, letters, malformed) → null.
 * The result is clamped to be >= 0 (a negative result returns 0).
 * ─────────────────────────────────────────────────────────────────────────────
 */

const ABSOLUTE_RE = /^(\d{1,2}):([0-5]?\d)(?::([0-5]?\d))?$/;
const REL_SECONDS_RE = /^([+-])(\d+)$/;
const REL_MINUTES_RE = /^([+-])(\d+)\s*m$/i;
const REL_MMSS_RE = /^([+-])(\d{1,3}):([0-5]?\d)(?::([0-5]?\d))?$/;

/**
 * Parse a user time input into GTFS seconds.
 * @param raw   the raw string from the input field
 * @param base  the reference time in seconds for relative deltas, or null
 * @returns seconds (>= 0) or null when unparseable
 */
export function parseTimeInput(raw: string, base: number | null): number | null {
  const s = (raw ?? "").trim();
  if (!s) return null;

  const isRelative = s[0] === "+" || s[0] === "-";

  if (isRelative) {
    if (base == null || !Number.isFinite(base)) return null;

    let delta: number | null = null;

    const mmss = REL_MMSS_RE.exec(s);
    const mins = REL_MINUTES_RE.exec(s);
    const secs = REL_SECONDS_RE.exec(s);

    if (mmss) {
      const a = Number(mmss[2]);
      const b = Number(mmss[3]);
      const c = mmss[4] != null ? Number(mmss[4]) : null;
      if (c != null) {
        // h:mm:ss
        if (b > 59 || c > 59) return null;
        delta = a * 3600 + b * 60 + c;
      } else {
        // mm:ss
        if (b > 59) return null;
        delta = a * 60 + b;
      }
      if (mmss[1] === "-") delta = -delta;
    } else if (mins) {
      delta = Number(mins[2]) * 60;
      if (mins[1] === "-") delta = -delta;
    } else if (secs) {
      delta = Number(secs[2]);
      if (secs[1] === "-") delta = -delta;
    }

    if (delta == null || !Number.isFinite(delta)) return null;
    return Math.max(0, Math.round(base + delta));
  }

  const abs = ABSOLUTE_RE.exec(s);
  if (!abs) return null;
  const h = Number(abs[1]);
  const m = Number(abs[2]);
  const sec = abs[3] != null ? Number(abs[3]) : 0;
  if (!Number.isFinite(h) || h < 0 || m > 59 || sec > 59) return null;
  return h * 3600 + m * 60 + sec;
}

/**
 * Format GTFS seconds back into a clock string.
 *
 *   null   → ""
 *   0      → "00:00"
 *   23400  → "06:30"      (seconds omitted when zero; H and M zero-padded)
 *   23415  → "06:30:15"   (seconds shown when non-zero)
 *   90000  → "25:00"      (hours may exceed 24)
 */
export function secToClock(sec: number | null): string {
  if (sec == null || !Number.isFinite(sec)) return "";
  const total = Math.max(0, Math.round(sec));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const hh = String(h).padStart(2, "0");
  const mm = String(m).padStart(2, "0");
  if (s === 0) return `${hh}:${mm}`;
  return `${hh}:${mm}:${String(s).padStart(2, "0")}`;
}
