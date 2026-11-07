<script setup lang="ts">
import { useRouteDetailQuery } from '~/composables/useRestDetails'
import SplitterGrip from '~/components/shell/SplitterGrip.vue'
import type { SplitterItem } from '@nuxt/ui'
import AppPage from '~/components/shell/AppPage.vue'
import ValidationFindingsPanel from '~/components/explore/ValidationFindingsPanel.vue'
import VehicleRouteMap from '~/components/map/VehicleRouteMap.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useFeedExtent } from '~/composables/useFeedExtent'

import { routeTypeLabel, geometryLine, patternStopFeatures } from '~/utils/gtfs'
import { useSplitLayout } from '~/composables/useSplitLayout'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { selectedFeedCode, feedPath } = useFeeds()
const { extent } = useFeedExtent(selectedFeedCode)
const routeId = computed(() => String(route.params.routeId))

const { result, loading, error } = useRouteDetailQuery(() => ({
  variables: { feedCode: selectedFeedCode.value ?? '', routeId: routeId.value },
  enabled: !!selectedFeedCode.value,
}))

const gtfsRoute = computed(() => result.value?.route ?? null)
const patterns = computed(() => result.value?.tripPatterns ?? [])

const patternKey = ref<string | undefined>()
watch(
  patterns,
  (list) => {
    if (!patternKey.value || !list.some((p) => p.patternKey === patternKey.value)) {
      patternKey.value = list[0]?.patternKey ?? undefined
    }
  },
  { immediate: true },
)

const pattern = computed(
  () => patterns.value.find((p) => p.patternKey === patternKey.value) ?? null,
)
const patternItems = computed(() =>
  patterns.value.map((p) => ({
    label: t('explore.routes.patternLabel', {
      headsign: p.headsign ?? p.patternKey,
      dir: p.directionId ?? '?',
      stops: t('feed.overview.stops', { count: p.stopCount }, p.stopCount),
      trips: t('feed.overview.trips', { count: p.tripCount }, p.tripCount),
    }),
    value: p.patternKey,
  })),
)

const line = computed(() => (pattern.value ? geometryLine(pattern.value.geometry) : []))
const stops = computed(
  () =>
    (pattern.value
      ? patternStopFeatures(pattern.value.stopPaths)
      : { type: 'FeatureCollection', features: [] }) as GeoJSON.FeatureCollection,
)
const stopList = computed(() =>
  [...(pattern.value?.stopPaths ?? [])].sort((a, b) => a.stopPathIndex - b.stopPathIndex),
)
const selectedStopId = ref<string | null>(null)

watch(patternKey, () => {
  selectedStopId.value = null
})

const tripsLink = computed(() => {
  const dir = pattern.value?.directionId
  const q = dir == null ? '' : `&dir=${dir}`
  return feedPath(`/explore/trips?route=${encodeURIComponent(routeId.value)}${q}`)
})

const patternSummary = computed(() => {
  const p = pattern.value
  if (!p) return null
  let runSec = 0
  let timepoints = 0
  for (const sp of p.stopPaths) {
    runSec += (sp.typicalTravelTimeSec ?? 0) + (sp.typicalDwellTimeSec ?? 0)
    if (sp.scheduleAdherenceStop) timepoints++
  }
  return {
    km: p.lengthM ? (p.lengthM / 1000).toFixed(1) : null,
    runMin: Math.round(runSec / 60),
    timepoints,
  }
})

const { isDesktop, splitterOrientation } = useSplitLayout()
const splitterItems = computed<SplitterItem[]>(() =>
  isDesktop.value
    ? [
        {
          id: 'details',
          slot: 'details',
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
          id: 'details',
          slot: 'details',
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
  <AppPage constrain-content :title="gtfsRoute?.routeShortName ?? routeId">
    <template #leading>
      <UButton
        icon="i-lucide-arrow-left"
        color="neutral"
        variant="ghost"
        :aria-label="t('common.goBack')"
        @click="router.back()"
      />
    </template>
    <template #actions>
      <ValidationFindingsPanel
        :feed-code="selectedFeedCode"
        entity-type="Route"
        :entity-id="routeId"
      />
    </template>

    <UAlert
      v-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      :title="t('explore.routes.unavailable')"
      :description="error.message"
      class="m-4"
    />
    <div v-else-if="loading && !gtfsRoute" class="flex min-h-0 flex-1">
      <div class="w-96 shrink-0 p-4">
        <USkeleton v-for="i in 10" :key="i" class="mb-2 h-9 w-full" />
      </div>
      <USkeleton class="min-w-0 flex-1 rounded-none" />
    </div>

    <USplitter
      v-else
      id="route-detail-splitter"
      :orientation="splitterOrientation"
      :items="splitterItems"
      class="h-full min-h-0 w-full"
    >
      <template #details>
        <aside class="flex h-full w-full flex-col gap-3 overflow-y-auto p-4">
          <div>
            <div class="text-sm font-medium text-highlighted">{{ gtfsRoute?.routeLongName }}</div>
            <div class="text-xs text-muted">
              {{ routeTypeLabel(gtfsRoute?.routeType, t) }} ·
              {{ t('explore.count.patterns', { count: patterns.length }, patterns.length) }}
            </div>
          </div>

          <USelectMenu
            v-model="patternKey"
            :items="patternItems"
            value-key="value"
            :placeholder="t('explore.routes.pattern')"
          />

          <section
            v-if="patternSummary && pattern"
            class="grid grid-cols-2 overflow-hidden rounded-md border border-default bg-default"
          >
            <div class="border-b border-r border-default p-3">
              <div class="text-[11px] font-medium uppercase tracking-wide text-dimmed">
                {{ t('explore.routes.patterns') }}
              </div>
              <div class="mt-1 text-xl font-semibold tabular-nums text-highlighted">
                {{ patterns.length }}
              </div>
            </div>
            <div class="border-b border-default p-3">
              <div class="text-[11px] font-medium uppercase tracking-wide text-dimmed">
                {{ t('nav.trips') }}
              </div>
              <div class="mt-1 text-xl font-semibold tabular-nums text-highlighted">
                {{ pattern.tripCount }}
              </div>
            </div>
            <div class="border-r border-default p-3">
              <div class="text-[11px] font-medium uppercase tracking-wide text-dimmed">
                {{ t('nav.stops') }}
              </div>
              <div class="mt-1 text-xl font-semibold tabular-nums text-highlighted">
                {{ pattern.stopCount }}
              </div>
            </div>
            <div class="p-3">
              <div class="text-[11px] font-medium uppercase tracking-wide text-dimmed">
                {{ t('explore.routes.typicalRun') }}
              </div>
              <div class="mt-1 text-xl font-semibold tabular-nums text-highlighted">
                {{ t('explore.minutesShort', { n: patternSummary.runMin }) }}
              </div>
            </div>
          </section>

          <UButton
            :to="tripsLink"
            color="neutral"
            variant="soft"
            icon="i-lucide-git-branch"
            trailing-icon="i-lucide-arrow-right"
            :label="t('explore.routes.viewTrips')"
            block
          />

          <div v-if="patternSummary" class="flex flex-wrap gap-x-3 text-xs text-dimmed">
            <span v-if="patternSummary.km">{{ patternSummary.km }} km</span>
            <span>{{
              t(
                'explore.count.timepoints',
                { count: patternSummary.timepoints },
                patternSummary.timepoints,
              )
            }}</span>
          </div>

          <div class="flex items-center justify-between pt-1">
            <span class="text-sm font-medium text-highlighted">{{
              t('explore.routes.stopSequence')
            }}</span>
            <span class="text-xs text-dimmed">{{
              t('feed.overview.stops', { count: stopList.length }, stopList.length)
            }}</span>
          </div>

          <ol class="rounded-md border border-default bg-default px-2 py-2">
            <li v-for="(sp, i) in stopList" :key="sp.stopPathIndex" class="relative pl-8">
              <span
                class="absolute bottom-0 left-3 top-0 w-px bg-default"
                :class="i === 0 ? 'top-1/2' : i === stopList.length - 1 ? 'bottom-1/2' : ''"
                aria-hidden="true"
              />
              <button
                type="button"
                class="group relative flex w-full items-center gap-2 rounded-md px-2 py-2 text-left text-sm outline-none transition-colors hover:bg-elevated focus-visible:ring-2 focus-visible:ring-primary"
                :class="
                  selectedStopId === sp.stopId
                    ? 'bg-primary/10 text-highlighted'
                    : 'text-highlighted'
                "
                :aria-pressed="selectedStopId === sp.stopId"
                @click="selectedStopId = sp.stopId"
              >
                <span
                  class="absolute -left-7 flex size-5 items-center justify-center rounded-full border-2 border-default bg-default text-[10px] font-semibold tabular-nums text-dimmed transition-colors group-hover:border-primary"
                  :class="
                    selectedStopId === sp.stopId ? 'border-primary bg-primary text-inverted' : ''
                  "
                  >{{ i + 1 }}</span
                >
                <span class="min-w-0 flex-1 truncate font-medium">{{
                  sp.stop?.stopName ?? sp.stopId
                }}</span>
                <span class="flex shrink-0 items-center gap-1">
                  <UIcon
                    v-if="sp.scheduleAdherenceStop"
                    name="i-lucide-timer"
                    class="size-3.5 text-primary"
                    :title="t('explore.routes.timepoint')"
                  />
                  <UIcon
                    v-if="sp.layoverStop"
                    name="i-lucide-pause"
                    class="size-3.5 text-warning"
                    :title="t('explore.routes.layover')"
                  />
                  <UIcon
                    v-if="sp.waitStop"
                    name="i-lucide-hourglass"
                    class="size-3.5 text-dimmed"
                    :title="t('explore.routes.waitStop')"
                  />
                  <span v-if="(sp.typicalDwellTimeSec ?? 0) > 0" class="text-[10px] text-dimmed"
                    >{{ sp.typicalDwellTimeSec }}s</span
                  >
                </span>
              </button>
            </li>
          </ol>
        </aside>
      </template>
      <template #map>
        <div class="relative h-full min-w-0">
          <VehicleRouteMap
            route-color="#ff0000"
            :line="line"
            :stops="stops"
            :vehicle="null"
            :extent="extent"
            :focus-id="selectedStopId"
          />
        </div>
      </template>

      <template #resize-handle>
        <SplitterGrip :orientation="splitterOrientation" />
      </template>
    </USplitter>
  </AppPage>
</template>
