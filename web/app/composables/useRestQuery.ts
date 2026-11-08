import { computed, watch, type Ref } from 'vue'
import { keepPreviousData, useQuery } from '@tanstack/vue-query'
import { queryClient } from '~/api/queryClient'

export interface RestQueryOptions<V> {
  variables: V
  enabled?: boolean
  pollInterval?: number
  fetchPolicy?: string
}

export interface RestQuerySettings {
  /** How long unobserved data stays cached (ms). Pass 0 for data that must never be shown stale, e.g. drafts under edit. */
  gcTime?: number
}

/**
 * Reactive REST read on top of TanStack Query: cached per `[key, variables]`, de-duplicated across
 * components, cancelled through the `AbortSignal`, with optional polling. `key` names the read
 * (use the composable's name); the variables are appended automatically.
 */
export function useRestQuery<T, V>(
  key: string,
  options: () => RestQueryOptions<V>,
  load: (variables: V, signal: AbortSignal) => Promise<T>,
  settings: RestQuerySettings = {},
) {
  const query = useQuery(computed(() => {
    const { variables, enabled, pollInterval } = options()
    return {
      queryKey: [key, variables] as const,
      queryFn: ({ queryKey, signal }: { queryKey: readonly [string, unknown], signal: AbortSignal }) => load(queryKey[1] as V, signal),
      enabled: enabled !== false,
      refetchInterval: pollInterval && pollInterval > 0 ? pollInterval : false,
      refetchIntervalInBackground: true,
      // Keep showing the previous variables' result while the next ones load, as before.
      placeholderData: keepPreviousData,
      // Every response is a fresh object, so watchers on `result` re-run per poll.
      structuralSharing: false,
      gcTime: settings.gcTime,
    }
  }), queryClient)

  const error = computed(() => (query.error.value instanceof Error ? query.error.value : null))
  const listeners = new Set<(error: Error) => void>()
  watch(error, (failure) => {
    if (failure) for (const listener of listeners) listener(failure)
  })

  return {
    result: query.data as Readonly<Ref<T | undefined>>,
    loading: query.isFetching,
    error,
    onError: (listener: (error: Error) => void) => { listeners.add(listener); return () => listeners.delete(listener) },
    /** Re-read now (cancelling a read in flight). Resolves to the fresh result, or `undefined` if it failed. */
    refetch: async (): Promise<{ result: T } | undefined> => {
      const outcome = await query.refetch()
      return outcome.isError || outcome.data === undefined ? undefined : { result: outcome.data as T }
    },
  }
}
