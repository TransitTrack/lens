<script setup lang="ts">
import type {TableColumn} from '@nuxt/ui'
import AppPage from '~/components/AppPage.vue'
import ExploreToolbar from '~/components/ExploreToolbar.vue'
import StopsMap from '~/components/StopsMap.vue'
import {useFeeds} from '~/composables/useFeeds'
import {useFeedExtent} from '~/composables/useFeedExtent'
import {useAgencyFilter} from '~/composables/useAgencyFilter'
import {useExplorePatterns} from '~/composables/useExplorePatterns'
import {useExploreStopsQuery, type ExploreStopsQuery} from '~~/generated/graphql'

const {selectedFeedCode, feedPath} = useFeeds()
const {extent} = useFeedExtent(selectedFeedCode)
const {agencyId} = useAgencyFilter()

const {result, loading} = useExploreStopsQuery(
  () => ({feedCode: selectedFeedCode.value ?? ''}),
  () => ({enabled: !!selectedFeedCode.value}),
)

// only needed to resolve which stops an agency serves
const {stopIdsForAgency} = useExplorePatterns(computed(() => !!agencyId.value))
const agencyStopIds = computed(() => stopIdsForAgency(agencyId.value))

type Stop = ExploreStopsQuery['stops'][number]
const allStops = computed(() => result.value?.stops ?? [])

const inAgency = computed(() => {
  const set = agencyStopIds.value
  return set ? allStops.value.filter((s) => set.has(s.stopId)) : allStops.value
})

const stats = computed(() => {
  const list = inAgency.value
  return [
    {label: 'Stops', value: list.length},
    {label: 'Mapped', value: list.filter((s) => s.stopLat != null && s.stopLon != null).length},
    {label: 'Stations', value: list.filter((s) => s.locationType === 1).length},
    {label: 'Child platforms', value: list.filter((s) => !!s.parentStation).length},
  ]
})

const search = ref('')
const focusId = ref<string | null>(null)

const rows = computed(() => {
  const q = search.value.trim().toLowerCase()
  const list = [...inAgency.value].sort((a, b) =>
    (a.stopName ?? a.stopId).localeCompare(b.stopName ?? b.stopId),
  )
  if (!q) return list
  return list.filter((s) =>
    [s.stopName, s.stopCode, s.stopId].some((v) => v?.toLowerCase().includes(q)),
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
          geometry: {type: 'Point', coordinates: [s.stopLon, s.stopLat]},
          properties: {stopId: s.stopId, name: s.stopName ?? s.stopId},
        })),
    }) as GeoJSON.FeatureCollection,
)

const columns: TableColumn<Stop>[] = [
  {accessorKey: 'stopName', header: 'Stop'},
  {accessorKey: 'stopCode', header: 'Code'},
  {accessorKey: 'stopId', header: 'ID'},
]

function onSelect(_e: Event, row: { original: Stop }) {
  focusId.value = row.original.stopId
}

function openStop(stopId: string) {
  navigateTo(feedPath(`/explore/stops/${stopId}`))
}
</script>

<template>
  <AppPage title="Stops" description="Search the network and inspect each stop in context" full-bleed>
    <template #toolbar>
      <ExploreToolbar/>
    </template>

    <div class="grid grid-cols-2 border-b border-default sm:grid-cols-4">
      <div
        v-for="s in stats"
        :key="s.label"
        class="border-b border-r border-default px-4 py-2.5 last:border-r-0 sm:border-b-0"
      >
        <div class="text-lg font-semibold tracking-tight text-highlighted">{{ s.value.toLocaleString() }}</div>
        <div class="text-xs text-muted">{{ s.label }}</div>
      </div>
    </div>

    <div class="flex min-h-0 flex-1">
      <aside class="flex w-96 shrink-0 flex-col gap-3 border-r border-default bg-elevated/20 p-3">
        <div class="flex flex-col gap-2">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-highlighted">Stop directory</span>
            <span class="text-xs text-dimmed">{{ rows.length.toLocaleString() }} shown</span>
          </div>
          <UInput
            v-model="search"
            icon="i-lucide-search"
            placeholder="Search name, code, or ID…"
          />
        </div>
        <div class="flex items-center justify-between border-y border-default py-2 text-xs text-dimmed">
          <span>{{ inAgency.length.toLocaleString() }} stops in this view</span>
          <UButton
            v-if="focusId"
            size="xs"
            variant="soft"
            color="primary"
            label="Open detail"
            trailing-icon="i-lucide-arrow-right"
            @click="openStop(focusId)"
          />
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto">
          <div v-if="loading && !rows.length" class="flex flex-col gap-2">
            <USkeleton v-for="i in 10" :key="i" class="h-9 w-full"/>
          </div>
          <UTable
            v-else
            :data="rows"
            :columns="columns"
            :virtualize="{estimateSize: 48, overscan: 10}"
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

      <div class="relative min-w-0 flex-1">
        <div class="pointer-events-none absolute left-4 top-4 z-1 rounded-md border border-default bg-default/90 px-3 py-2 text-xs text-muted shadow-sm backdrop-blur">
          Select a stop on the map or in the directory
        </div>
        <StopsMap
          :stops="stopFeatures"
          :focus-id="focusId"
          :extent="extent"
          @select="focusId = $event"
        />
      </div>
    </div>
  </AppPage>
</template>
