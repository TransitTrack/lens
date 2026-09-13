<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import AppPage from '~/components/AppPage.vue'
import ExploreToolbar from '~/components/ExploreToolbar.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useAgencyFilter } from '~/composables/useAgencyFilter'
import { useRouteTypeFilter } from '~/composables/useRouteTypeFilter'
import { useExploreRoutesQuery, type ExploreRoutesQuery } from '~~/generated/graphql'
import { routeTypeLabel, hexColor } from '~/utils/gtfs'

const { selectedFeedCode, feedPath } = useFeeds()
const { agencyId } = useAgencyFilter()
const { typeId } = useRouteTypeFilter()

const { result, loading } = useExploreRoutesQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)

type Row = ExploreRoutesQuery['routes'][number]
const agencyName = computed(() => {
  const map = new Map<string, string>()
  for (const a of result.value?.agencies ?? []) {
    if (a.agencyId) map.set(a.agencyId, a.agencyName ?? a.agencyId)
  }
  return map
})

const search = ref('')
const rows = computed(() => {
  const q = search.value.trim().toLowerCase()
  let all = [...(result.value?.routes ?? [])].sort((a, b) =>
    (a.routeShortName ?? a.routeId).localeCompare(b.routeShortName ?? b.routeId, undefined, {
      numeric: true,
    }),
  )
  if (agencyId.value) all = all.filter((r) => r.agencyId === agencyId.value)
  if (typeId.value) all = all.filter((r) => String(r.routeType) === typeId.value)
  if (!q) return all
  return all.filter((r) =>
    [r.routeShortName, r.routeLongName, r.routeId].some((v) => v?.toLowerCase().includes(q)),
  )
})

const routeStats = computed(() => {
  const all = result.value?.routes ?? []
  return {
    routes: rows.value.length,
    agencies: new Set(rows.value.map((r) => r.agencyId).filter(Boolean)).size,
    modes: new Set(rows.value.map((r) => r.routeType)).size,
    named: rows.value.filter((r) => !!r.routeLongName).length,
    total: all.length,
  }
})

const columns: TableColumn<Row>[] = [
  { accessorKey: 'routeShortName', header: 'Route' },
  { accessorKey: 'routeLongName', header: 'Name' },
  { accessorKey: 'routeType', header: 'Type' },
  { id: 'agency', header: 'Agency' },
]

function onSelect(_e: Event, row: { original: Row }) {
  navigateTo(feedPath(`/explore/routes/${row.original.routeId}`))
}
</script>

<template>
  <AppPage title="Routes" description="Network service catalogue, patterns, and scheduled operations">
    <template #toolbar>
      <ExploreToolbar show-route-type />
    </template>

    <section class="grid overflow-hidden rounded-xl border border-default bg-default sm:grid-cols-2 xl:grid-cols-4">
      <div class="flex flex-col gap-1 border-b border-default p-4 sm:border-r xl:border-b-0">
        <span class="text-xs font-medium uppercase tracking-wide text-dimmed">Visible routes</span>
        <span class="text-2xl font-semibold tabular-nums text-highlighted">{{ routeStats.routes }}</span>
        <span class="text-xs text-muted">{{ routeStats.total }} in this feed</span>
      </div>
      <div class="flex flex-col gap-1 border-b border-default p-4 xl:border-b-0 xl:border-r">
        <span class="text-xs font-medium uppercase tracking-wide text-dimmed">Agencies</span>
        <span class="text-2xl font-semibold tabular-nums text-highlighted">{{ routeStats.agencies }}</span>
        <span class="text-xs text-muted">Represented in this view</span>
      </div>
      <div class="flex flex-col gap-1 border-b border-default p-4 sm:border-r sm:border-b-0 xl:border-r">
        <span class="text-xs font-medium uppercase tracking-wide text-dimmed">Service modes</span>
        <span class="text-2xl font-semibold tabular-nums text-highlighted">{{ routeStats.modes }}</span>
        <span class="text-xs text-muted">GTFS route types</span>
      </div>
      <div class="flex flex-col gap-1 p-4">
        <span class="text-xs font-medium uppercase tracking-wide text-dimmed">Named services</span>
        <span class="text-2xl font-semibold tabular-nums text-highlighted">{{ routeStats.named }}</span>
        <span class="text-xs text-muted">With a route description</span>
      </div>
    </section>

    <section class="overflow-hidden rounded-xl border border-default bg-default">
      <div class="flex flex-wrap items-center justify-between gap-3 border-b border-default px-4 py-3">
        <div>
          <div class="text-sm font-medium text-highlighted">Route directory</div>
          <div class="text-xs text-muted">Select a route to inspect its patterns, stops, and trips.</div>
        </div>
        <UInput v-model="search" icon="i-lucide-search" placeholder="Search routes…" class="w-64 max-w-full" />
      </div>

      <div v-if="loading && !rows.length" class="flex flex-col gap-2 p-3">
        <USkeleton v-for="i in 8" :key="i" class="h-14 w-full" />
      </div>

      <UTable v-else :data="rows" :columns="columns" @select="onSelect">
        <template #routeShortName-cell="{ row }">
          <UBadge :style="{
            backgroundColor: hexColor(row.original.routeColor, undefined),
            color: row.original.routeTextColor ? hexColor(row.original.routeTextColor) : undefined,
          }">
            {{ row.original.routeShortName ?? row.original.routeId }}
          </UBadge>
        </template>
        <template #routeLongName-cell="{ row }">
          <div class="font-medium text-highlighted">{{ row.original.routeLongName?.substring(0, 60) ?? 'Unnamed service'
            }}</div>
          <div class="text-xs text-dimmed">{{ row.original.routeId }}</div>
        </template>
        <template #routeType-cell="{ row }">{{ routeTypeLabel(row.original.routeType) }}</template>
        <template #agency-cell="{ row }">
          {{
            row.original.agencyId
              ? (agencyName.get(row.original.agencyId) ?? row.original.agencyId)
              : '—'
          }}
        </template>
      </UTable>
    </section>
  </AppPage>
</template>
