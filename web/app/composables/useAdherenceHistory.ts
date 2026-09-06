export interface AdherencePoint {
  t: number
  sec: number
}

/**
 * Accumulates a short, session-local history of a vehicle's schedule adherence
 * as it is re-polled. Resets when the source key (vehicle id) changes.
 */
export function useAdherenceHistory(
  sec: Ref<number | null | undefined>,
  key: Ref<string>,
  cap = 40,
) {
  const points = ref<AdherencePoint[]>([])

  watch(key, () => {
    points.value = []
  })

  watch(
    sec,
    (value) => {
      if (value == null) return
      const last = points.value[points.value.length - 1]
      if (last && last.sec === value) return
      points.value = [...points.value, { t: Date.now(), sec: value }].slice(-cap)
    },
    { immediate: true },
  )

  return { points }
}
