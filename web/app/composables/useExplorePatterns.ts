import { useExplorePatternsQuery, type ExplorePatternsQuery } from '../../generated/graphql'
import { useFeeds } from './useFeeds'

export type Pattern = ExplorePatternsQuery['tripPatterns'][number]

/**
 * The feed's trip patterns (route → ordered stops with typical segment timing).
 * Heavy-ish, so pass a reactive `enabled` guard; Apollo caches it across the
 * Explore section.
 */
export function useExplorePatterns(enabled: Ref<boolean>) {
  const { selectedFeedCode } = useFeeds()

  const { result, loading } = useExplorePatternsQuery(
    () => ({ feedCode: selectedFeedCode.value ?? '' }),
    () => ({ enabled: enabled.value && !!selectedFeedCode.value }),
  )

  const patterns = computed<Pattern[]>(() => result.value?.tripPatterns ?? [])

  /** stop ids served by the given agency (null → every stop) */
  function stopIdsForAgency(agencyId: string | null): Set<string> | null {
    if (!agencyId) return null
    const ids = new Set<string>()
    for (const p of patterns.value) {
      if (p.route?.agencyId !== agencyId) continue
      for (const sp of p.stopPaths) ids.add(sp.stopId)
    }
    return ids
  }

  /** patterns whose route belongs to the agency (null → all patterns) */
  function patternsForAgency(agencyId: string | null): Pattern[] {
    if (!agencyId) return patterns.value
    return patterns.value.filter((p) => p.route?.agencyId === agencyId)
  }

  return { patterns, loading, stopIdsForAgency, patternsForAgency }
}
