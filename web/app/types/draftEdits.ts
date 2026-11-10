import type {
  AddTripInput, BulkShiftTripsInput, DeleteTripInput, DuplicateTripInput,
  SetStopDwellInput, ShiftTripInput, Trip, TripPattern, UpdateStopTimeInput,
} from '~/api'
import type { StopPathWithStop } from '~/utils/joins/stopPaths'

/** The fields every edit command gets from the editor session, not from the component. */
type Injected = 'draftId' | 'editor' | 'expectedVersion'

/**
 * One draft edit a component asks the page to run: the SDK request body minus
 * the session fields the page injects (`draftId` / `editor` / `expectedVersion`).
 * Undo / redo carry no body of their own.
 */
export type DraftEditCommand =
  | { op: 'updateStopTime', body: Omit<UpdateStopTimeInput, Injected> }
  | { op: 'setStopDwell', body: Omit<SetStopDwellInput, Injected> }
  | { op: 'shiftTrip', body: Omit<ShiftTripInput, Injected> }
  | { op: 'deleteTrip', body: Omit<DeleteTripInput, Injected> }
  | { op: 'addTrip', body: Omit<AddTripInput, Injected> }
  | { op: 'duplicateTrip', body: Omit<DuplicateTripInput, Injected> }
  | { op: 'bulkShiftTrips', body: Omit<BulkShiftTripsInput, Injected> }
  | { op: 'undoDraftEdit' }
  | { op: 'redoDraftEdit' }

/** A draft trip pattern with its stop paths (for building a new trip's stop times). */
export type DraftPattern = TripPattern & { stopPaths: StopPathWithStop[] }

/** A draft trip with its derived pattern (null when the trip has none / it is unknown). */
export type DraftTripDetail = Trip & { pattern: TripPattern | null }
