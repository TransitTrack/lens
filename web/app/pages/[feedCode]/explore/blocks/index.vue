<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import AppPage from '~/components/AppPage.vue'
import ExploreToolbar from '~/components/ExploreToolbar.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useAgencyFilter } from '~/composables/useAgencyFilter'
import {
  useBlocksListQuery,
  useExploreRoutesQuery,
  useExploreCalendarQuery,
  type BlocksListQuery,
} from '~~/generated/graphql'
import { serviceDaysLabel } from '~/utils/gtfs'
import { blockDurationSec, peakConcurrency } from '~/utils/blocks'

const { selectedFeedCode, feedPath } = useFeeds()
const { agencyId } = useAgencyFilter()

const { result, loading } = useBlocksListQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const { result: routesResult } = useExploreRoutesQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const { result: calResult } = useExploreCalendarQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)

type Block = BlocksListQuery['blocks'][number]
const allBlocks = computed(() => result.value?.blocks ?? [])

const routeAgency = computed(() => {
  const m = new Map<string, string>()
  for (const r of routesResult.value?.routes ?? []) {
    if (r.agencyId) m.set(r.routeId, r.agencyId)
  }
  return m
})

const calLabel = computed(() => {
  const m = new Map<string, string>()
  for (const c of calResult.value?.calendars ?? []) m.set(c.serviceId, serviceDaysLabel(c))
  return m
})

const serviceItems = computed(() => {
  const ids = [...new Set(allBlocks.value.map((b) => b.serviceId))].sort()
  return [
    { label: 'All services', value: null },
    ...ids.map((id) => ({
      label: calLabel.value.get(id) ? `${calLabel.value.get(id)} — ${id}` : id,
      value: id,
    })),
  ]
})
const serviceId = ref<string | null>(null)
const search = ref('')

const blocks = computed(() => {
  let list = allBlocks.value
  if (serviceId.value) list = list.filter((b) => b.serviceId === serviceId.value)
  if (agencyId.value) {
    list = list.filter((b) =>
      b.routeIds.some((rid) => routeAgency.value.get(rid) === agencyId.value),
    )
  }
  const query = search.value.trim().toLowerCase()
  if (query) {
    list = list.filter((b) =>
      [b.blockId, b.serviceId, ...b.routeIds].some((value) => value.toLowerCase().includes(query)),
    )
  }
  return [...list].sort((a, b) => a.startTimeSec - b.startTimeSec)
})

const stats = computed(() => {
  const list = blocks.value
  const totalSec = list.reduce((s, b) => s + blockDurationSec(b), 0)
  const trips = list.reduce((s, b) => s + b.tripCount, 0)
  return [
    { label: 'Blocks', value: String(list.length) },
    { label: 'Vehicle-hours', value: (totalSec / 3600).toFixed(0) },
    { label: 'Avg trips/block', value: list.length ? (trips / list.length).toFixed(1) : '0' },
    { label: 'Peak concurrent', value: String(peakConcurrency(list)) },
  ]
})

const columns: TableColumn<Block>[] = [
  { accessorKey: 'blockId', header: 'Block' },
  { accessorKey: 'serviceId', header: 'Service' },
  { id: 'span', header: 'Span' },
  { id: 'duration', header: 'Duration' },
  { accessorKey: 'tripCount', header: 'Trips' },
  { id: 'routes', header: 'Routes' },
]

const routeName = computed(() => {
  const m = new Map<string, { name: string; color: string | null }>()
  for (const r of routesResult.value?.routes ?? []) {
    m.set(r.routeId, { name: r.routeShortName ?? r.routeId, color: r.routeColor ?? null })
  }
  return m
})

function onSelect(_e: Event, row: { original: Block }) {
  navigateTo(
    feedPath(
      `/explore/blocks/${encodeURIComponent(row.original.blockId)}?service=${encodeURIComponent(row.original.serviceId)}`,
    ),
  )
}

function durH(b: Block): string {
  const s = blockDurationSec(b)
  return `${Math.floor(s / 3600)}h ${Math.round((s % 3600) / 60)}m`
}
</script>

<template>
  <AppPage title="Blocks" description="Vehicle work assignments across the selected schedule">
    <template #toolbar>
      <ExploreToolbar />
    </template>

    <section class="overflow-hidden rounded-xl border border-default bg-elevated/20">
      <div class="flex flex-col gap-3 p-3 sm:flex-row sm:items-center">
        <UInput v-model="search" icon="i-lucide-search" placeholder="Find a block, service, or route…"
          class="w-full sm:max-w-sm" />
        <USelectMenu v-model="serviceId" :items="serviceItems" value-key="value" placeholder="Service"
          icon="i-lucide-calendar" class="w-full sm:w-64" />
        <span class="text-xs text-dimmed sm:ml-auto">
          {{ blocks.length.toLocaleString() }} matching blocks
        </span>
      </div>
      <div class="grid grid-cols-2 border-t border-default sm:grid-cols-4">
        <div v-for="s in stats" :key="s.label"
          class="border-b border-r border-default px-4 py-3 last:border-r-0 sm:border-b-0">
          <div class="text-lg font-semibold tracking-tight text-highlighted">{{ s.value }}</div>
          <div class="text-xs text-muted">{{ s.label }}</div>
        </div>
      </div>
    </section>

    <div v-if="loading && !blocks.length" class="flex flex-col gap-2">
      <USkeleton v-for="i in 8" :key="i" class="h-10 w-full" />
    </div>

    <section v-else class="overflow-hidden rounded-xl border border-default">
      <div class="flex items-center justify-between border-b border-default bg-elevated/30 px-4 py-2.5">
        <span class="text-sm font-medium text-highlighted">Block assignments</span>
        <span class="text-xs text-dimmed">Select a row for its timeline</span>
      </div>
      <UTable :data="blocks" sticky :columns="columns" :virtualize="{ estimateSize: 64, overscan: 10 }"
        class="max-h-[calc(100vh-22rem)] overflow-auto" @select="onSelect">
        <template #blockId-cell="{ row }">
          <span class="font-medium text-highlighted">{{ row.original.blockId }}</span>
        </template>
        <template #span-cell="{ row }">
          {{ secToHm(row.original.startTimeSec) }}–{{ secToHm(row.original.endTimeSec) }}
        </template>
        <template #duration-cell="{ row }">{{ durH(row.original) }}</template>
        <template #routes-cell="{ row }">
          <div class="flex flex-wrap gap-1">
            <UBadge v-for="rid in row.original.routeIds" :key="rid" :style="{
              backgroundColor: routeName.get(rid)?.color
                ? `#${routeName.get(rid)!.color!.replace('#', '')}`
                : undefined,
            }">
              {{ routeName.get(rid)?.name ?? rid }}
            </UBadge>
          </div>
        </template>
      </UTable>
    </section>
  </AppPage>
</template>
