import type { UseMutationOptions } from '@pinia/colada'
import { Drafts, type UndoDraftEditData, type UndoDraftEditResponse, type ApiError, type Options } from '~/api'

/**
 * One step of an "undo to here" burst: POST /drafts/{id}/undo without invalidating anything,
 * so a burst of N steps doesn't refetch the draft's reads N times. The caller ends the burst
 * with `invalidateDraft` (draftCache.ts) once.
 */
export const undoDraftEditStepMutation = () => ({
  mutation: (vars: Options<UndoDraftEditData>) => Drafts.undoDraftEdit({ ...vars, throwOnError: true }).then((r) => r.data),
}) satisfies UseMutationOptions<UndoDraftEditResponse, Options<UndoDraftEditData>, ApiError>
