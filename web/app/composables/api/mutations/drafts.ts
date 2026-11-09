import type { UseMutationOptions } from '@pinia/colada'
import { type ActivateDraftData, type ActivateDraftResponse, type AddTripData, type AddTripResponse, type ApiError, type BulkShiftTripsData, type BulkShiftTripsResponse, type ClaimDraftEditorData, type ClaimDraftEditorResponse, type DeleteTripData, type DeleteTripResponse, type DiscardDraftData, type DiscardDraftResponse, Drafts, type DuplicateTripData, type DuplicateTripResponse, type ForkDraftData, type ForkDraftResponse, type Options, type RebuildDraftData, type RebuildDraftResponse, type RedoDraftEditData, type RedoDraftEditResponse, type ReleaseDraftEditorData, type ReleaseDraftEditorResponse, type RenewDraftEditorData, type RenewDraftEditorResponse, type SetStopDwellData, type SetStopDwellResponse, type ShiftTripData, type ShiftTripResponse, type UndoDraftEditData, type UndoDraftEditResponse, type UpdateStopTimeData, type UpdateStopTimeResponse } from '~/api'
import { draftKeys } from '../keys/drafts'
import { feedKeys } from '../keys/feeds'
import type { QueryCache } from './types'

export const activateDraftMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<ActivateDraftData>) => Drafts.activateDraft({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: ActivateDraftResponse, _vars: Options<ActivateDraftData>) => {
    queryCache.invalidateQueries({ key: draftKeys.all })
    queryCache.invalidateQueries({ key: feedKeys.all })
  },
}) satisfies UseMutationOptions<ActivateDraftResponse, Options<ActivateDraftData>, ApiError>

export const addTripMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<AddTripData>) => Drafts.addTrip({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: AddTripResponse, vars: Options<AddTripData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<AddTripResponse, Options<AddTripData>, ApiError>

export const bulkShiftTripsMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<BulkShiftTripsData>) => Drafts.bulkShiftTrips({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: BulkShiftTripsResponse, vars: Options<BulkShiftTripsData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<BulkShiftTripsResponse, Options<BulkShiftTripsData>, ApiError>

export const claimDraftEditorMutation = () => ({
  mutation: (vars: Options<ClaimDraftEditorData>) => Drafts.claimDraftEditor({ ...vars, throwOnError: true }).then((r) => r.data),
}) satisfies UseMutationOptions<ClaimDraftEditorResponse, Options<ClaimDraftEditorData>, ApiError>

export const deleteTripMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<DeleteTripData>) => Drafts.deleteTrip({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: DeleteTripResponse, vars: Options<DeleteTripData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<DeleteTripResponse, Options<DeleteTripData>, ApiError>

export const discardDraftMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<DiscardDraftData>) => Drafts.discardDraft({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: DiscardDraftResponse, _vars: Options<DiscardDraftData>) => {
    queryCache.invalidateQueries({ key: draftKeys.all })
  },
}) satisfies UseMutationOptions<DiscardDraftResponse, Options<DiscardDraftData>, ApiError>

/**
 * Refresh every read of one draft once (the end of an undo burst, see `undoDraftEditStepMutation`).
 * A failed or aborted refetch is left to the queries' own error state.
 */
export function invalidateDraft(queryCache: QueryCache, draftId: string | number): Promise<void> {
  return queryCache.invalidateQueries({ key: draftKeys.draft(draftId) }).then(() => undefined, () => undefined)
}

/**
 * Abort every in-flight read of one draft, keeping the data the page shows. Used when a page
 * leaves a draft that no longer exists (activated / discarded): the mutation's invalidation has
 * started refetches whose 404s would otherwise flash a "draft gone" state before navigation.
 */
export function cancelDraftReads(queryCache: QueryCache, draftId: string | number): void {
  queryCache.cancelQueries({ key: draftKeys.draft(draftId) })
}

export const duplicateTripMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<DuplicateTripData>) => Drafts.duplicateTrip({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: DuplicateTripResponse, vars: Options<DuplicateTripData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<DuplicateTripResponse, Options<DuplicateTripData>, ApiError>

export const forkDraftMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<ForkDraftData>) => Drafts.forkDraft({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: ForkDraftResponse, _vars: Options<ForkDraftData>) => {
    queryCache.invalidateQueries({ key: draftKeys.all })
  },
}) satisfies UseMutationOptions<ForkDraftResponse, Options<ForkDraftData>, ApiError>

export const rebuildDraftMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<RebuildDraftData>) => Drafts.rebuildDraft({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: RebuildDraftResponse, vars: Options<RebuildDraftData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.path.id) })
  },
}) satisfies UseMutationOptions<RebuildDraftResponse, Options<RebuildDraftData>, ApiError>

export const redoDraftEditMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<RedoDraftEditData>) => Drafts.redoDraftEdit({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: RedoDraftEditResponse, vars: Options<RedoDraftEditData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.path.id) })
  },
}) satisfies UseMutationOptions<RedoDraftEditResponse, Options<RedoDraftEditData>, ApiError>

export const releaseDraftEditorMutation = () => ({
  mutation: (vars: Options<ReleaseDraftEditorData>) => Drafts.releaseDraftEditor({ ...vars, throwOnError: true }).then((r) => r.data),
}) satisfies UseMutationOptions<ReleaseDraftEditorResponse, Options<ReleaseDraftEditorData>, ApiError>

export const renewDraftEditorMutation = () => ({
  mutation: (vars: Options<RenewDraftEditorData>) => Drafts.renewDraftEditor({ ...vars, throwOnError: true }).then((r) => r.data),
}) satisfies UseMutationOptions<RenewDraftEditorResponse, Options<RenewDraftEditorData>, ApiError>

export const setStopDwellMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<SetStopDwellData>) => Drafts.setStopDwell({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: SetStopDwellResponse, vars: Options<SetStopDwellData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<SetStopDwellResponse, Options<SetStopDwellData>, ApiError>

export const shiftTripMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<ShiftTripData>) => Drafts.shiftTrip({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: ShiftTripResponse, vars: Options<ShiftTripData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<ShiftTripResponse, Options<ShiftTripData>, ApiError>

export const undoDraftEditMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<UndoDraftEditData>) => Drafts.undoDraftEdit({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: UndoDraftEditResponse, vars: Options<UndoDraftEditData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.path.id) })
  },
}) satisfies UseMutationOptions<UndoDraftEditResponse, Options<UndoDraftEditData>, ApiError>

/**
 * One step of an "undo to here" burst: POST /drafts/{id}/undo without invalidating anything,
 * so a burst of N steps doesn't refetch the draft's reads N times. The caller ends the burst
 * with `invalidateDraft` (below) once.
 */
export const undoDraftEditStepMutation = () => ({
  mutation: (vars: Options<UndoDraftEditData>) => Drafts.undoDraftEdit({ ...vars, throwOnError: true }).then((r) => r.data),
}) satisfies UseMutationOptions<UndoDraftEditResponse, Options<UndoDraftEditData>, ApiError>

export const updateStopTimeMutation = (queryCache: QueryCache) => ({
  mutation: (vars: Options<UpdateStopTimeData>) => Drafts.updateStopTime({ ...vars, throwOnError: true }).then((r) => r.data),
  onSuccess: (_data: UpdateStopTimeResponse, vars: Options<UpdateStopTimeData>) => {
    queryCache.invalidateQueries({ key: draftKeys.draft(vars.body.draftId) })
  },
}) satisfies UseMutationOptions<UpdateStopTimeResponse, Options<UpdateStopTimeData>, ApiError>
