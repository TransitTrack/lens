<script setup lang="ts">
import { useQuery } from '@pinia/colada'
import { stopsQuery, routesQuery, agenciesQuery, validationFindingCountsQuery } from '~/composables/api/queries/gtfs'
import { tripPatternsQuery, stopPathsQuery } from '~/composables/api/queries/schedule'
import type { Stop } from '~/api'
import { useFindingsFilter } from '~/composables/useFindingsFilter'
import { groupStopPaths, patternsWithStopPaths } from '~/utils/joins/stopPaths'
import { indexBy, withRoute } from '~/utils/joins/routes'
import { agenciesExtent } from '~/utils/joins/extent'
import SplitterGrip from '~/components/shell/SplitterGrip.vue'
import { formatNumber } from '~/utils/intlLocale'
import type { SplitterItem, TableColumn } from '@nuxt/ui'
import AppPage from '~/components/shell/AppPage.vue'
import ExploreToolbar from '~/components/explore/ExploreToolbar.vue'
import StopsMap from '~/components/explore/StopsMap.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useAgencyFilter } from '~/composables/useAgencyFilter'
import { useExplorePatterns, type Pattern } from '~/composables/useExplorePatterns'
import { useSplitLayout } from '~/composables/useSplitLayout'

const { t, locale } = useI18n()
const { selectedFeedCode, feedPath } = useFeeds()

const enabled = computed(() => !!selectedFeedCode.value)
const { data: stopRows, isLoading: loading } = useQuery(() => ({
  ...stopsQuery({ feedCode: selectedFeedCode.value! }),
  enabled: enabled.value,
}))
const { data: agencies } = useQuery(() => ({
  ...agenciesQuery({ feedCode: selectedFeedCode.value! }),
  enabled: enabled.value,
}))
const { data: findingCounts } = useQuery(() => ({
  ...validationFindingCountsQuery({ feedCode: selectedFeedCode.value!, entityType: 'Stop' }),
  enabled: enabled.value,
}))
const extent = computed(() => agenciesExtent(agencies.value ?? []))
const { agencyId } = useAgencyFilter(agencies)

// only needed to resolve which stops an agency serves
const patternsEnabled = computed(() => enabled.value && !!agencyId.value)
const { data: tripPatterns } = useQuery(() => ({
  ...tripPatternsQuery({ feedCode: selectedFeedCode.value! }),
  enabled: patternsEnabled.value,
}))
const { data: stopPaths } = useQuery(() => ({
  ...stopPathsQuery({ feedCode: selectedFeedCode.value! }),
  enabled: patternsEnabled.value,
}))
const { data: routes } = useQuery(() => ({
  ...routesQuery({ feedCode: selectedFeedCode.value! }),
  enabled: patternsEnabled.value,
}))
const patterns = computed<Pattern[]>(() => {
  const routeById = indexBy(routes.value ?? [], 'routeId')
  const grouped = groupStopPaths(stopPaths.value ?? [], stopRows.value ?? [])
  return patternsWithStopPaths(tripPatterns.value ?? [], grouped).map((p) => withRoute(p, routeById))
})
const { stopIdsForAgency } = useExplorePatterns(patterns)
const agencyStopIds = computed(() => stopIdsForAgency(agencyId.value))

const allStops = computed(() => stopRows.value ?? [])

const inAgency = computed(() => {
  const set = agencyStopIds.value
  return set ? allStops.value.filter((s) => set.has(s.stopId)) : allStops.value
})

const stats = computed(() => {
  const list = inAgency.value
  return [
    { label: t('nav.stops'), value: list.length },
    {
      label: t('explore.stops.mapped'),
      value: list.filter((s) => s.stopLat != null && s.stopLon != null).length,
    },
    { label: t('explore.stops.stations'), value: list.filter((s) => s.locationType === 1).length },
    {
      label: t('explore.stops.childPlatforms'),
      value: list.filter((s) => !!s.parentStation).length,
    },
  ]
})

const { onlyWithFindings, count: findingsCount, keep: hasFindings } = useFindingsFilter(findingCounts)
const search = ref('')
const focusId = ref<string | null>(null)

const rows = computed(() => {
  const q = search.value.trim().toLowerCase()
  const list = [...inAgency.value].sort((a, b) =>
    (a.stopName ?? a.stopId).localeCompare(b.stopName ?? b.stopId),
  )
  return list.filter(
    (s) =>
      hasFindings(s.stopId) &&
      (!q || [s.stopName, s.stopCode, s.stopId].some((v) => v?.toLowerCase().includes(q))),
  )
})

const stopFeatures = computed(
  () =>
    ({
      type: 'FeatureCollection',
      features: inAgency.value
        .filter((s) => s.stopLat != null && s.stopLon != null)
        .map((s) => ({
          type: 'Feature',
          geometry: { type: 'Point', coordinates: [s.stopLon, s.stopLat] },
          properties: { stopId: s.stopId, name: s.stopName ?? s.stopId },
        })),
    }) as GeoJSON.FeatureCollection,
)

const columns = computed<TableColumn<Stop>[]>(() => [
  { accessorKey: 'stopName', header: t('explore.col.stop') },
  { accessorKey: 'stopCode', header: t('explore.col.code') },
  { accessorKey: 'stopId', header: t('explore.col.id') },
])

function onSelect(_e: Event, row: { original: Stop }) {
  focusId.value = row.original.stopId
}

function openStop(stopId: string) {
  navigateTo(feedPath(`/explore/stops/${stopId}`))
}

const { isDesktop, splitterOrientation } = useSplitLayout()
const splitterItems = computed<SplitterItem[]>(() =>
  isDesktop.value
    ? [
        {
          id: 'directory',
          slot: 'directory',
          sizeUnit: 'px',
          defaultSize: 384,
          minSize: 320,
          maxSize: 560,
          class: 'flex min-h-0 flex-col overflow-hidden',
        },
        { id: 'map', slot: 'map', class: 'flex min-h-0 flex-col overflow-hidden' },
      ]
    : [
        {
          id: 'directory',
          slot: 'directory',
          defaultSize: 52,
          minSize: 30,
          maxSize: 70,
          class: 'flex min-h-0 flex-col overflow-hidden',
        },
        { id: 'map', slot: 'map', class: 'flex min-h-0 flex-col overflow-hidden' },
      ],
)
</script>

<template>
  <AppPage
    constrain-content
    :title="t('nav.stops')"
    :description="t('explore.stops.description')"
  >
    <template #toolbar>
      <ExploreToolbar :agencies="agencies" />
    </template>

    <div class="grid shrink-0 grid-cols-2 border-b border-default sm:grid-cols-4">
      <div
        v-for="s in stats"
        :key="s.label"
        class="border-b border-r border-default px-4 py-2.5 last:border-r-0 sm:border-b-0"
      >
        <div class="text-lg font-semibold tracking-tight text-highlighted">
          {{ formatNumber(s.value, locale) }}
        </div>
        <div class="text-xs text-muted">{{ s.label }}</div>
      </div>
    </div>

    <USplitter
      id="stops-browser-splitter"
      :orientation="splitterOrientation"
      :items="splitterItems"
      class="min-h-0 w-full flex-1"
    >
      <template #directory>
        <aside
          class="flex h-full min-h-0 w-full flex-1 flex-col gap-3 overflow-hidden bg-elevated/20 p-3"
        >
          <div class="flex shrink-0 flex-col gap-2">
            <div class="flex items-center justify-between">
              <span class="text-sm font-medium text-highlighted">{{
                t('explore.stops.directory')
              }}</span>
              <span class="text-xs text-dimmed">{{
                t('explore.stops.shown', { count: formatNumber(rows.length, locale) })
              }}</span>
            </div>
            <UInput
              v-model="search"
              icon="i-lucide-search"
              :placeholder="t('explore.stops.searchPlaceholder')"
            />
            <USwitch
              v-if="findingsCount > 0 || onlyWithFindings"
              v-model="onlyWithFindings"
              size="sm"
              :label="t('findings.filterWith', { count: findingsCount })"
            />
          </div>
          <div
            class="flex shrink-0 items-center justify-between border-y border-default py-2 text-xs text-dimmed"
          >
            <span>{{
              t(
                'explore.stops.inView',
                { count: formatNumber(inAgency.length, locale) },
                inAgency.length,
              )
            }}</span>
            <UButton
              v-if="focusId"
              size="xs"
              variant="soft"
              color="primary"
              :label="t('explore.stops.openDetail')"
              trailing-icon="i-lucide-arrow-right"
              @click="openStop(focusId)"
            />
          </div>
          <div class="min-h-0 flex-1 overflow-y-auto">
            <div v-if="loading && !rows.length" class="flex flex-col gap-2">
              <USkeleton v-for="i in 10" :key="i" class="h-9 w-full" />
            </div>
            <UTable
              v-else
              :data="rows"
              :columns="columns"
              :virtualize="{ estimateSize: 48, overscan: 10 }"
              class="h-full overflow-auto"
              @select="onSelect"
            >
              <template #stopName-cell="{ row }">
                <button
                  class="text-left transition-colors hover:text-primary"
                  :class="{ 'font-medium text-primary': row.original.stopId === focusId }"
                  @click.stop="openStop(row.original.stopId)"
                >
                  {{ row.original.stopName ?? row.original.stopId }}
                </button>
              </template>
            </UTable>
          </div>
        </aside>
      </template>

      <template #map>
        <div class="relative h-full min-w-0">
          <div
            class="pointer-events-none absolute left-4 top-4 z-1 rounded-md border border-default bg-default/90 px-3 py-2 text-xs text-muted shadow-sm backdrop-blur"
          >
            {{ t('explore.stops.mapHint') }}
          </div>
          <StopsMap
            :stops="stopFeatures"
            :focus-id="focusId"
            :extent="extent"
            @select="focusId = $event"
          />
        </div>
      </template>

      <template #resize-handle>
        <SplitterGrip :orientation="splitterOrientation" />
      </template>
    </USplitter>
  </AppPage>
</template>
