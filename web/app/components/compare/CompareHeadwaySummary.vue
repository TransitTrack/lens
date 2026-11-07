<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import type { CompareRevisionsQuery } from '~/types/restViews'

type HeadwaySummary = CompareRevisionsQuery['compareRevisions']['headwaySummaries'][number]

defineProps<{ summaries: HeadwaySummary[] }>()
const { t } = useI18n()
const columns = computed<TableColumn<HeadwaySummary>[]>(() => [
  { accessorKey: 'routeId', header: t('explore.col.route') },
  { id: 'direction', header: t('explore.col.dir') },
  { accessorKey: 'serviceId', header: t('explore.col.service') },
  { id: 'tripCount', header: t('compare.tripsFromTo') },
  { id: 'meanGap', header: t('compare.meanGap') },
  { id: 'maxGap', header: t('compare.maxGap') },
])
function fmtGap(s: number | null | undefined): string {
  return s == null ? '—' : t('explore.minutesShort', { n: Math.round(s / 60) })
}
</script>

<template>
  <div>
    <h2 class="mb-2 text-sm font-semibold text-highlighted">{{ $t('compare.headwayTitle') }}</h2>
    <div v-if="!summaries.length" class="text-sm text-muted">{{ $t('compare.noHeadway') }}</div>
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
