import { onScopeDispose, ref, shallowRef, watch } from 'vue'

export interface RestQueryOptions<V> {
  variables: V
  enabled?: boolean
  pollInterval?: number
  fetchPolicy?: string
}

/** Reactive REST read with cancellation, error state, and optional polling. */
export function useRestQuery<T, V>(options: () => RestQueryOptions<V>, load: (variables: V, signal: AbortSignal) => Promise<T>) {
  const result = shallowRef<T | undefined>()
  const error = shallowRef<Error | null>(null)
  const loading = ref(false)
  let controller: AbortController | undefined
  let timer: ReturnType<typeof setTimeout> | undefined
  let disposed = false
  const listeners = new Set<(error: Error) => void>()

  const refresh = async (variables: V, interval: number) => {
    if (timer) clearTimeout(timer)
    controller?.abort()
    const request = new AbortController()
    controller = request
    loading.value = true
    try {
      const data = await load(variables, request.signal)
      if (!request.signal.aborted) { result.value = data; error.value = null }
      return { result: data }
    } catch (cause) {
      if (!request.signal.aborted) {
        error.value = cause instanceof Error ? cause : new Error(String(cause))
        for (const listener of listeners) listener(error.value)
      }
      return undefined
    } finally {
      if (!request.signal.aborted) {
        loading.value = false
        if (!disposed && interval > 0) timer = setTimeout(() => void refresh(variables, interval), interval)
      }
    }
  }

  watch(options, (next, _previous, cleanup) => {
    if (timer) clearTimeout(timer)
    controller?.abort()
    cleanup(() => { controller?.abort(); if (timer) clearTimeout(timer) })
    if (next.enabled === false) { loading.value = false; return }
    void refresh(next.variables, next.pollInterval ?? 0)
  }, { deep: true, immediate: true })
  onScopeDispose(() => { disposed = true; controller?.abort(); if (timer) clearTimeout(timer) })
  return {
    result, loading, error,
    onError: (listener: (error: Error) => void) => { listeners.add(listener); return () => listeners.delete(listener) },
    refetch: (variables?: Partial<V>) => refresh(Object.assign({}, options().variables, variables) as V, options().pollInterval ?? 0),
  }
}
