import { DraftsApi } from '~~/generated/rest'
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

const api = new DraftsApi()

export function useUpdateStopTimeMutation() {
  return useRestMutation<UpdateStopTimeMutation, UpdateStopTimeMutationVariables>(async (v) => ({
    updateStopTime: await api.updateStopTime({ updateStopTimeInput: v.input } as unknown as Parameters<typeof api.updateStopTime>[0]),
  }) as unknown as UpdateStopTimeMutation)
}

export function useShiftTripMutation() {
  return useRestMutation<ShiftTripMutation, ShiftTripMutationVariables>(async (v) => ({
    shiftTrip: await api.shiftTrip({ shiftTripInput: v.input } as unknown as Parameters<typeof api.shiftTrip>[0]),
  }) as unknown as ShiftTripMutation)
}

export function useSetStopDwellMutation() {
  return useRestMutation<SetStopDwellMutation, SetStopDwellMutationVariables>(async (v) => ({
    setStopDwell: await api.setStopDwell({ setStopDwellInput: v.input } as unknown as Parameters<typeof api.setStopDwell>[0]),
  }) as unknown as SetStopDwellMutation)
}

export function useAddTripMutation() {
  return useRestMutation<AddTripMutation, AddTripMutationVariables>(async (v) => ({
    addTrip: await api.addTrip({ addTripInput: v.input } as unknown as Parameters<typeof api.addTrip>[0]),
  }) as unknown as AddTripMutation)
}

export function useDuplicateTripMutation() {
  return useRestMutation<DuplicateTripMutation, DuplicateTripMutationVariables>(async (v) => ({
    duplicateTrip: await api.duplicateTrip({ duplicateTripInput: v.input } as unknown as Parameters<typeof api.duplicateTrip>[0]),
  }) as unknown as DuplicateTripMutation)
}

export function useDeleteTripMutation() {
  return useRestMutation<DeleteTripMutation, DeleteTripMutationVariables>(async (v) => ({
    deleteTrip: await api.deleteTrip({ deleteTripInput: v.input } as unknown as Parameters<typeof api.deleteTrip>[0]),
  }) as unknown as DeleteTripMutation)
}

export function useBulkShiftTripsMutation() {
  return useRestMutation<BulkShiftTripsMutation, BulkShiftTripsMutationVariables>(async (v) => ({
    bulkShiftTrips: await api.bulkShiftTrips({ bulkShiftTripsInput: v.input } as unknown as Parameters<typeof api.bulkShiftTrips>[0]),
  }) as unknown as BulkShiftTripsMutation)
}

export function useForkDraftMutation() {
  return useRestMutation<ForkDraftMutation, ForkDraftMutationVariables>(async (v) => ({
    forkDraft: await api.forkDraft({ forkDraftInput: v.input } as unknown as Parameters<typeof api.forkDraft>[0]),
  }) as unknown as ForkDraftMutation)
}

export function useDiscardDraftMutation() {
  return useRestMutation<DiscardDraftMutation, DiscardDraftMutationVariables>(async (v) => ({
    discardDraft: await api.discardDraft({ id: v.id, discardDraftRequest: { editor: v.editor } } as unknown as Parameters<typeof api.discardDraft>[0]),
  }) as unknown as DiscardDraftMutation)
}

export function useActivateDraftMutation() {
  return useRestMutation<ActivateDraftMutation, ActivateDraftMutationVariables>(async (v) => ({
    activateDraft: await api.activateDraft({ id: v.id, activateDraftRequest: { editor: v.editor, force: v.force ?? undefined } } as unknown as Parameters<typeof api.activateDraft>[0]),
  }) as unknown as ActivateDraftMutation)
}

export function useRebuildDraftMutation() {
  return useRestMutation<RebuildDraftMutation, RebuildDraftMutationVariables>(async (v) => ({
    rebuildDraft: await api.rebuildDraft({ id: v.id } as unknown as Parameters<typeof api.rebuildDraft>[0]),
  }) as unknown as RebuildDraftMutation)
}

export function useClaimDraftEditorMutation() {
  return useRestMutation<ClaimDraftEditorMutation, ClaimDraftEditorMutationVariables>(async (v) => ({
    claimDraftEditor: await api.claimDraftEditor({ id: v.id, claimDraftEditorRequest: { editor: v.editor, takeOver: v.takeOver ?? undefined } } as unknown as Parameters<typeof api.claimDraftEditor>[0]),
  }) as unknown as ClaimDraftEditorMutation)
}

export function useRenewDraftEditorMutation() {
  return useRestMutation<RenewDraftEditorMutation, RenewDraftEditorMutationVariables>(async (v) => ({
    renewDraftEditor: await api.renewDraftEditor({ id: v.id, revertDraftToForkRequest: { editor: v.editor } } as unknown as Parameters<typeof api.renewDraftEditor>[0]),
  }) as unknown as RenewDraftEditorMutation)
}

export function useReleaseDraftEditorMutation() {
  return useRestMutation<ReleaseDraftEditorMutation, ReleaseDraftEditorMutationVariables>(async (v) => ({
    releaseDraftEditor: await api.releaseDraftEditor({ id: v.id, revertDraftToForkRequest: { editor: v.editor } } as unknown as Parameters<typeof api.releaseDraftEditor>[0]),
  }) as unknown as ReleaseDraftEditorMutation)
}

export function useUndoDraftEditMutation() {
  return useRestMutation<UndoDraftEditMutation, UndoDraftEditMutationVariables>(async (v) => ({
    undoDraftEdit: await api.undoDraftEdit({ id: v.id, undoDraftEditRequest: { editor: v.editor, expectedVersion: v.expectedVersion } } as unknown as Parameters<typeof api.undoDraftEdit>[0]),
  }) as unknown as UndoDraftEditMutation)
}

export function useRedoDraftEditMutation() {
  return useRestMutation<RedoDraftEditMutation, RedoDraftEditMutationVariables>(async (v) => ({
    redoDraftEdit: await api.redoDraftEdit({ id: v.id, redoDraftEditRequest: { editor: v.editor, expectedVersion: v.expectedVersion } } as unknown as Parameters<typeof api.redoDraftEdit>[0]),
  }) as unknown as RedoDraftEditMutation)
}
