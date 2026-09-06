<script setup lang="ts">
import NavbarActions from '~/components/NavbarActions.vue'
import VehicleRouteMap from '~/components/VehicleRouteMap.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useRouteDetailQuery } from '~~/generated/graphql'
import { routeTypeLabel, hexColor, patternLine, patternStopFeatures } from '~/utils/gtfs'

const route = useRoute()
const { selectedFeedCode, feedPath } = useFeeds()
const routeId = computed(() => String(route.params.routeId))

const { result, loading, error } = useRouteDetailQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '', routeId: routeId.value }),
  () => ({ enabled: !!selectedFeedCode.value }),
)

const gtfsRoute = computed(() => result.value?.route ?? null)
const patterns = computed(() => result.value?.tripPatterns ?? [])
const routeColor = computed(() => hexColor(gtfsRoute.value?.routeColor, '#3b82f6'))

const patternKey = ref<string | null>(null)
watch(patterns, (list) => {
  if (!patternKey.value || !list.some((p) => p.patternKey === patternKey.value)) {
    patternKey.value = list[0]?.patternKey ?? null
  }
}, { immediate: true })

const pattern = computed(
  () => patterns.value.find((p) => p.patternKey === patternKey.value) ?? null,
)
const patternItems = computed(() =>
  patterns.value.map((p) => ({
    label: `${p.headsign ?? p.patternKey} · dir ${p.directionId ?? '?'} · ${p.stopCount} stops · ${p.tripCount} trips`,
    value: p.patternKey,
  })),
)

const line = computed(() => (pattern.value ? patternLine(pattern.value.stopPaths) : []))
const stops = computed(
  () =>
    (pattern.value
      ? patternStopFeatures(pattern.value.stopPaths)
      : { type: 'FeatureCollection', features: [] }) as GeoJSON.FeatureCollection,
)
const stopList = computed(() =>
  [...(pattern.value?.stopPaths ?? [])].sort((a, b) => a.stopPathIndex - b.stopPathIndex),
)

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
</script>

<template>
  <UDashboardPanel id="explore-route-detail" :ui="{ body: 'p-0 sm:p-0 gap-0' }">
    <template #header>
      <UDashboardNavbar :title="gtfsRoute?.routeShortName ?? routeId">
        <template #leading>
          <UButton
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="ghost"
            :to="feedPath('/explore/routes')"
            aria-label="Back to routes"
          />
        </template>
        <template #right>
          <NavbarActions />
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <UAlert
        v-if="error"
        color="error"
        variant="soft"
        icon="i-lucide-alert-triangle"
        title="Route unavailable"
        :description="error.message"
        class="m-4"
      />
      <div v-else-if="loading && !gtfsRoute" class="flex min-h-0 flex-1">
        <USkeleton class="min-w-0 flex-1 rounded-none" />
        <div class="w-96 shrink-0 border-l border-default p-4">
          <USkeleton v-for="i in 10" :key="i" class="mb-2 h-9 w-full" />
        </div>
      </div>

      <div v-else class="flex min-h-0 flex-1">
        <div class="relative min-w-0 flex-1">
          <VehicleRouteMap :line="line" :stops="stops" :vehicle="null" :route-color="routeColor" />
        </div>

        <aside class="flex w-96 shrink-0 flex-col gap-3 overflow-y-auto border-l border-default p-4">
          <div>
            <div class="text-sm font-medium text-highlighted">{{ gtfsRoute?.routeLongName }}</div>
            <div class="text-xs text-muted">
              {{ routeTypeLabel(gtfsRoute?.routeType) }} · {{ patterns.length }} pattern(s)
            </div>
            <p v-if="gtfsRoute?.routeDesc" class="mt-1 text-xs text-dimmed">
              {{ gtfsRoute.routeDesc }}
            </p>
          </div>

          <USelectMenu
            v-model="patternKey"
            :items="patternItems"
            value-key="value"
            size="sm"
            placeholder="Pattern"
          />

          <div v-if="patternSummary" class="flex flex-wrap gap-x-3 text-xs text-dimmed">
            <span v-if="patternSummary.km">{{ patternSummary.km }} km</span>
            <span>~{{ patternSummary.runMin }} min run</span>
            <span>{{ patternSummary.timepoints }} timepoint{{ patternSummary.timepoints === 1 ? '' : 's' }}</span>
          </div>

          <ol class="flex flex-col">
            <li
              v-for="(sp, i) in stopList"
              :key="sp.stopPathIndex"
              class="flex items-center gap-2 border-l-2 py-1.5 pl-3 text-sm"
              :class="sp.scheduleAdherenceStop ? 'border-primary' : 'border-default'"
            >
              <span class="w-5 shrink-0 text-right text-xs text-dimmed">{{ i + 1 }}</span>
              <span class="truncate text-highlighted">{{ sp.stop?.stopName ?? sp.stopId }}</span>
              <span class="ml-auto flex shrink-0 items-center gap-1">
                <UIcon
                  v-if="sp.scheduleAdherenceStop"
                  name="i-lucide-timer"
                  class="size-3.5 text-primary"
                  title="Timepoint"
                />
                <UIcon
                  v-if="sp.layoverStop"
                  name="i-lucide-pause"
                  class="size-3.5 text-warning"
                  title="Layover"
                />
                <UIcon
                  v-if="sp.waitStop"
                  name="i-lucide-hourglass"
                  class="size-3.5 text-dimmed"
                  title="Wait stop"
                />
                <span
                  v-if="(sp.typicalDwellTimeSec ?? 0) > 0"
                  class="text-[10px] text-dimmed"
                >{{ sp.typicalDwellTimeSec }}s</span>
              </span>
            </li>
          </ol>
        </aside>
      </div>
    </template>
  </UDashboardPanel>
</template>
