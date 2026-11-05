import { gunzipSync, strFromU8 } from 'fflate'

/**
 * Decode the backend's compact GeoJSON form — `base64(gzip(utf8 GeoJSON))` — synchronously, so it
 * can be used inside computed properties.
 */
export function decodeGeoJson<T = unknown>(encoded: string): T {
  const bytes = Uint8Array.from(atob(encoded), (c) => c.charCodeAt(0))
  return JSON.parse(strFromU8(gunzipSync(bytes))) as T
}
