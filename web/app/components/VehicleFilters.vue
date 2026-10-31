<script setup lang="ts">
import type { VehicleRow } from '../composables/useVehiclePolling'
import {
  VEHICLE_STATES,
  emptyFilters,
  routeFacets,
  stateCounts,
  type VehicleFilterState,
} from '../utils/vehicleFilters'

/**
 * Compact filter bar for narrow vehicle lists (e.g. beside the Overview map): the same search,
 * state and route filters as the Vehicles page's [VehicleFacets] column, as dropdowns.
 */
const props = defineProps<{ vehicles: VehicleRow[] }>()
const model = defineModel<VehicleFilterState>({ required: true })

const counts = computed(() => stateCounts(props.vehicles, model.value))

const stateItems = computed(() => [
  { label: `All · ${counts.value.all}`, value: null },
  ...VEHICLE_STATES.map((s) => ({
    label: `${s.label} · ${counts.value[s.value]}`,
    value: s.value,
  })),
])

const routeItems = computed(() =>
  routeFacets(props.vehicles, model.value).map((r) => ({
    label: `${r.label} · ${r.count}`,
    value: r.routeId,
  })),
)

const dirty = computed(
  () =>
    model.value.query.trim().length > 0 ||
    model.value.state != null ||
    model.value.routeIds.length > 0,
)
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <UInput
      :model-value="model.query"
      icon="i-lucide-search"
      placeholder="Search vehicle, route, trip or block"
      class="w-full"
      @update:model-value="model = { ...model, query: $event }"
    />
    <USelectMenu
      :model-value="model.state"
      :items="stateItems"
      value-key="value"
      placeholder="State"
      icon="i-lucide-filter"
      :search-input="false"
      class="w-40"
      @update:model-value="model = { ...model, state: $event }"
    />
    <USelectMenu
      :model-value="model.routeIds"
      :items="routeItems"
      value-key="value"
      multiple
      placeholder="Routes"
      icon="i-lucide-route"
      class="w-36"
      @update:model-value="model = { ...model, routeIds: $event }"
    />
    <UButton
      v-if="dirty"
      color="neutral"
      variant="soft"
      size="sm"
      icon="i-lucide-x"
      aria-label="Clear filters"
      @click="model = emptyFilters()"
    />
  </div>
</template>
