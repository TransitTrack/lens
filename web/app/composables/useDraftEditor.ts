import type { Ref } from 'vue'
import type { ClaimDraftEditorData, Draft, DraftEdit, DraftEditResult, Options, ReleaseDraftEditorData, RenewDraftEditorData } from '~/api'
import { useEditorIdentity } from './useEditorIdentity'

export type DraftDetail = Draft

/** server lease is 15 min; renew at 1/3 */
const RENEW_INTERVAL_MS = 5 * 60_000

export interface MutateVars {
  draftId: string
  editor: string
  expectedVersion: number
}

/**
 * What the page hands the editor: the draft and its edit history (read by the
 * page's queries), a refresh of the draft that resolves with the fresh row, and
 * the editor-lock mutations (SDK-shaped variables, e.g. a `mutateAsync`).
 */
export interface DraftEditorDeps {
  draft: Ref<DraftDetail | null | undefined>
  edits: Ref<DraftEdit[] | undefined>
  refreshDraft: () => Promise<DraftDetail | null>
  claim: (vars: Options<ClaimDraftEditorData>) => Promise<unknown>
  renew: (vars: Options<RenewDraftEditorData>) => Promise<unknown>
  release: (vars: Options<ReleaseDraftEditorData>) => Promise<unknown>
}

/** Read the stable application code from a REST problem response. */
function errorCode(e: unknown): string | undefined {
  const code = restProblemOf(e)?.code
  return typeof code === 'string' ? code : undefined
}

/**
 * Owns a schedule draft's editing session: a local copy of the draft (so edit
 * results fold in instantly), the editor lock (claim on mount, renew on a
 * heartbeat, release on unmount / unload), and a `mutate` wrapper that injects
 * `draftId` / `editor` / `expectedVersion` and recovers from a stale-version
 * conflict once. It fetches nothing itself: reads and lock calls come from `deps`.
 */
export function useDraftEditor(draftId: Ref<string>, deps: DraftEditorDeps) {
  const toast = useToast()
  const { t } = useI18n()
  const identity = useEditorIdentity()
  const me = identity.name

  const draft = ref<DraftDetail | null>(null)

  watch(
    deps.draft,
    (d) => {
      draft.value = d ? { ...d } : null
    },
    { immediate: true },
  )

  /** Refresh the draft and return the fresh row (the `watch` above only flushes on a later tick). */
  async function refreshDraft(): Promise<DraftDetail | null> {
    return (await deps.refreshDraft()) ?? deps.draft.value ?? null
  }

  async function refreshDraftVersion(fallback: number): Promise<number> {
    return (await refreshDraft())?.version ?? fallback
  }

  // --- undo/redo seeding -------------------------------------------------------
  const canUndo = ref(false)
  const canRedo = ref(false)

  watch(
    deps.edits,
    (edits) => {
      if (!edits) return
      canUndo.value = edits.some((e) => !e.undone)
      canRedo.value = edits.some((e) => e.undone)
    },
    { immediate: true },
  )

  // --- lock -----------------------------------------------------------------
  const claim = (editor: string, takeOver?: boolean) =>
    deps.claim({ path: { id: draftId.value }, body: { editor, takeOver } })

  /** true once we hold the lock; false after a release / a claim failure. */
  const claimed = ref(false)
  /** set when the identity dialog was dismissed on entry — page is inert. */
  const identityMissing = ref(false)

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
        title: t('drafts.editCancelled'),
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
        const freshVersion = await refreshDraftVersion(current.version)
        try {
          return await run(freshVersion)
        } catch (retryErr) {
          toast.add({
            title: t('drafts.editFailed'),
            description: (retryErr as Error).message,
            color: 'error',
          })
          throw retryErr
        }
      }

      if (code === 'LOCK_LOST') {
        try {
          await claim(editor)
          claimed.value = true
          const freshVersion = await refreshDraftVersion(current.version)
          return await run(freshVersion)
        } catch {
          claimed.value = false
          await refreshDraft()
          toast.add({ title: t('drafts.anotherHolds'), color: 'error' })
          throw e
        }
      }

      toast.add({ title: t('drafts.editFailed'), description: (e as Error).message, color: 'error' })
      throw e
    }
  }

  /** Fold a `DraftEditResult` into local state — no network. */
  function applyResult(r: DraftEditResult) {
    if (draft.value) draft.value = { ...draft.value, ...r.draft }
    canUndo.value = r.canUndo
    canRedo.value = r.canRedo
  }

  async function takeOver() {
    const editor = await ensureIdentity()
    try {
      await claim(editor, true)
      claimed.value = true
      identityMissing.value = false
    } catch (e) {
      toast.add({
        title: t('drafts.takeOverFailed'),
        description: (e as Error).message,
        color: 'error',
      })
      throw e
    }
    await refreshDraft()
  }

  // --- lifecycle (client only) ------------------------------------------
  let renewTimer: ReturnType<typeof setInterval> | null = null
  let disposed = false

  function releaseNow() {
    const editor = me.value
    claimed.value = false
    if (!editor) return
    // fire-and-forget
    void deps.release({ path: { id: draftId.value }, body: { editor } }).catch(() => {})
  }

  function onBeforeUnload() {
    releaseNow()
  }

  onMounted(async () => {
    if (!import.meta.client) return
    let editor: string
    try {
      editor = await identity.ensure()
    } catch {
      identityMissing.value = true
      claimed.value = false
      toast.add({ title: t('drafts.enterNameToEdit'), color: 'warning' })
      return
    }
    try {
      await claim(editor)
      claimed.value = true
    } catch {
      // someone else holds the lock — `readOnly` handles display
      claimed.value = false
    }
    await refreshDraft()

    // The component may have unmounted while the awaits above were pending.
    if (disposed) return

    renewTimer = setInterval(() => {
      void deps.renew({ path: { id: draftId.value }, body: { editor } }).catch(() => {})
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
    readOnly,
    claimed,
    identityMissing,
    lockBanner,
    canUndo,
    canRedo,
    me,
    mutate,
    applyResult,
    takeOver,
    refreshDraft,
  }
}
