import { createSharedComposable } from '@vueuse/core'

const BASE_INTERVAL_MS = 5000

/**
 * Shared control for the dashboard's live polling. Every polling query reads
 * `intervalMs` for its `pollInterval` so a single toggle pauses/resumes them all.
 */
const _usePollControl = () => {
  const paused = ref(false)
  const intervalMs = computed(() => (paused.value ? 0 : BASE_INTERVAL_MS))

  function toggle() {
    paused.value = !paused.value
  }

  return { paused, intervalMs, toggle }
}

export const usePollControl = createSharedComposable(_usePollControl)
