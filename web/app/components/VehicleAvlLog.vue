<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import { useAvlLogQuery } from '../../generated/graphql'
import { usePollControl } from '../composables/usePollControl'
import { useUnits } from '../composables/useUnits'
import { adherenceBadge } from '../utils/adherence'

const props = defineProps<{
  feedCode: string
  vehicleId: string
}>()
const open = defineModel<boolean>('open', { default: false })

const units = useUnits()
const { intervalMs } = usePollControl()

const { result, loading, error } = useAvlLogQuery(
  () => ({ feedCode: props.feedCode, vehicleId: props.vehicleId, limit: 100 }),
  () => ({ enabled: open.value, pollInterval: open.value ? intervalMs.value : 0 }),
)

type AvlLogRow = NonNullable<typeof result.value>['avlReports'][number]

const rows = computed<AvlLogRow[]>(() => result.value?.avlReports ?? [])

const STATUS_COLOR: Record<string, 'success' | 'error' | 'neutral' | 'warning'> = {
  MATCHED: 'success',
  UNMATCHED: 'error',
  SKIPPED: 'neutral',
  PENDING: 'warning',
}

function hhmmss(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(
    new Date(iso),
  )
}

const columns: TableColumn<AvlLogRow>[] = [
  {
    accessorKey: 'ts',
    header: 'Time',
    cell: ({ row }) => hhmmss(row.original.ts),
  },
  {
    id: 'status',
    header: 'Status',
    cell: ({ row }) =>
      h(
        resolveComponent('UBadge'),
        { color: STATUS_COLOR[row.original.matchStatus] ?? 'neutral', variant: 'subtle' },
        () => row.original.matchStatus,
      ),
  },
  {
    id: 'trip',
    header: 'Trip',
    cell: ({ row }) => {
      const trip = row.original.match?.trip
      if (!trip) return h('span', { class: 'text-dimmed' }, row.original.descTripId ?? '—')
      const short = trip.route?.routeShortName
      const label = [short, trip.tripHeadsign].filter(Boolean).join(' · ')
      return h('span', { class: 'truncate' }, label || trip.tripId)
    },
  },
  {
    id: 'deviation',
    header: 'Deviation',
    cell: ({ row }) => {
      const m = row.original.match?.deviationM
      return m == null ? '—' : units.distance(m)
    },
  },
  {
    id: 'adherence',
    header: 'Adherence',
    cell: ({ row }) => {
      if (!row.original.match) return '—'
      const badge = adherenceBadge(row.original.match.scheduleAdherenceSec)
      return h(resolveComponent('UBadge'), { color: badge.color, variant: 'subtle' }, () => badge.label)
    },
  },
  {
    id: 'score',
    header: 'Score',
    cell: ({ row }) => row.original.match?.score?.toFixed(2) ?? '—',
  },
  {
    accessorKey: 'speedMps',
    header: 'Speed',
    cell: ({ row }) => units.speed(row.original.speedMps),
  },
]
</script>

<template>
  <USlideover
    v-model:open="open"
    title="AVL log"
    description="Raw AVL reports for this vehicle, newest first, with the match each one produced."
    :ui="{ content: 'max-w-4xl' }"
  >
    <template #body>
      <UAlert
        v-if="error"
        color="error"
        variant="soft"
        icon="i-lucide-alert-triangle"
        title="Couldn't load the AVL log"
        :description="error.message"
      />
      <div v-else-if="loading && !rows.length" class="flex flex-col gap-2">
        <USkeleton v-for="i in 8" :key="i" class="h-10 w-full"/>
      </div>
      <p v-else-if="!rows.length" class="text-sm text-muted">No AVL reports for this vehicle yet.</p>
      <UTable v-else :data="rows" :columns="columns"/>
    </template>
  </USlideover>
</template>
