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
  <AppPage title="Routes">
    <template #toolbar>
      <ExploreToolbar show-route-type />
    </template>

    <div class="flex flex-wrap items-center justify-between gap-2">
      <UInput
        v-model="search"
        icon="i-lucide-search"
        placeholder="Search routes…"
        class="max-w-xs"
      />
      <span class="text-xs text-dimmed">{{ rows.length }} routes</span>
    </div>

    <div v-if="loading && !rows.length" class="flex flex-col gap-2">
      <USkeleton v-for="i in 8" :key="i" class="h-10 w-full" />
    </div>

    <UTable v-else :data="rows" :columns="columns" @select="onSelect">
      <template #routeShortName-cell="{ row }">
        <UBadge
          size="sm"
          :style="{
            backgroundColor: hexColor(row.original.routeColor, undefined),
            color: row.original.routeTextColor ? hexColor(row.original.routeTextColor) : undefined,
          }"
        >
          {{ row.original.routeShortName ?? row.original.routeId }}
        </UBadge>
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
  </AppPage>
</template>
