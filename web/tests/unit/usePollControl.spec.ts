import { describe, expect, it } from 'vitest'
import { usePollControl } from '../../app/composables/usePollControl'

describe('usePollControl', () => {
  it('defaults to the deploy-configured poll interval (5s), not a hardcoded literal', () => {
    const { intervalMs } = usePollControl()
    const config = useRuntimeConfig()
    expect(config.public.pollIntervalMs).toBe(5000)
    expect(intervalMs.value).toBe(config.public.pollIntervalMs)
  })

  it('pausing still reports 0 regardless of the configured interval', () => {
    const { paused, intervalMs, toggle } = usePollControl()
    const wasPaused = paused.value
    if (!wasPaused) toggle()
    expect(intervalMs.value).toBe(0)
    if (!wasPaused) toggle()
  })
})
