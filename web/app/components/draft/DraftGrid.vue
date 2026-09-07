<script setup lang="ts">
import { computed } from 'vue'
import { useDraftGridQuery } from '~~/generated/graphql'
import { secToClock } from '~/utils/gtfsTime'
import DraftGridCell from '~/components/draft/DraftGridCell.vue'

const props = defineProps<{
  draftId: string
  routeId: string
  directionId: number | null
  serviceId: string | null
  readOnly: boolean
}>()

const selectedTripId = defineModel<string | null>('selectedTripId', { default: null })
const activeCell = defineModel<{ tripId: string; stopSequence: number } | null>('activeCell', {
  default: null,
})

const emit = defineEmits<{
  'commit-cell': [
    payload: {
      tripId: string
      stopSequence: number
      arrivalSec: number | null
      departureSec: number | null
    },
  ]
  'add-trip': []
}>()

const { result, loading, error, refetch } = useDraftGridQuery(
  () => ({
    draftId: props.draftId,
    routeId: props.routeId,
    directionId: props.directionId,
    serviceId: props.serviceId,
  }),
  () => ({ enabled: !!props.draftId && !!props.routeId }),
)

const stops = computed(() => result.value?.draftGrid.stops ?? [])
const trips = computed(() => result.value?.draftGrid.trips ?? [])
const isEmpty = computed(
  () => !loading.value && !error.value && (stops.value.length === 0 || trips.value.length === 0),
)

type CellVal = { arrivalSec: number | null; departureSec: number | null }
const cellMap = computed(() => {
  const map = new Map<string, Map<number, CellVal>>()
  for (const trip of trips.value) {
    const inner = new Map<number, CellVal>()
    for (const cell of trip.cells) {
      inner.set(cell.stopSequence, {
        arrivalSec: cell.arrivalSec,
        departureSec: cell.departureSec,
      })
    }
    map.set(trip.tripId, inner)
  }
  return map
})

function cellFor(tripId: string, stopSequence: number): CellVal {
  return cellMap.value.get(tripId)?.get(stopSequence) ?? { arrivalSec: null, departureSec: null }
}

function tripLabel(tripId: string): string {
  return tripId.split('_').pop() ?? tripId
}

defineExpose({ refetch })
</script>

<template>
  <div class="grid-root">
    <div v-if="loading && trips.length === 0" class="grid-state">
      <UIcon name="i-lucide-loader-circle" class="size-5 animate-spin" />
      <span class="text-sm">Loading timetable…</span>
    </div>
    <div v-else-if="error" class="grid-state text-error">
      <UIcon name="i-lucide-triangle-alert" class="size-5" />
      <span class="text-sm">Failed to load timetable: {{ error.message }}</span>
    </div>
    <div v-else-if="isEmpty" class="grid-state">
      <UIcon name="i-lucide-table" class="size-5" />
      <span class="text-sm">No trips for this route / direction / service.</span>
    </div>

    <div v-else class="grid-scroll">
      <!-- TODO: horizontal virtualization if a large feed lags -->
      <table class="grid-table">
        <thead>
          <tr>
            <th class="corner-cell">Stop</th>
            <th
              v-for="trip in trips"
              :key="trip.tripId"
              class="trip-head"
              :class="{ 'is-selected': selectedTripId === trip.tripId }"
              @click="selectedTripId = trip.tripId"
            >
              <span class="trip-head-id">{{ tripLabel(trip.tripId) }}</span>
              <span class="trip-head-dep">{{ secToClock(trip.firstDepartureSec) }}</span>
            </th>
            <th class="add-head">
              <UButton
                icon="i-lucide-plus"
                size="xs"
                color="neutral"
                variant="soft"
                :disabled="readOnly"
                aria-label="Add trip"
                @click="emit('add-trip')"
              />
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="stop in stops" :key="stop.stopSequence">
            <th class="stop-cell" scope="row">
              <span class="stop-name">{{ stop.stopName ?? stop.stopId }}</span>
              <span v-if="stop.timepoint" class="stop-marker" title="Timepoint">⏱</span>
            </th>
            <DraftGridCell
              v-for="trip in trips"
              :key="trip.tripId + ':' + stop.stopSequence"
              :arrival-sec="cellFor(trip.tripId, stop.stopSequence).arrivalSec"
              :departure-sec="cellFor(trip.tripId, stop.stopSequence).departureSec"
              :read-only="readOnly"
              :is-active="
                activeCell?.tripId === trip.tripId
                  && activeCell?.stopSequence === stop.stopSequence
              "
              @activate="activeCell = { tripId: trip.tripId, stopSequence: stop.stopSequence }"
              @commit="
                (p) =>
                  emit('commit-cell', {
                    tripId: trip.tripId,
                    stopSequence: stop.stopSequence,
                    arrivalSec: p.arrivalSec,
                    departureSec: p.departureSec,
                  })
              "
            />
            <td class="pad-cell" />
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.grid-root {
  height: 100%;
  min-height: 0;
}
.grid-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding: 4rem 1rem;
  color: var(--ui-text-muted);
}
.grid-scroll {
  height: 100%;
  overflow: auto;
  position: relative;
}
.grid-table {
  border-collapse: separate;
  border-spacing: 0;
  font-variant-numeric: tabular-nums;
}
.grid-table th,
.grid-table td {
  background: var(--ui-bg);
}
thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  border-bottom: 1px solid var(--ui-border);
  border-right: 1px solid var(--ui-border);
}
.trip-head {
  width: 84px;
  min-width: 84px;
  max-width: 84px;
  padding: 4px;
  text-align: center;
  font-size: 0.7rem;
  font-weight: 500;
  cursor: pointer;
  user-select: none;
}
.trip-head.is-selected {
  background: var(--ui-bg-elevated);
  box-shadow: inset 0 -2px 0 0 var(--ui-primary);
}
.trip-head-id {
  display: block;
}
.trip-head-dep {
  display: block;
  font-size: 0.62rem;
  color: var(--ui-text-dimmed);
}
.add-head {
  padding: 4px 8px;
}
.corner-cell {
  position: sticky;
  left: 0;
  top: 0;
  z-index: 3;
  min-width: 180px;
  max-width: 180px;
  padding: 4px 8px;
  text-align: left;
  font-size: 0.7rem;
  font-weight: 600;
}
.stop-cell {
  position: sticky;
  left: 0;
  z-index: 1;
  min-width: 180px;
  max-width: 180px;
  padding: 4px 8px;
  text-align: left;
  font-size: 0.72rem;
  font-weight: 400;
  border-right: 1px solid var(--ui-border);
  border-bottom: 1px solid var(--ui-border);
}
.stop-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: inline-block;
  max-width: 150px;
  vertical-align: middle;
}
.stop-marker {
  margin-left: 4px;
  font-size: 0.7rem;
}
.pad-cell {
  border-bottom: 1px solid var(--ui-border);
}
</style>
