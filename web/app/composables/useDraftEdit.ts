import type { DraftEditResultFFragment } from '~~/generated/graphql'
import type { useDraftEditor } from '~/composables/useDraftEditor'

type Editor = ReturnType<typeof useDraftEditor>
type EditVars = { draftId: string; editor: string; expectedVersion: number }

type MutationResult =
  | { data?: Record<string, DraftEditResultFFragment | null | undefined> | null }
  | null
  | undefined

/**
 * Thin helper over an existing {@link useDraftEditor} instance that folds the
 * `edit → editor.mutate → applyResult → refetchEdits → onChanged` block —
 * duplicated across every draft-edit call site — into one place, so the
 * try/catch that swallows `editor.mutate`'s post-toast rethrow can never be
 * forgotten (no unhandled promise rejection).
 */
export function useDraftEdit(
  editor: Editor,
  opts?: { onChanged?: () => void | Promise<void> },
) {
  /**
   * Run one draft-edit mutation. `mutation` is called with the injected vars
   * (`draftId` / `editor` / `expectedVersion`) and must return the raw mutation
   * result. Handles the `editor.mutate` wrapper (STALE_DRAFT retry + LOCK_LOST
   * re-claim + version injection), `applyResult`, `refetchEdits`, `opts.onChanged`,
   * and swallows the post-toast rethrow. Returns the fragment on success, `null`
   * on failure or a null payload.
   */
  async function run(
    mutation: (vars: EditVars) => Promise<MutationResult>,
    field: string,
  ): Promise<DraftEditResultFFragment | null> {
    try {
      const res = await editor.mutate((vars) => mutation(vars))
      const result = res?.data?.[field] ?? null
      if (result) {
        editor.applyResult(result)
        void editor.refetchEdits()
        await opts?.onChanged?.()
      }
      return result
    } catch {
      // editor.mutate has already toasted; swallow the rethrow so there is no
      // unhandled promise rejection.
      return null
    }
  }

  return { run }
}
