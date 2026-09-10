import type { LocationQueryValue } from 'vue-router'

/**
 * Read/write string filters in the URL query, the way the Explore section keeps
 * its state (`?agency=`, `?type=`, `?dir=`, …) so filters survive navigation and
 * are shareable. Writes use `router.replace` — they don't add history entries.
 */
export function useExploreQuery() {
  const route = useRoute()
  const router = useRouter()

  const read = (v: LocationQueryValue | LocationQueryValue[] | undefined) =>
    typeof v === 'string' && v ? v : null

  function get(key: string): string | null {
    return read(route.query[key])
  }

  function set(key: string, value: string | null) {
    // `undefined` drops the param from the URL; avoids a dynamic `delete`.
    router.replace({ query: { ...route.query, [key]: value || undefined } })
  }

  /** Reactive, writable view of one query param. */
  function param(key: string) {
    return computed<string | null>({
      get: () => get(key),
      set: (value) => set(key, value),
    })
  }

  return { get, set, param }
}
