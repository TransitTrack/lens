<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { CompareRevisionsQuery } from '~~/generated/graphql'

type HeadwaySummary = CompareRevisionsQuery['compareRevisions']['headwaySummaries'][number]

defineProps<{ summaries: HeadwaySummary[] }>()

const columns: TableColumn<HeadwaySummary>[] = [
  { accessorKey: 'routeId', header: 'Route' },
  { id: 'direction', header: 'Dir' },
  { accessorKey: 'serviceId', header: 'Service' },
  { id: 'tripCount', header: 'Trips (from→to)' },
  { id: 'meanGap', header: 'Mean gap (from→to)' },
  { id: 'maxGap', header: 'Max gap (from→to)' },
]

function fmtGap(s: number | null | undefined): string {
  return s == null ? '—' : `${Math.round(s / 60)}m`
}
</script>

<template>
  <div>
    <h2 class="mb-2 text-sm font-semibold text-highlighted">Headway summary</h2>
    <div v-if="!summaries.length" class="text-sm text-muted">No headway data for this filter.</div>
    <UTable v-else :data="summaries" :columns="columns">
      <template #direction-cell="{ row }">{{ row.original.directionId ?? '—' }}</template>
      <template #tripCount-cell="{ row }">{{ row.original.fromTripCount }} → {{ row.original.toTripCount }}</template>
      <template #meanGap-cell="{ row }">
        {{ fmtGap(row.original.fromMeanGapSec) }} → {{ fmtGap(row.original.toMeanGapSec) }}
      </template>
      <template #maxGap-cell="{ row }">
        {{ fmtGap(row.original.fromMaxGapSec) }} → {{ fmtGap(row.original.toMaxGapSec) }}
      </template>
    </UTable>
  </div>
</template>
