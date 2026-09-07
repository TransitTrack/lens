import {
  useDraftDetailQuery,
  useDraftEditsQuery,
  useClaimDraftEditorMutation,
  useRenewDraftEditorMutation,
  useReleaseDraftEditorMutation,
  type DraftDetailQuery,
  type DraftEditResultFFragment,
} from '../../generated/graphql'
import { useEditorIdentity } from './useEditorIdentity'

export type DraftDetail = NonNullable<DraftDetailQuery['draft']>

/** server lease is 15 min; renew at 1/3 */
const RENEW_INTERVAL_MS = 5 * 60_000

interface MutateVars {
  draftId: string
  editor: string
  expectedVersion: number
}

/** Pull the GraphQL error code out of whatever shape an ApolloError arrives in. */
function errorCode(e: unknown): string | undefined {
  const err = e as {
    graphQLErrors?: ReadonlyArray<{ extensions?: Record<string, unknown> | null }>
    cause?: { graphQLErrors?: ReadonlyArray<{ extensions?: Record<string, unknown> | null }> }
  }
  const gql = err?.graphQLErrors ?? err?.cause?.graphQLErrors
  const code = gql?.[0]?.extensions?.code
  return typeof code === 'string' ? code : undefined
}

/**
 * Owns a schedule draft's editing session: the `DraftDetail` query, the editor
 * lock (claim on mount, renew on a heartbeat, release on unmount / unload), and
 * a `mutate` wrapper that injects `draftId` / `editor` / `expectedVersion` and
 * recovers from a stale-version conflict once.
 */
export function useDraftEditor(draftId: Ref<string>) {
  const toast = useToast()
  const identity = useEditorIdentity()
  const me = identity.name

  const {
    result: detailResult,
    loading,
    refetch: refetchDraftDetailRaw,
    onError: onDetailError,
  } = useDraftDetailQuery(
    () => ({ id: draftId.value }),
    () => ({ enabled: !!draftId.value }),
  )

  const draft = ref<DraftDetail | null>(null)

  watch(
    () => detailResult.value?.draft,
    (d) => {
      draft.value = d ? { ...d } : null
    },
    { immediate: true },
  )

  onDetailError((err) => {
    toast.add({ title: 'Could not load draft', description: err.message, color: 'error' })
  })

  async function refetchDraft() {
    await refetchDraftDetailRaw()
  }

  /**
   * Refetch and return the draft version straight from the resolved query
   * result — the `watch` that syncs `draft.value` only flushes on a later
   * tick, so reading `draft.value` right after the await is still stale.
   */
  async function refetchDraftVersion(fallback: number): Promise<number> {
    const res = await refetchDraftDetailRaw()
    return (
      res?.data?.draft?.version ?? detailResult.value?.draft?.version ?? fallback
    )
  }

  // --- undo/redo seeding -------------------------------------------------------
  const canUndo = ref(false)
  const canRedo = ref(false)

  const { result: editsResult, refetch: refetchEditsRaw } = useDraftEditsQuery(
    () => ({ id: draftId.value, limit: 500 }),
    () => ({ enabled: !!draftId.value }),
  )

  watch(
    () => editsResult.value?.draftEdits,
    (edits) => {
      if (!edits) return
      canUndo.value = edits.some((e) => !e.undone)
      canRedo.value = edits.some((e) => e.undone)
    },
    { immediate: true },
  )

  async function refetchEdits() {
    await refetchEditsRaw()
  }

  // --- lock -----------------------------------------------------------------
  const { mutate: claimDraftEditor } = useClaimDraftEditorMutation()
  const { mutate: renewDraftEditor } = useRenewDraftEditorMutation()
  const { mutate: releaseDraftEditor } = useReleaseDraftEditorMutation()

  const readOnly = computed(
    () => draft.value?.lock?.editor != null && draft.value.lock.editor !== me.value,
  )

  const lockBanner = computed<{ editor: string; expiresAt: string } | null>(() => {
    if (!readOnly.value || !draft.value?.lock) return null
    const { editor, expiresAt } = draft.value.lock
    return { editor, expiresAt }
  })

  /** Resolve the editor name, toasting + rethrowing if the user cancels. */
  async function ensureIdentity(): Promise<string> {
    if (me.value) return me.value
    try {
      return await identity.ensure()
    } catch (e) {
      toast.add({
        title: 'Editing cancelled — enter your name to edit',
        color: 'warning',
      })
      throw e
    }
  }

  // --- version-guarded mutate ---------------------------------------------
  async function mutate<T>(fn: (vars: MutateVars) => Promise<T>): Promise<T> {
    const current = draft.value
    if (!current) throw new Error('Draft is not loaded yet')
    const editor = await ensureIdentity()

    const run = (expectedVersion: number) =>
      fn({ draftId: draftId.value, editor, expectedVersion })

    try {
      return await run(current.version)
    } catch (e) {
      const code = errorCode(e)

      if (code === 'STALE_DRAFT') {
        const freshVersion = await refetchDraftVersion(current.version)
        try {
          return await run(freshVersion)
        } catch (retryErr) {
          toast.add({
            title: 'Edit failed',
            description: (retryErr as Error).message,
            color: 'error',
          })
          throw retryErr
        }
      }

      if (code === 'LOCK_LOST') {
        await refetchDraft()
        toast.add({ title: 'You no longer hold the editor lock', color: 'error' })
        throw e
      }

      toast.add({ title: 'Edit failed', description: (e as Error).message, color: 'error' })
      throw e
    }
  }

  /** Fold a `DraftEditResult` fragment into local state — no network. */
  function applyResult(r: DraftEditResultFFragment) {
    if (draft.value) draft.value = { ...draft.value, ...r.draft }
    canUndo.value = r.canUndo
    canRedo.value = r.canRedo
  }

  async function takeOver() {
    const editor = await ensureIdentity()
    try {
      await claimDraftEditor({ id: draftId.value, editor, takeOver: true })
    } catch (e) {
      toast.add({
        title: 'Could not take over the editor lock',
        description: (e as Error).message,
        color: 'error',
      })
      throw e
    }
    await refetchDraft()
  }

  // --- lifecycle (client only) ------------------------------------------
  let renewTimer: ReturnType<typeof setInterval> | null = null
  let disposed = false

  function releaseNow() {
    const editor = me.value
    if (!editor) return
    // fire-and-forget
    void releaseDraftEditor({ id: draftId.value, editor }).catch(() => {})
  }

  function onBeforeUnload() {
    releaseNow()
  }

  onMounted(async () => {
    if (!import.meta.client) return
    const editor = await identity.ensure()
    try {
      await claimDraftEditor({ id: draftId.value, editor })
    } catch {
      // someone else holds the lock — `readOnly` handles display
    }
    await refetchDraft()

    // The component may have unmounted while the awaits above were pending.
    if (disposed) return

    renewTimer = setInterval(() => {
      void renewDraftEditor({ id: draftId.value, editor }).catch(() => {})
    }, RENEW_INTERVAL_MS)

    window.addEventListener('beforeunload', onBeforeUnload)
  })

  onBeforeUnmount(() => {
    disposed = true
    if (renewTimer) {
      clearInterval(renewTimer)
      renewTimer = null
    }
    if (import.meta.client) {
      window.removeEventListener('beforeunload', onBeforeUnload)
      releaseNow()
    }
  })

  return {
    draft,
    loading,
    readOnly,
    lockBanner,
    canUndo,
    canRedo,
    me,
    mutate,
    applyResult,
    takeOver,
    refetchDraft,
    refetchEdits,
  }
}
