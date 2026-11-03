<script setup lang="ts">
import { h, resolveComponent } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import { useAvlLogQuery } from '../../generated/graphql'
import { usePollControl } from '../composables/usePollControl'
import { useUnits } from '../composables/useUnits'
import { adherenceBadge } from '../utils/adherence'
import { formatClock } from '../utils/clock'
import { matchStatusLabel } from '../utils/vehicleTelemetry'

const props = defineProps<{
  feedCode: string
  vehicleId: string
}>()
const open = defineModel<boolean>('open', { default: false })

const { t } = useI18n()
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
  return formatClock(iso, { seconds: true })
}

const columns = computed<TableColumn<AvlLogRow>[]>(() => [
  {
    accessorKey: 'ts',
    header: t('vehicles.avl.column.time'),
    cell: ({ row }) => hhmmss(row.original.ts),
  },
  {
    id: 'position',
    header: t('vehicles.field.position'),
    cell: ({ row }) =>
      h(
        'span',
        { class: 'font-mono text-xs tabular-nums text-muted' },
        `${row.original.position.lat.toFixed(5)}, ${row.original.position.lon.toFixed(5)}`,
      ),
  },
  {
    id: 'status',
    header: t('vehicles.avl.column.status'),
    cell: ({ row }) =>
      h(
        resolveComponent('UBadge'),
        { color: STATUS_COLOR[row.original.matchStatus] ?? 'neutral', variant: 'subtle' },
        () => matchStatusLabel(row.original.matchStatus, t),
      ),
  },
  {
    id: 'route',
    header: t('vehicles.field.route'),
    cell: ({ row }) => {
      const trip = row.original.match?.trip
      if (!trip) return h('span', { class: 'text-dimmed' }, '—')
      return h('span', { class: 'font-medium text-highlighted' }, trip.route?.routeShortName ?? trip.routeId)
    },
  },
  {
    id: 'trip',
    header: t('vehicles.field.trip'),
    cell: ({ row }) => {
      const trip = row.original.match?.trip
      if (!trip) {
        return h('div', { class: 'min-w-0' }, [
          h('span', { class: 'block text-xs text-dimmed' }, t('vehicles.avl.unmatchedDescriptor')),
          h('span', { class: 'mt-0.5 block truncate font-mono text-xs text-muted' }, row.original.descTripId ?? '—'),
        ])
      }
      return h('div', { class: 'min-w-0' }, [
        h('span', { class: 'block truncate font-medium text-highlighted' }, trip.tripId),
        trip.tripHeadsign
          ? h('span', { class: 'mt-0.5 block truncate text-xs text-muted' }, trip.tripHeadsign)
          : null,
      ])
    },
  },
  {
    id: 'deviation',
    header: t('vehicles.avl.column.deviation'),
    cell: ({ row }) => {
      const m = row.original.match?.deviationM
      return m == null ? '—' : units.distance(m)
    },
  },
  {
    id: 'adherence',
    header: t('vehicles.field.adherence'),
    cell: ({ row }) => {
      if (!row.original.match) return '—'
      const badge = adherenceBadge(row.original.match.scheduleAdherenceSec, t)
      return h(resolveComponent('UBadge'), { color: badge.color, variant: 'subtle' }, () => badge.label)
    },
  },
  {
    id: 'score',
    header: t('vehicles.avl.column.score'),
    cell: ({ row }) => row.original.match?.score?.toFixed(2) ?? '—',
  },
  {
    accessorKey: 'speedMps',
    header: t('vehicles.field.speed'),
    cell: ({ row }) => units.speed(row.original.speedMps),
  },
])
</script>

<template>
  <USlideover
    v-model:open="open"
:title="t('vehicles.avl.title')"
    :description="t('vehicles.avl.description')"
    :ui="{ content: 'sm:w-[min(94vw,88rem)] sm:max-w-none' }"
  >
    <template #body>
      <UAlert
        v-if="error"
        color="error"
        variant="soft"
        icon="i-lucide-alert-triangle"
        :title="t('vehicles.avl.loadError')"
        :description="error.message"
      />
      <div v-else-if="loading && !rows.length" class="flex flex-col gap-2">
        <USkeleton v-for="i in 8" :key="i" class="h-10 w-full"/>
      </div>
      <p v-else-if="!rows.length" class="text-sm text-muted">{{ t('vehicles.avl.empty') }}</p>
      <UTable v-else :data="rows" :columns="columns"/>
    </template>
  </USlideover>
</template>
