import { createSharedComposable } from '@vueuse/core'

/**
 * Shared control for the dashboard's live polling. Every polling query reads
 * `intervalMs` for its `pollInterval` so a single toggle pauses/resumes them all.
 * The base interval is deploy-configured via `runtimeConfig.public.pollIntervalMs`
 * (NUXT_PUBLIC_POLL_INTERVAL_MS), not hardcoded.
 */
const _usePollControl = () => {
  const baseIntervalMs = useRuntimeConfig().public.pollIntervalMs
  const paused = ref(false)
  const intervalMs = computed(() => (paused.value ? 0 : baseIntervalMs))

  function toggle() {
    paused.value = !paused.value
  }

  return { paused, intervalMs, toggle }
}

export const usePollControl = createSharedComposable(_usePollControl)
