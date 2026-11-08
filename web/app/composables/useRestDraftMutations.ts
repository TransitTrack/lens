import { Drafts } from '~/api'
import { useRestMutation } from './useRestMutation'
import type {
  UpdateStopTimeMutation, UpdateStopTimeMutationVariables,
  ShiftTripMutation, ShiftTripMutationVariables,
  SetStopDwellMutation, SetStopDwellMutationVariables,
  AddTripMutation, AddTripMutationVariables,
  DuplicateTripMutation, DuplicateTripMutationVariables,
  DeleteTripMutation, DeleteTripMutationVariables,
  BulkShiftTripsMutation, BulkShiftTripsMutationVariables,
  ForkDraftMutation, ForkDraftMutationVariables,
  DiscardDraftMutation, DiscardDraftMutationVariables,
  ActivateDraftMutation, ActivateDraftMutationVariables,
  RebuildDraftMutation, RebuildDraftMutationVariables,
  ClaimDraftEditorMutation, ClaimDraftEditorMutationVariables,
  RenewDraftEditorMutation, RenewDraftEditorMutationVariables,
  ReleaseDraftEditorMutation, ReleaseDraftEditorMutationVariables,
  UndoDraftEditMutation, UndoDraftEditMutationVariables,
  RedoDraftEditMutation, RedoDraftEditMutationVariables,
} from '~/types/restViews'

export function useUpdateStopTimeMutation() {
  return useRestMutation<UpdateStopTimeMutation, UpdateStopTimeMutationVariables>(async (v) => ({
    updateStopTime: (await Drafts.updateStopTime({ body: v.input as never })).data,
  }) as unknown as UpdateStopTimeMutation)
}

export function useShiftTripMutation() {
  return useRestMutation<ShiftTripMutation, ShiftTripMutationVariables>(async (v) => ({
    shiftTrip: (await Drafts.shiftTrip({ body: v.input as never })).data,
  }) as unknown as ShiftTripMutation)
}

export function useSetStopDwellMutation() {
  return useRestMutation<SetStopDwellMutation, SetStopDwellMutationVariables>(async (v) => ({
    setStopDwell: (await Drafts.setStopDwell({ body: v.input as never })).data,
  }) as unknown as SetStopDwellMutation)
}

export function useAddTripMutation() {
  return useRestMutation<AddTripMutation, AddTripMutationVariables>(async (v) => ({
    addTrip: (await Drafts.addTrip({ body: v.input as never })).data,
  }) as unknown as AddTripMutation)
}

export function useDuplicateTripMutation() {
  return useRestMutation<DuplicateTripMutation, DuplicateTripMutationVariables>(async (v) => ({
    duplicateTrip: (await Drafts.duplicateTrip({ body: v.input as never })).data,
  }) as unknown as DuplicateTripMutation)
}

export function useDeleteTripMutation() {
  return useRestMutation<DeleteTripMutation, DeleteTripMutationVariables>(async (v) => ({
    deleteTrip: (await Drafts.deleteTrip({ body: v.input as never })).data,
  }) as unknown as DeleteTripMutation)
}

export function useBulkShiftTripsMutation() {
  return useRestMutation<BulkShiftTripsMutation, BulkShiftTripsMutationVariables>(async (v) => ({
    bulkShiftTrips: (await Drafts.bulkShiftTrips({ body: v.input as never })).data,
  }) as unknown as BulkShiftTripsMutation)
}

export function useForkDraftMutation() {
  return useRestMutation<ForkDraftMutation, ForkDraftMutationVariables>(async (v) => ({
    forkDraft: (await Drafts.forkDraft({ body: v.input as never })).data,
  }) as unknown as ForkDraftMutation)
}

export function useDiscardDraftMutation() {
  return useRestMutation<DiscardDraftMutation, DiscardDraftMutationVariables>(async (v) => ({
    discardDraft: (await Drafts.discardDraft({ path: { id: String(v.id) }, body: { editor: v.editor } })).data,
  }) as unknown as DiscardDraftMutation)
}

export function useActivateDraftMutation() {
  return useRestMutation<ActivateDraftMutation, ActivateDraftMutationVariables>(async (v) => ({
    activateDraft: (await Drafts.activateDraft({ path: { id: String(v.id) }, body: { editor: v.editor, force: v.force ?? undefined } })).data,
  }) as unknown as ActivateDraftMutation)
}

export function useRebuildDraftMutation() {
  return useRestMutation<RebuildDraftMutation, RebuildDraftMutationVariables>(async (v) => ({
    rebuildDraft: (await Drafts.rebuildDraft({ path: { id: String(v.id) } })).data,
  }) as unknown as RebuildDraftMutation)
}

export function useClaimDraftEditorMutation() {
  return useRestMutation<ClaimDraftEditorMutation, ClaimDraftEditorMutationVariables>(async (v) => ({
    claimDraftEditor: (await Drafts.claimDraftEditor({ path: { id: String(v.id) }, body: { editor: v.editor, takeOver: v.takeOver ?? undefined } })).data,
  }) as unknown as ClaimDraftEditorMutation)
}

export function useRenewDraftEditorMutation() {
  return useRestMutation<RenewDraftEditorMutation, RenewDraftEditorMutationVariables>(async (v) => ({
    renewDraftEditor: (await Drafts.renewDraftEditor({ path: { id: String(v.id) }, body: { editor: v.editor } })).data,
  }) as unknown as RenewDraftEditorMutation)
}

export function useReleaseDraftEditorMutation() {
  return useRestMutation<ReleaseDraftEditorMutation, ReleaseDraftEditorMutationVariables>(async (v) => ({
    releaseDraftEditor: (await Drafts.releaseDraftEditor({ path: { id: String(v.id) }, body: { editor: v.editor } })).data,
  }) as unknown as ReleaseDraftEditorMutation)
}

export function useUndoDraftEditMutation() {
  return useRestMutation<UndoDraftEditMutation, UndoDraftEditMutationVariables>(async (v) => ({
    undoDraftEdit: (await Drafts.undoDraftEdit({ path: { id: String(v.id) }, body: { editor: v.editor, expectedVersion: v.expectedVersion } })).data,
  }) as unknown as UndoDraftEditMutation)
}

export function useRedoDraftEditMutation() {
  return useRestMutation<RedoDraftEditMutation, RedoDraftEditMutationVariables>(async (v) => ({
    redoDraftEdit: (await Drafts.redoDraftEdit({ path: { id: String(v.id) }, body: { editor: v.editor, expectedVersion: v.expectedVersion } })).data,
  }) as unknown as RedoDraftEditMutation)
}
