export type KeyParams = Record<string, unknown>

/** Drops `null` / `undefined` fields so equivalent params produce equal keys. */
export function cleanParams(params?: KeyParams | null): KeyParams {
  const out: KeyParams = {}
  if (!params) return out
  for (const [k, v] of Object.entries(params)) {
    if (v !== null && v !== undefined) out[k] = v
  }
  return out
}
