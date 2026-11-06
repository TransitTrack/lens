<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import AppPage from '~/components/shell/AppPage.vue'
import ExploreToolbar from '~/components/explore/ExploreToolbar.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useAgencyFilter } from '~/composables/useAgencyFilter'
import { useRouteTypeFilter } from '~/composables/useRouteTypeFilter'
import { useExploreRoutesQuery, type ExploreRoutesQuery } from '~~/generated/graphql'
import { routeTextColor, routeTypeLabel, hexColor } from '~/utils/gtfs'

const { t } = useI18n()
const { selectedFeedCode, feedPath } = useFeeds()
const { agencyId } = useAgencyFilter()
const { typeId } = useRouteTypeFilter()

const { result, loading } = useExploreRoutesQuery(() => ({
  variables: { feedCode: selectedFeedCode.value ?? '' },
  enabled: !!selectedFeedCode.value,
}))

type Row = ExploreRoutesQuery['routes'][number]
const agencyName = computed(() => {
  const map = new Map<string, string>()
  for (const a of result.value?.agencies ?? []) {
    if (a.agencyId) map.set(a.agencyId, a.agencyName ?? a.agencyId)
  }
  return map
})

const { onlyWithFindings, count: findingsCount, keep: hasFindings } = useFindingsFilter(
  selectedFeedCode,
  'Route',
)
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
  return all.filter(
    (r) =>
      hasFindings(r.routeId) &&
      (!q || [r.routeShortName, r.routeLongName, r.routeId].some((v) => v?.toLowerCase().includes(q))),
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

const columns = computed<TableColumn<Row>[]>(() => [
  { accessorKey: 'routeShortName', header: t('explore.col.route') },
  { accessorKey: 'routeLongName', header: t('explore.col.name') },
  { accessorKey: 'routeType', header: t('explore.col.type') },
  { id: 'agency', header: t('explore.col.agency') },
])

function onSelect(_e: Event, row: { original: Row }) {
  navigateTo(feedPath(`/explore/routes/${row.original.routeId}`))
}
</script>

<template>
  <AppPage :title="t('nav.routes')" :description="t('explore.routes.description')">
    <template #toolbar>
      <ExploreToolbar show-route-type />
    </template>

    <div class="flex flex-col gap-6">
      <section class="app-frame grid sm:grid-cols-2 xl:grid-cols-4">
        <div class="flex flex-col gap-1 border-b border-default p-4 sm:border-r xl:border-b-0">
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed"
            >{{
            t('explore.routes.visibleRoutes')
          }}</span>
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            routeStats.routes
          }}</span>
          <span class="text-xs text-muted">{{ t('explore.routes.inFeed', { count: routeStats.total }) }}</span>
        </div>
        <div class="flex flex-col gap-1 border-b border-default p-4 xl:border-b-0 xl:border-r">
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed">{{
            t('explore.routes.agencies')
          }}</span>
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            routeStats.agencies
          }}</span>
          <span class="text-xs text-muted">{{ t('explore.routes.representedInView') }}</span>
        </div>
        <div
          class="flex flex-col gap-1 border-b border-default p-4 sm:border-r sm:border-b-0 xl:border-r"
        >
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed">{{
            t('explore.routes.serviceModes')
          }}</span>
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            routeStats.modes
          }}</span>
          <span class="text-xs text-muted">{{ t('explore.routes.gtfsRouteTypes') }}</span>
        </div>
        <div class="flex flex-col gap-1 p-4">
          <span class="text-xs font-medium uppercase tracking-wide text-dimmed"
            >{{
            t('explore.routes.namedServices')
          }}</span>
          <span class="text-2xl font-semibold tabular-nums text-highlighted">{{
            routeStats.named
          }}</span>
          <span class="text-xs text-muted">{{ t('explore.routes.withDescription') }}</span>
        </div>
      </section>

      <section class="app-frame overflow-scroll">
        <div
          class="flex flex-wrap items-center justify-between gap-3 border-b border-default px-4 py-3"
        >
          <div>
            <div class="text-sm font-medium text-highlighted">
              {{ t('explore.routes.directory') }}
            </div>
            <div class="text-xs text-muted">
              {{ t('explore.routes.selectRoute') }}
            </div>
          </div>
          <div class="flex flex-wrap items-center gap-3">
            <USwitch
              v-if="findingsCount > 0 || onlyWithFindings"
              v-model="onlyWithFindings"
              size="sm"
              :label="t('findings.filterWith', { count: findingsCount })"
            />
            <UInput
              v-model="search"
              icon="i-lucide-search"
              :placeholder="t('explore.routes.searchPlaceholder')"
              class="w-64 max-w-full"
            />
          </div>
        </div>

        <div v-if="loading && !rows.length" class="flex flex-col gap-2 p-3">
          <USkeleton v-for="i in 8" :key="i" class="h-14 w-full" />
        </div>

        <UTable v-else :data="rows" :columns="columns" @select="onSelect">
          <template #routeShortName-cell="{ row }">
            <UBadge
              :style="{
                backgroundColor: hexColor(row.original.routeColor, undefined),
                color: routeTextColor(
                  hexColor(row.original.routeColor),
                  row.original.routeTextColor,
                ),
              }"
            >
              {{ row.original.routeShortName ?? row.original.routeId }}
            </UBadge>
          </template>
          <template #routeLongName-cell="{ row }">
            <div class="font-medium text-highlighted">
              {{ row.original.routeLongName?.substring(0, 60) ?? t('explore.routes.unnamed') }}
            </div>
            <div class="text-xs text-dimmed">{{ row.original.routeId }}</div>
          </template>
          <template #routeType-cell="{ row }">{{
            routeTypeLabel(row.original.routeType, t)
          }}</template>
          <template #agency-cell="{ row }">
            {{
              row.original.agencyId
                ? (agencyName.get(row.original.agencyId) ?? row.original.agencyId)
                : '—'
            }}
          </template>
        </UTable>
      </section>
    </div>
  </AppPage>
</template>
