<script setup lang="ts">
import type { TableColumn, TableRow } from '@nuxt/ui'
import type { VehicleRow } from '../composables/useVehiclePolling'
import { adherenceBadge } from '../utils/adherence'
import { useDashboardSelection } from '../composables/useDashboardSelection'

const props = defineProps<{ vehicles: VehicleRow[] }>()
const emit = defineEmits<{ (e: 'select', vehicleId: string): void }>()

const { selectVehicle } = useDashboardSelection()

const columns: TableColumn<VehicleRow>[] = [
  { accessorKey: 'vehicleId', header: 'Vehicle' },
  { id: 'status', header: 'Status' },
  { accessorKey: 'scheduleAdherenceSec', header: 'Adherence' },
  { accessorKey: 'speedMps', header: 'Speed (m/s)' },
]

function onSelect(_event: Event, row: TableRow<VehicleRow>) {
  selectVehicle(row.original.vehicleId)
  emit('select', row.original.vehicleId)
}
</script>

<template>
  <UTable :data="props.vehicles" :columns="columns" @select="onSelect">
    <template #status-cell="{ row }">
      <UBadge
        :color="
          row.original.matched && !row.original.stale
            ? 'success'
            : row.original.stale
              ? 'warning'
              : 'neutral'
        "
      >
        {{
          row.original.matched && !row.original.stale
            ? 'Matched'
            : row.original.stale
              ? 'Stale'
              : 'Unmatched'
        }}
      </UBadge>
    </template>
    <template #scheduleAdherenceSec-cell="{ row }">
      <UBadge :color="adherenceBadge(row.original.scheduleAdherenceSec).color">
        {{ adherenceBadge(row.original.scheduleAdherenceSec).label }}
      </UBadge>
    </template>
  </UTable>
</template>
