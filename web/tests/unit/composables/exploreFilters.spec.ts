import { describe, expect, it } from 'vitest'
import { defineComponent, ref } from 'vue'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import type { Agency, EntityFindingCount, Route } from '~/api'
import { useAgencyFilter } from '../../../app/composables/useAgencyFilter'
import { useFindingsFilter } from '../../../app/composables/useFindingsFilter'
import { useExplorePatterns, type Pattern } from '../../../app/composables/useExplorePatterns'
import { useRouteTypeFilter } from '../../../app/composables/useRouteTypeFilter'

describe('useAgencyFilter', () => {
  it('sorts agencies by name and reports multiple', () => {
    const data = ref([{ agencyId: 'b', agencyName: 'Beta' }, { agencyId: 'a', agencyName: 'Alpha' }] as Agency[])
    const { agencies, hasMultiple } = useAgencyFilter(data)
    expect(agencies.value.map((a) => a.agencyId)).toEqual(['a', 'b'])
    expect(hasMultiple.value).toBe(true)
  })
  it('handles undefined data', () => {
    const { agencies, hasMultiple } = useAgencyFilter(ref(undefined))
    expect(agencies.value).toEqual([])
    expect(hasMultiple.value).toBe(false)
  })
})

describe('useRouteTypeFilter', () => {
  it('offers only the types present', async () => {
    const routes = ref([{ routeId: '1', routeType: 3 }, { routeId: '2', routeType: 3 }, { routeId: '3', routeType: 0 }] as Route[])
    let result!: ReturnType<typeof useRouteTypeFilter>
    await mountSuspended(defineComponent({ setup() { result = useRouteTypeFilter(routes); return () => null } }))
    const { types, hasMultiple } = result
    expect(types.value.map((t) => t.type).sort()).toEqual([0, 3])
    expect(hasMultiple.value).toBe(true)
  })
})

describe('useFindingsFilter', () => {
  it('counts entities and filters only when switched on', () => {
    const counts = ref([{ entityId: 'x' }, { entityId: 'y' }] as EntityFindingCount[])
    const { onlyWithFindings, count, keep } = useFindingsFilter(counts)
    expect(count.value).toBe(2)
    expect(keep('z')).toBe(true)
    onlyWithFindings.value = true
    expect(keep('z')).toBe(false)
    expect(keep('x')).toBe(true)
  })
})

describe('useExplorePatterns', () => {
  const patterns = ref([
    { patternKey: 'p1', routeId: 'r1', route: { routeId: 'r1', agencyId: 'A' }, stopPaths: [{ stopId: 's1' }, { stopId: 's2' }] },
    { patternKey: 'p2', routeId: 'r2', route: { routeId: 'r2', agencyId: 'B' }, stopPaths: [{ stopId: 's3' }] },
  ] as unknown as Pattern[])
  it('resolves stops and patterns per agency', () => {
    const { stopIdsForAgency, patternsForAgency } = useExplorePatterns(patterns)
    expect(stopIdsForAgency(null)).toBeNull()
    expect([...stopIdsForAgency('A')!]).toEqual(['s1', 's2'])
    expect(patternsForAgency('B').map((p) => p.patternKey)).toEqual(['p2'])
    expect(patternsForAgency(null)).toHaveLength(2)
  })
})
