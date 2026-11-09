import type { DraftEditResult } from '~/api'
import type { MutateVars, useDraftEditor } from '~/composables/useDraftEditor'

type Editor = ReturnType<typeof useDraftEditor>

/**
 * Thin helper over an existing {@link useDraftEditor} instance that folds the
 * `edit → editor.mutate → applyResult → onChanged` block — shared by every
 * draft-edit call site — into one place, so the try/catch that swallows
 * `editor.mutate`'s post-toast rethrow can never be forgotten (no unhandled
 * promise rejection). Reloading the grid / history is left to the edit
 * mutation's invalidation of the draft's queries.
 */
export function useDraftEdit(
  editor: Editor,
  opts?: { onChanged?: () => void | Promise<void> },
) {
  /**
   * Run one draft-edit mutation. `mutation` is called with the injected vars
   * (`draftId` / `editor` / `expectedVersion`) and must resolve with the
   * `DraftEditResult`. Handles the `editor.mutate` wrapper (STALE_DRAFT retry +
   * LOCK_LOST re-claim + version injection), `applyResult`, `opts.onChanged`, and
   * swallows the post-toast rethrow. Returns the result on success, `null` on
   * failure or an empty payload.
   */
  async function run(
    mutation: (vars: MutateVars) => Promise<DraftEditResult | null | undefined>,
  ): Promise<DraftEditResult | null> {
    try {
      const result = (await editor.mutate((vars) => mutation(vars))) ?? null
      if (result) {
        editor.applyResult(result)
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
