<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import { useVehiclePredictionsQuery, type VehiclePredictionsQuery } from '../../generated/graphql'
import { useDashboardSelection } from '../composables/useDashboardSelection'

type PredictionRow = VehiclePredictionsQuery['vehiclePredictions'][number]

const { selectedFeedCode, selectedVehicleId, selectVehicle } = useDashboardSelection()

const isOpen = computed({
  get: () => !!selectedVehicleId.value,
  set: (v: boolean) => {
    if (!v) selectVehicle(null)
  },
})

const { result } = useVehiclePredictionsQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '', vehicleId: selectedVehicleId.value ?? '' }),
  () => ({
    enabled: !!selectedFeedCode.value && !!selectedVehicleId.value,
    pollInterval: 5000,
  }),
)

const predictions = computed<PredictionRow[]>(() => result.value?.vehiclePredictions ?? [])

const columns: TableColumn<PredictionRow>[] = [
  { id: 'stopName', header: 'Stop' },
  { accessorKey: 'scheduledArrival', header: 'Scheduled' },
  { accessorKey: 'predictedArrival', header: 'Predicted' },
  { accessorKey: 'actualArrival', header: 'Actual' },
  { accessorKey: 'algorithm', header: 'Algorithm' },
]

function formatTs(iso: string | null | undefined): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(iso))
}
</script>

<template>
  <USlideover v-model:open="isOpen" :portal="false">
    <template #header>
      <h3 class="text-lg font-semibold">Vehicle {{ selectedVehicleId }}</h3>
    </template>
    <template #body>
      <UTable :data="predictions" :columns="columns">
        <template #stopName-cell="{ row }">
          {{ row.original.stop?.stopName ?? row.original.stop?.stopId ?? '—' }}
        </template>
        <template #scheduledArrival-cell="{ row }">{{ formatTs(row.original.scheduledArrival) }}</template>
        <template #predictedArrival-cell="{ row }">{{ formatTs(row.original.predictedArrival) }}</template>
        <template #actualArrival-cell="{ row }">{{ formatTs(row.original.actualArrival) }}</template>
      </UTable>
    </template>
  </USlideover>
</template>
