<script setup lang="ts">
import {useRoutesQuery} from '../../generated/graphql'
import {
  STATUS_OPTIONS,
  emptyFilters,
  type VehicleFilterState,
} from '../utils/vehicleFilters'

const props = defineProps<{ feedCode: string | null }>()
const model = defineModel<VehicleFilterState>({required: true})

const {result} = useRoutesQuery(
  () => ({feedCode: props.feedCode ?? ''}),
  () => ({enabled: !!props.feedCode}),
)

const routeItems = computed(() => [
  {label: 'All routes', value: null},
  ...[...(result.value?.routes ?? [])]
    .sort((a, b) =>
      (a.routeShortName ?? a.routeId).localeCompare(b.routeShortName ?? b.routeId, undefined, {
        numeric: true,
      }),
    )
    .map((r) => ({
      label: r.routeShortName ?? r.routeLongName ?? r.routeId,
      value: r.routeId,
    })),
])

const dirty = computed(() =>
  model.value.query.trim().length > 0 || model.value.routeId != null || model.value.statuses.length > 0,
)

function reset() {
  model.value = emptyFilters()
}
</script>

<template>
  <div class="flex flex-wrap items-center gap-2">
    <UInput
      :model-value="model.query"
      icon="i-lucide-search"
      placeholder="Search vehicle, route, destination"
      class="w-64 max-w-full"
      @update:model-value="model = { ...model, query: $event }"
    />
    <USelectMenu
      :model-value="model.routeId"
      :items="routeItems"
      value-key="value"
      placeholder="Route"
      icon="i-lucide-route"
      class="w-44"
      @update:model-value="model = { ...model, routeId: $event }"
    />
    <USelectMenu
      :model-value="model.statuses"
      :items="STATUS_OPTIONS"
      value-key="value"
      multiple
      placeholder="Status"
      icon="i-lucide-filter"
      class="w-44"
      @update:model-value="model = { ...model, statuses: $event }"
    />
    <UButton
      v-if="dirty"
      color="neutral"
      variant="ghost"
      icon="i-lucide-x"
      label="Clear"
      @click="reset"
    />
  </div>
</template>
