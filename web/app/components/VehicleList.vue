<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import type { Column } from '@tanstack/table-core'
import type { TableColumn, TableRow } from '@nuxt/ui'
import type { VehicleRow } from '../composables/useVehiclePolling'
import { adherenceBadge } from '../utils/adherence'

const props = defineProps<{ vehicles: VehicleRow[] }>()
const emit = defineEmits<{ (e: 'select', vehicleId: string): void }>()

const sorting = ref([{ id: 'vehicleId', desc: false }])

function sortableHeader(label: string, column: Column<VehicleRow>) {
  const isSorted = column.getIsSorted()
  return h(resolveComponent('UButton'), {
    label,
    color: 'neutral',
    variant: 'ghost',
    icon:
      isSorted === 'asc'
        ? 'i-lucide-arrow-up'
        : isSorted === 'desc'
          ? 'i-lucide-arrow-down'
          : 'i-lucide-arrow-up-down',
    onClick: () => column.toggleSorting(isSorted === 'asc'),
  })
}

const columns: TableColumn<VehicleRow>[] = [
  { accessorKey: 'vehicleId', header: ({ column }) => sortableHeader('Vehicle', column) },
  {
    id: 'status',
    header: 'Status',
    accessorFn: (row) =>
      row.matched && !row.stale ? 'Matched' : row.stale ? 'Stale' : 'Unmatched',
  },
  {
    accessorKey: 'scheduleAdherenceSec',
    header: ({ column }) => sortableHeader('Adherence', column),
  },
  { accessorKey: 'speedMps', header: ({ column }) => sortableHeader('Speed (m/s)', column) },
]

function onSelect(_event: Event, row: TableRow<VehicleRow>) {
  emit('select', row.original.vehicleId)
}
</script>

<template>
  <UTable v-model:sorting="sorting" :data="props.vehicles" :columns="columns" @select="onSelect">
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
