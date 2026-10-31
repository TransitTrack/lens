<script setup lang="ts">
import type { VehicleRow } from '../composables/useVehiclePolling'
import {
  VEHICLE_STATES,
  routeFacets,
  stateCounts,
  type VehicleFilterState,
  type VehicleState,
} from '../utils/vehicleFilters'
import { hexColor, routeTextColor } from '~/utils/gtfs'

/**
 * Filter column for the vehicle list: one state at a time (each with its live count) and any
 * number of routes (counted for the chosen state). Search lives with the list, not here.
 */
const props = defineProps<{ vehicles: VehicleRow[] }>()
const model = defineModel<VehicleFilterState>({ required: true })

const counts = computed(() => stateCounts(props.vehicles, model.value))
const routes = computed(() => routeFacets(props.vehicles, model.value))

function pickState(state: VehicleState | null) {
  model.value = { ...model.value, state }
}

function toggleRoute(routeId: string) {
  const routeIds = model.value.routeIds.includes(routeId)
    ? model.value.routeIds.filter((id) => id !== routeId)
    : [...model.value.routeIds, routeId]
  model.value = { ...model.value, routeIds }
}

const stateItems = computed(() => [
  { value: null, label: 'All', dotClass: null, count: counts.value.all },
  ...VEHICLE_STATES.map((s) => ({ ...s, count: counts.value[s.value] })),
])

function badgeStyle(color: string | null, textColor: string | null) {
  const background = hexColor(color)
  return { backgroundColor: background, color: routeTextColor(background, textColor) }
}
</script>

<template>
  <div class="space-y-5 text-sm">
    <section aria-labelledby="vehicle-facets-state">
      <h3
        id="vehicle-facets-state"
        class="mb-1.5 px-2 text-xs font-semibold uppercase tracking-wide text-dimmed"
      >
        State
      </h3>
      <ul class="space-y-0.5">
        <li v-for="item in stateItems" :key="item.value ?? 'all'">
          <button
            type="button"
            class="flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-left transition-colors hover:bg-elevated"
            :class="
              model.state === item.value
                ? 'bg-primary/10 font-semibold text-primary'
                : 'text-default'
            "
            :aria-pressed="model.state === item.value"
            :data-testid="`vehicle-state-${item.value ?? 'all'}`"
            @click="pickState(item.value)"
          >
            <span
              v-if="item.dotClass"
              class="size-2 shrink-0 rounded-full"
              :class="item.dotClass"
            />
            <span class="min-w-0 flex-1 truncate">{{ item.label }}</span>
            <span
              class="tabular-nums"
              :class="model.state === item.value ? 'text-primary' : 'text-muted'"
              >{{ item.count }}</span
            >
          </button>
        </li>
      </ul>
    </section>

    <section aria-labelledby="vehicle-facets-routes">
      <div class="mb-1.5 flex items-center justify-between px-2">
        <h3
          id="vehicle-facets-routes"
          class="text-xs font-semibold uppercase tracking-wide text-dimmed"
        >
          Routes
        </h3>
        <button
          v-if="model.routeIds.length"
          type="button"
          class="text-xs text-muted hover:text-primary"
          @click="model = { ...model, routeIds: [] }"
        >
          Clear
        </button>
      </div>
      <p v-if="!routes.length" class="px-2 text-xs text-dimmed">No vehicle is on a trip.</p>
      <ul v-else class="space-y-0.5">
        <li v-for="route in routes" :key="route.routeId">
          <label
            class="flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 transition-colors hover:bg-elevated"
            :class="{ 'opacity-50': route.count === 0 && !model.routeIds.includes(route.routeId) }"
          >
            <UCheckbox
              :model-value="model.routeIds.includes(route.routeId)"
              @update:model-value="toggleRoute(route.routeId)"
            />
            <UBadge size="sm" :style="badgeStyle(route.color, route.textColor)">{{
              route.label
            }}</UBadge>
            <span class="ml-auto tabular-nums text-muted">{{ route.count }}</span>
          </label>
        </li>
      </ul>
    </section>
  </div>
</template>
