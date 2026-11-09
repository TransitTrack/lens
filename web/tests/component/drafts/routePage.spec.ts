import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mountSuspended, mockNuxtImport } from '@nuxt/test-utils/runtime'
import { h, ref } from 'vue'
import { UApp } from '#components'
import DraftRoutePage from '../../../app/pages/[feedCode]/drafts/[draftId]/[routeId].vue'
import DraftGrid from '../../../app/components/draft/DraftGrid.vue'
import DraftRail from '../../../app/components/draft/DraftRail.vue'
import { useEditorIdentity } from '../../../app/composables/useEditorIdentity'
import { withFeedContext } from '../../helpers/feedContext'
import { json, page, stubFetch, type SeenRequest } from '../../helpers/stubFetch'

mockNuxtImport('useRoute', () => () => ({
  params: { feedCode: 'g', draftId: 'D1', routeId: 'R1' },
  query: { dir: '0' },
  path: '/g/drafts/D1/R1',
}))

async function settle() {
  for (let i = 0; i < 12; i++) await vi.advanceTimersByTimeAsync(0)
}

const lock = { editor: 'alice', expiresAt: '2026-11-09T10:15:00Z' }
const draftRow = (version: number) => ({
  id: 'D1', feedCode: 'g', status: 'DRAFT', kind: 'SCHEDULE', version, derivationStale: false,
  createdAt: '2026-11-09T10:00:00Z', lock,
})
const grid = {
  stops: [{ stopSequence: 1, stopId: 'S1', stopName: 'First' }],
  trips: [{ tripId: 'T1', firstDepartureSec: 25_200, cells: [{ stopSequence: 1, arrivalSec: 25_200, departureSec: 25_200 }] }],
}

const pathOf = (c: SeenRequest) => new URL(c.url).pathname
const isGrid = (c: SeenRequest) => pathOf(c).endsWith('/drafts/D1/grid')

describe('draft route editor page', () => {
  let version: number

  beforeEach(() => {
    useCookie('tt-demo-authenticated').value = 'true'
    vi.useFakeTimers({ shouldAdvanceTime: true })
    useEditorIdentity().name.value = 'alice'
    version = 3
  })
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  async function mountPage() {
    const { calls } = stubFetch(({ url, method }) => {
      const path = new URL(url).pathname
      if (path.endsWith('/drafts/D1') && method === 'GET') return json(draftRow(version))
      if (path.endsWith('/drafts/D1/grid')) return json(grid)
      if (path.endsWith('/drafts/D1/edits')) return json([])
      if (path.includes('/editor-lock/')) return json(lock)
      if (path.endsWith('/draft-edit-commands/update-stop-time') || path.endsWith('/drafts/D1/undo')) {
        version += 1
        return json({ draft: draftRow(version), canUndo: true, canRedo: true, edit: null })
      }
      return page([])
    })
    const wrapper = await mountSuspended(
      { render: () => h(UApp, null, () => h(DraftRoutePage)) },
      withFeedContext({ selectedFeedCode: ref<string | null>('g') }),
    )
    await settle()
    return { calls, wrapper }
  }

  it('reloads the grid exactly once after a successful stop-time edit', async () => {
    const { calls, wrapper } = await mountPage()
    expect(calls.filter(isGrid)).toHaveLength(1)

    wrapper.findComponent(DraftGrid).vm.$emit('commit-cell', { tripId: 'T1', stopSequence: 1, arrivalSec: 25_260, departureSec: 25_260 })
    await settle()

    const edit = calls.findIndex((c) => pathOf(c).endsWith('/update-stop-time'))
    expect(edit).toBeGreaterThan(-1)
    expect(calls[edit]!.body).toEqual({
      draftId: 'D1', editor: 'alice', expectedVersion: 3, tripId: 'T1', stopSequence: 1, arrivalSec: 25_260, departureSec: 25_260,
    })
    const after = calls.slice(edit + 1)
    expect(after.filter(isGrid)).toHaveLength(1)
    // The other draft reads refresh once too: the detail, and the two edit-history windows (500 / 200).
    expect(after.filter((c) => pathOf(c).endsWith('/drafts/D1') && c.method === 'GET')).toHaveLength(1)
    expect(after.filter((c) => pathOf(c).endsWith('/drafts/D1/edits')).map((c) => new URL(c.url).searchParams.get('limit')).sort())
      .toEqual(['200', '500'])
    wrapper.unmount()
  })

  it('runs an "undo to here" burst step by step and reloads the grid once at the end', async () => {
    const { calls, wrapper } = await mountPage()

    wrapper.findComponent(DraftRail).vm.$emit('undo-steps', 3)
    await settle()

    const undos = calls.filter((c) => pathOf(c).endsWith('/drafts/D1/undo'))
    expect(undos.map((c) => (c.body as { expectedVersion: number }).expectedVersion)).toEqual([3, 4, 5])
    const firstUndo = calls.indexOf(undos[0]!)
    const lastUndo = calls.indexOf(undos[2]!)
    expect(calls.slice(firstUndo, lastUndo).filter(isGrid)).toHaveLength(0)
    expect(calls.slice(lastUndo + 1).filter(isGrid)).toHaveLength(1)
    wrapper.unmount()
  })
})
