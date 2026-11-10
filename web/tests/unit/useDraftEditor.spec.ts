import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, ref } from 'vue'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import { ApiError, type Draft, type DraftEdit } from '~/api'
import { useDraftEditor, type DraftEditorDeps, type MutateVars } from '../../app/composables/useDraftEditor'
import { useEditorIdentity } from '../../app/composables/useEditorIdentity'

const draft = (version: number): Draft => ({
  id: 'D1', feedCode: 'g', status: 'DRAFT', kind: 'SCHEDULE', version, derivationStale: false,
  createdAt: '2026-11-09T10:00:00Z', lock: { editor: 'alice', expiresAt: '2026-11-09T10:15:00Z' },
} as Draft)

const problem = (code: string) => new ApiError(409, { title: code, status: 409, code })

async function settle() {
  for (let i = 0; i < 8; i++) await vi.advanceTimersByTimeAsync(0)
}

function fakeDeps(initial: Draft) {
  const current = ref<Draft | null>(initial)
  const deps = {
    draft: current,
    edits: ref<DraftEdit[] | undefined>([]),
    refreshDraft: vi.fn(async () => current.value),
    claim: vi.fn(async () => ({})),
    renew: vi.fn(async () => ({})),
    release: vi.fn(async () => ({})),
  } satisfies DraftEditorDeps
  return deps
}

async function mountEditor(deps: DraftEditorDeps) {
  let editor!: ReturnType<typeof useDraftEditor>
  const wrapper = await mountSuspended(defineComponent({
    setup() {
      editor = useDraftEditor(ref('D1'), deps)
      return () => null
    },
  }))
  await settle()
  return { editor, wrapper }
}

describe('useDraftEditor with injected reads and lock calls', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    useEditorIdentity().name.value = 'alice'
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it('claims the lock on mount, renews it every 5 minutes and releases it on unmount', async () => {
    const deps = fakeDeps(draft(3))
    const { wrapper } = await mountEditor(deps)

    expect(deps.claim).toHaveBeenCalledWith({ path: { id: 'D1' }, body: { editor: 'alice', takeOver: undefined } })
    expect(deps.refreshDraft).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(5 * 60_000)
    expect(deps.renew).toHaveBeenCalledWith({ path: { id: 'D1' }, body: { editor: 'alice' } })

    wrapper.unmount()
    expect(deps.release).toHaveBeenCalledWith({ path: { id: 'D1' }, body: { editor: 'alice' } })
  })

  it('retries a STALE_DRAFT edit once with the version of a freshly refreshed draft', async () => {
    const deps = fakeDeps(draft(3))
    const { editor, wrapper } = await mountEditor(deps)
    deps.refreshDraft.mockClear()
    deps.refreshDraft.mockResolvedValueOnce(draft(7))

    const seen: MutateVars[] = []
    const result = await editor.mutate(async (vars) => {
      seen.push(vars)
      if (seen.length === 1) throw problem('STALE_DRAFT')
      return 'ok'
    })

    expect(result).toBe('ok')
    expect(deps.refreshDraft).toHaveBeenCalledTimes(1)
    expect(seen).toEqual([
      { draftId: 'D1', editor: 'alice', expectedVersion: 3 },
      { draftId: 'D1', editor: 'alice', expectedVersion: 7 },
    ])
    expect(deps.claim).toHaveBeenCalledTimes(1) // the mount claim only
    wrapper.unmount()
  })

  it('re-claims the lock after LOCK_LOST, then retries with the fresh version', async () => {
    const deps = fakeDeps(draft(3))
    const { editor, wrapper } = await mountEditor(deps)
    deps.claim.mockClear()
    deps.refreshDraft.mockClear()
    deps.refreshDraft.mockResolvedValueOnce(draft(9))

    const order: string[] = []
    deps.claim.mockImplementation(async () => {
      order.push('claim')
      return {}
    })
    const seen: number[] = []
    const result = await editor.mutate(async (vars) => {
      order.push('edit')
      seen.push(vars.expectedVersion)
      if (seen.length === 1) throw problem('LOCK_LOST')
      return 'ok'
    })

    expect(result).toBe('ok')
    expect(deps.claim).toHaveBeenCalledTimes(1)
    expect(deps.claim).toHaveBeenCalledWith({ path: { id: 'D1' }, body: { editor: 'alice', takeOver: undefined } })
    expect(order).toEqual(['edit', 'claim', 'edit'])
    expect(seen).toEqual([3, 9])
    expect(editor.claimed.value).toBe(true)
    wrapper.unmount()
  })

  it('does not retry other failures and rethrows them', async () => {
    const deps = fakeDeps(draft(3))
    const { editor, wrapper } = await mountEditor(deps)
    deps.refreshDraft.mockClear()
    const fn = vi.fn(async () => {
      throw problem('VALIDATION')
    })

    await expect(editor.mutate(fn)).rejects.toBeInstanceOf(ApiError)
    expect(fn).toHaveBeenCalledTimes(1)
    expect(deps.refreshDraft).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('folds an edit result into the local draft and the undo / redo flags', async () => {
    const deps = fakeDeps(draft(3))
    const { editor, wrapper } = await mountEditor(deps)

    editor.applyResult({ draft: draft(4), canUndo: true, canRedo: false })

    expect(editor.draft.value?.version).toBe(4)
    expect(editor.canUndo.value).toBe(true)
    expect(editor.canRedo.value).toBe(false)
    wrapper.unmount()
  })
})
