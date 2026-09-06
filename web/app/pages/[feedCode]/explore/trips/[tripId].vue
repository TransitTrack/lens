<script setup lang="ts">
import NavbarActions from '~/components/NavbarActions.vue'
import VehicleRouteMap from '~/components/VehicleRouteMap.vue'
import {useFeeds} from '~/composables/useFeeds'
import {useTripDetailQuery} from '~~/generated/graphql'
import {hexColor, type LngLat} from '~/utils/gtfs'

const route = useRoute()
const {selectedFeedCode, feedPath} = useFeeds()
const tripId = computed(() => String(route.params.tripId))

const {result, loading, error} = useTripDetailQuery(
  () => ({feedCode: selectedFeedCode.value ?? '', tripId: tripId.value}),
  () => ({enabled: !!selectedFeedCode.value}),
)

const trip = computed(() => result.value?.trip ?? null)
const routeColor = computed(() => hexColor(trip.value?.route?.routeColor, '#3b82f6'))

const stopTimes = computed(() =>
  [...(trip.value?.stopTimes ?? [])].sort((a, b) => a.stopSequence - b.stopSequence),
)

const line = computed<LngLat[]>(() => {
  const pts = trip.value?.shape?.points ?? []
  const fromShape = pts
    .filter((p): p is { lat: number, lon: number } => p.lat != null && p.lon != null)
    .map((p) => [p.lon, p.lat] as LngLat)
  if (fromShape.length >= 2) return fromShape
  return stopTimes.value
    .filter((s) => s.stop?.stopLat != null && s.stop?.stopLon != null)
    .map((s) => [s.stop!.stopLon as number, s.stop!.stopLat as number] as LngLat)
})

const stops = computed(
  () =>
    ({
      type: 'FeatureCollection',
      features: stopTimes.value
        .filter((s) => s.stop?.stopLat != null && s.stop?.stopLon != null)
        .map((s) => ({
          type: 'Feature',
          geometry: {type: 'Point', coordinates: [s.stop!.stopLon, s.stop!.stopLat]},
          properties: {name: s.stop!.stopName ?? s.stop!.stopId, state: 'upcoming'},
        })),
    }) as GeoJSON.FeatureCollection,
)

function hm(t: string | null | undefined): string {
  return t ? t.slice(0, 5) : '—'
}

/** derived schedule times keyed by stopPathIndex (= stopSequence - 1) */
const schedByIndex = computed(() => {
  const m = new Map<number, NonNullable<typeof trip.value>['scheduleTimes'][number]>()
  for (const s of trip.value?.scheduleTimes ?? []) m.set(s.stopPathIndex, s)
  return m
})

const runtimeSec = computed(() => {
  const t = trip.value
  return t?.startTimeSec != null && t?.endTimeSec != null ? t.endTimeSec - t.startTimeSec : null
})
</script>

<template>
  <UDashboardPanel id="explore-trip-detail" :ui="{ body: 'p-0 sm:p-0 gap-0' }">
    <template #header>
      <UDashboardNavbar :title="trip?.tripHeadsign ?? tripId">
        <template #leading>
          <UButton
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="ghost"
            :to="feedPath('/explore/trips')"
            aria-label="Back to trips"
          />
        </template>
        <template #right>
          <UBadge
            v-if="trip?.route?.routeShortName"
            :style="{ backgroundColor: routeColor }"
          >
            {{ trip.route.routeShortName }}
          </UBadge>
          <NavbarActions/>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <UAlert
        v-if="error"
        color="error"
        variant="soft"
        icon="i-lucide-alert-triangle"
        title="Trip unavailable"
        :description="error.message"
        class="m-4"
      />
      <div v-else-if="loading && !trip" class="flex min-h-0 flex-1">
        <USkeleton class="min-w-0 flex-1 rounded-none"/>
        <div class="w-96 shrink-0 border-l border-default p-4">
          <USkeleton v-for="i in 12" :key="i" class="mb-2 h-8 w-full"/>
        </div>
      </div>

      <div v-else class="flex min-h-0 flex-1">
        <div class="relative min-w-0 flex-1">
          <VehicleRouteMap :line="line" :stops="stops" :vehicle="null" :route-color="routeColor"/>
        </div>

        <aside class="flex w-[26rem] shrink-0 flex-col gap-3 overflow-y-auto border-l border-default p-4">
          <div class="flex flex-col gap-1.5">
            <div class="text-xs text-muted">
              {{ trip?.tripId }} · dir {{ trip?.directionId ?? '?' }} · service {{ trip?.serviceId }}
            </div>
            <div class="flex flex-wrap items-center gap-1.5 text-xs">
              <UBadge
                v-if="trip?.pattern"
                color="neutral"
                variant="subtle"
                size="sm"
                icon="i-lucide-git-branch"
              >
                {{ trip.pattern.stopCount }} stops
                <template v-if="trip.pattern.lengthM">
                  · {{ (trip.pattern.lengthM / 1000).toFixed(1) }} km
                </template>
              </UBadge>
              <NuxtLink
                v-if="trip?.block"
                :to="feedPath(`/explore/blocks/${encodeURIComponent(trip.block.blockId)}?service=${encodeURIComponent(trip.block.serviceId)}`)"
              >
                <UBadge color="primary" variant="subtle" size="sm" icon="i-lucide-layers">
                  Block {{ trip.block.blockId }}
                </UBadge>
              </NuxtLink>
              <UBadge v-if="trip?.frequencyBased" color="warning" variant="subtle" size="sm">
                frequency-based
              </UBadge>
              <UBadge v-if="trip?.noSchedule" color="warning" variant="subtle" size="sm">
                no schedule
              </UBadge>
              <span v-if="runtimeSec != null" class="text-dimmed">
                run {{ Math.round(runtimeSec / 60) }} min
              </span>
            </div>
          </div>

          <table class="w-full text-sm">
            <thead>
            <tr class="text-left text-xs text-dimmed">
              <th class="py-1 pr-2 font-medium">#</th>
              <th class="py-1 font-medium">Stop</th>
              <th class="py-1 pl-3 text-right font-medium">Arr</th>
              <th class="py-1 pl-3 text-right font-medium">Dep</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="st in stopTimes" :key="st.stopSequence" class="border-t border-default align-top">
              <td class="py-1.5 text-xs text-dimmed">{{ st.stopSequence }}</td>
              <td class="py-1.5">
                <span class="line-clamp-1 text-highlighted">{{ st.stop?.stopName ?? '—' }}</span>
                <span
                  v-if="schedByIndex.get(st.stopSequence - 1)?.interpolated"
                  class="ml-1 rounded bg-elevated px-1 text-[10px] text-dimmed"
                >interp</span>
                <span
                  v-if="(schedByIndex.get(st.stopSequence - 1)?.schedDwellTimeSec ?? 0) > 0"
                  class="ml-1 text-[10px] text-dimmed"
                >dwell {{ schedByIndex.get(st.stopSequence - 1)!.schedDwellTimeSec }}s</span>
              </td>
              <td class="py-1.5 pl-3 text-right tabular-nums">
                <div class="text-highlighted">
                  {{
                    schedByIndex.has(st.stopSequence - 1)
                      ? secToHm(schedByIndex.get(st.stopSequence - 1)!.arrivalSec ?? null)
                      : hm(st.arrivalTime)
                  }}
                </div>
                <div v-if="schedByIndex.has(st.stopSequence - 1)" class="text-[10px] text-dimmed">
                  gtfs {{ hm(st.arrivalTime) }}
                </div>
              </td>
              <td class="py-1.5 pl-3 text-right tabular-nums text-muted">
                {{
                  schedByIndex.has(st.stopSequence - 1)
                    ? secToHm(schedByIndex.get(st.stopSequence - 1)!.departureSec ?? null)
                    : hm(st.departureTime)
                }}
              </td>
            </tr>
            </tbody>
          </table>
        </aside>
      </div>
    </template>
  </UDashboardPanel>
</template>
