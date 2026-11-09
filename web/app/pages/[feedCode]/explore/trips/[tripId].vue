<script setup lang="ts">
import { useQuery } from '@pinia/colada'
import { validationFindingsQuery, tripQuery, routeQuery, stopTimesQuery, shapeQuery, stopsByIdsQuery, agenciesQuery } from '~/composables/api/queries/gtfs'
import { tripPatternQuery, blockQuery, scheduleTimesQuery } from '~/composables/api/queries/schedule'
import { agenciesExtent } from '~/utils/joins/extent'
import { indexBy } from '~/utils/joins/routes'
import SplitterGrip from '~/components/shell/SplitterGrip.vue'
import type { SplitterItem } from '@nuxt/ui'
import AppPage from '~/components/shell/AppPage.vue'
import ValidationFindingsPanel from '~/components/explore/ValidationFindingsPanel.vue'
import VehicleRouteMap from '~/components/map/VehicleRouteMap.vue'
import { useFeeds } from '~/composables/useFeeds'

import type { LngLat } from '~/utils/gtfs'
import { useSplitLayout } from '~/composables/useSplitLayout'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { selectedFeedCode, feedPath } = useFeeds()
const tripId = computed(() => String(route.params.tripId))

const { data: findings, isLoading: findingsLoading } = useQuery(() => ({
  ...validationFindingsQuery({ feedCode: selectedFeedCode.value!, entityType: 'Trip', entityId: tripId.value }),
  enabled: !!selectedFeedCode.value && !!tripId.value,
}))

const enabled = computed(() => !!selectedFeedCode.value)
const { data: agencies } = useQuery(() => ({
  ...agenciesQuery({ feedCode: selectedFeedCode.value! }),
  enabled: enabled.value,
}))
const extent = computed(() => agenciesExtent(agencies.value ?? []))

const { data: tripData, isLoading: tripLoading, error: tripError } = useQuery(() => ({
  ...tripQuery({ feedCode: selectedFeedCode.value!, tripId: tripId.value }),
  enabled: enabled.value,
}))

// Everything below hangs off the trip: a 404 (null) leaves these disabled.
const hasTrip = computed(() => enabled.value && !!tripData.value)
const { data: tripRoute } = useQuery(() => ({
  ...routeQuery({ feedCode: selectedFeedCode.value!, routeId: tripData.value?.routeId ?? '' }),
  enabled: hasTrip.value,
}))
const { data: pattern } = useQuery(() => ({
  ...tripPatternQuery({ feedCode: selectedFeedCode.value!, patternKey: tripData.value?.patternKey ?? '' }),
  enabled: hasTrip.value && !!tripData.value?.patternKey,
}))
const { data: block } = useQuery(() => ({
  ...blockQuery({ feedCode: selectedFeedCode.value!, blockId: tripData.value?.blockId ?? '', serviceId: tripData.value?.serviceId ?? '' }),
  enabled: hasTrip.value && !!tripData.value?.blockId,
}))
const { data: scheduleTimes } = useQuery(() => ({
  ...scheduleTimesQuery({ feedCode: selectedFeedCode.value!, tripId: tripData.value?.tripId ?? '' }),
  enabled: hasTrip.value,
}))
const { data: rawStopTimes, isLoading: stopTimesLoading, error: stopTimesError } = useQuery(() => ({
  ...stopTimesQuery({ feedCode: selectedFeedCode.value!, tripId: tripData.value?.tripId ?? '' }),
  enabled: hasTrip.value,
}))
const { data: shape } = useQuery(() => ({
  ...shapeQuery({ feedCode: selectedFeedCode.value!, shapeId: tripData.value?.shapeId ?? '' }),
  enabled: hasTrip.value && !!tripData.value?.shapeId,
}))
const stopIds = computed(() => (rawStopTimes.value ?? []).map((st) => st.stopId).filter((id): id is string => !!id))
const { data: stopsData } = useQuery(() => ({
  ...stopsByIdsQuery({ feedCode: selectedFeedCode.value!, stopIds: stopIds.value }),
  enabled: hasTrip.value && stopIds.value.length > 0,
}))

const loading = computed(() => tripLoading.value || stopTimesLoading.value)
const error = computed(() => tripError.value ?? stopTimesError.value)

const trip = computed(() => {
  if (!tripData.value) return null
  const stopById = indexBy(stopsData.value ?? [], 'stopId')
  return {
    ...tripData.value,
    route: tripRoute.value ?? null,
    pattern: pattern.value ?? null,
    block: block.value ?? null,
    scheduleTimes: scheduleTimes.value ?? [],
    stopTimes: (rawStopTimes.value ?? []).map((st) => ({ ...st, stop: st.stopId ? stopById.get(st.stopId) ?? null : null })),
    shape: shape.value ?? null,
  }
})

const stopTimes = computed(() =>
  [...(trip.value?.stopTimes ?? [])].sort((a, b) => a.stopSequence - b.stopSequence),
)
const selectedStopId = ref<string | null>(null)

watch(tripId, () => {
  selectedStopId.value = null
})

const line = computed<LngLat[]>(() => {
  const pts = trip.value?.shape?.points ?? []
  const fromShape = pts
    .filter((p) => p.lat != null && p.lon != null)
    .map((p) => [p.lon, p.lat] as LngLat)
  if (fromShape.length >= 2) return fromShape
  return stopTimes.value
    .filter((s) => s.stop?.stopLat != null && s.stop?.stopLon != null)
    .map((s) => [s.stop!.stopLon as number, s.stop!.stopLat as number] as LngLat)
})

/** derived schedule times keyed by stopPathIndex (= stopSequence - 1) */
const schedByIndex = computed(() => {
  const m = new Map<number, NonNullable<typeof trip.value>['scheduleTimes'][number]>()
  for (const s of trip.value?.scheduleTimes ?? []) m.set(s.stopPathIndex, s)
  return m
})

const stops = computed(
  () =>
    ({
      type: 'FeatureCollection',
      features: stopTimes.value
        .filter((s) => s.stop?.stopLat != null && s.stop?.stopLon != null)
        .map((s) => {
          const schedule = schedByIndex.value.get(s.stopSequence - 1)
          const arrival = schedule ? secToHm(schedule.arrivalSec ?? null) : hm(s.arrivalTime)
          const departure = schedule ? secToHm(schedule.departureSec ?? null) : hm(s.departureTime)
          const times = [arrival, departure].filter((time) => time !== '—')
          const timeLabel =
            times.length === 2 && times[0] !== times[1] ? `${times[0]}–${times[1]}` : times[0]
          const name = s.stop!.stopName ?? s.stop!.stopId
          return {
            type: 'Feature',
            geometry: { type: 'Point', coordinates: [s.stop!.stopLon, s.stop!.stopLat] },
            properties: {
              name,
              label: timeLabel ? `${name}\n${timeLabel}` : name,
              stopId: s.stop!.stopId,
              state: 'upcoming',
            },
          }
        }),
    }) as GeoJSON.FeatureCollection,
)

function hm(time: string | null | undefined): string {
  return time ? time.slice(0, 5) : '—'
}

const runtimeSec = computed(() => {
  const tr = trip.value
  return tr?.startTimeSec != null && tr?.endTimeSec != null ? tr.endTimeSec - tr.startTimeSec : null
})

const tripStats = computed(() => ({
  stops: stopTimes.value.length,
  runtime:
    runtimeSec.value != null
      ? t('explore.minutesShort', { n: Math.round(runtimeSec.value / 60) })
      : '—',
  length: trip.value?.pattern?.lengthM
    ? `${(trip.value.pattern.lengthM / 1000).toFixed(1)} km`
    : '—',
  start: trip.value?.startTimeSec != null ? secToHm(trip.value.startTimeSec) : '—',
}))

const { isDesktop, splitterOrientation } = useSplitLayout()
const splitterItems = computed<SplitterItem[]>(() =>
  isDesktop.value
    ? [
        {
          id: 'details',
          slot: 'details',
          sizeUnit: 'px',
          defaultSize: 416,
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
  <AppPage :title="trip?.tripHeadsign ?? tripId" constrain-content>
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
      <ValidationFindingsPanel :findings="findings" :loading="findingsLoading" />
      <UBadge v-if="trip?.route?.routeShortName" :style="{ backgroundColor: '#ff0000' }">
        {{ trip.route.routeShortName }}
      </UBadge>
    </template>

    <UAlert
      v-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      :title="t('explore.trip.unavailable')"
      :description="error.message"
      class="m-4"
    />
    <div v-else-if="loading && !trip" class="flex min-h-0 flex-1">
      <div class="w-96 shrink-0 border-l border-default p-4">
        <USkeleton v-for="i in 12" :key="i" class="mb-2 h-8 w-full" />
      </div>
      <USkeleton class="min-w-0 flex-1 rounded-none" />
    </div>

    <USplitter
      v-else
      id="trip-detail-splitter"
      :orientation="splitterOrientation"
      :items="splitterItems"
      class="h-full min-h-0 w-full"
    >
      <template #details>
        <aside
          class="flex h-full min-h-0 w-full flex-1 flex-col gap-3 overflow-y-auto overscroll-contain p-4"
        >
          <section class="app-frame shrink-0">
            <header class="border-b border-default px-3 py-2.5">
              <div class="text-xs font-semibold uppercase tracking-wide text-dimmed">
                {{ t('explore.trip.profile') }}
              </div>
              <div class="mt-1 text-xs text-muted">
                {{
                  t('explore.trip.meta', {
                    id: trip?.tripId,
                    dir: trip?.directionId ?? '?',
                    service: trip?.serviceId,
                  })
                }}
              </div>
            </header>
            <div class="flex flex-wrap items-center gap-1.5 p-3 text-xs">
              <UBadge
                v-if="trip?.pattern"
                color="neutral"
                variant="subtle"
                icon="i-lucide-git-branch"
              >
                {{
                  trip.pattern.lengthM
                    ? t('explore.trip.stopsLength', {
                        stops: t('feed.overview.stops', { count: trip.pattern.stopCount }, trip.pattern.stopCount),
                        km: (trip.pattern.lengthM / 1000).toFixed(1),
                      })
                    : t('feed.overview.stops', { count: trip.pattern.stopCount }, trip.pattern.stopCount)
                }}
              </UBadge>
              <NuxtLink
                v-if="trip?.block"
                :to="
                  feedPath(
                    `/explore/blocks/${encodeURIComponent(trip.block.blockId)}?service=${encodeURIComponent(trip.block.serviceId)}`,
                  )
                "
              >
                <UBadge color="primary" variant="subtle" icon="i-lucide-layers">
                  {{ t('explore.trip.block', { id: trip.block.blockId }) }}
                </UBadge>
              </NuxtLink>
              <UBadge v-if="trip?.frequencyBased" color="warning" variant="subtle">
                {{ t('explore.trip.frequencyBased') }}
              </UBadge>
              <UBadge v-if="trip?.noSchedule" color="warning" variant="subtle">
                {{ t('explore.trip.noSchedule') }}
              </UBadge>
              <span v-if="runtimeSec != null" class="text-dimmed">
                {{ t('explore.trip.run', { minutes: Math.round(runtimeSec / 60) }) }}
              </span>
            </div>
          </section>

          <section class="app-frame shrink-0 grid grid-cols-2">
            <div class="border-b border-r border-default p-2">
              <div class="text-[10px] font-medium uppercase tracking-wide text-dimmed">
                {{ t('nav.stops') }}
              </div>
              <div class="mt-1 text-lg font-semibold tabular-nums text-highlighted">
                {{ tripStats.stops }}
              </div>
            </div>
            <div class="border-b border-default p-2">
              <div class="text-[10px] font-medium uppercase tracking-wide text-dimmed">
                {{ t('explore.trip.runtime') }}
              </div>
              <div class="mt-1 text-lg font-semibold tabular-nums text-highlighted">
                {{ tripStats.runtime }}
              </div>
            </div>
            <div class="border-r border-default p-2">
              <div class="text-[10px] font-medium uppercase tracking-wide text-dimmed">
                {{ t('explore.trip.length') }}
              </div>
              <div class="mt-1 text-lg font-semibold tabular-nums text-highlighted">
                {{ tripStats.length }}
              </div>
            </div>
            <div class="p-2">
              <div class="text-[10px] font-medium uppercase tracking-wide text-dimmed">
                {{ t('explore.trip.starts') }}
              </div>
              <div class="mt-1 text-lg font-semibold tabular-nums text-highlighted">
                {{ tripStats.start }}
              </div>
            </div>
          </section>

          <section class="app-frame shrink-0">
            <div class="flex items-center justify-between border-b border-default px-3 py-2.5">
              <span class="text-sm font-medium text-highlighted">{{
                t('explore.trip.stopTimetable')
              }}</span>
              <span class="text-xs text-dimmed">{{ t('explore.trip.scheduledTimes') }}</span>
            </div>
            <table class="w-full text-sm">
              <thead>
                <tr class="text-left text-xs text-dimmed">
                  <th class="py-1 pl-2 pr-2 font-medium">
                    {{ t('explore.col.stop') }}
                  </th>
                  <th class="py-1 text-right font-medium">{{ t('explore.trip.arr') }}</th>
                  <th class="py-1 pr-2 text-right font-medium">{{ t('explore.trip.dep') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="st in stopTimes"
                  :key="st.stopSequence"
                  class="border-t border-default align-top"
                  :class="st.stop?.stopId === selectedStopId ? 'bg-primary/10' : ''"
                >
                  <td class="py-1 pl-2">
                    <button
                      v-if="st.stop?.stopId"
                      type="button"
                      class="line-clamp-1 text-left text-highlighted hover:text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                      :aria-pressed="st.stop.stopId === selectedStopId"
                      @click="selectedStopId = st.stop.stopId"
                    >
                      {{ st.stop.stopName ?? st.stop.stopId }}
                    </button>
                    <span v-else class="line-clamp-1 text-highlighted">—</span>
                    <span
                      v-if="schedByIndex.get(st.stopSequence - 1)?.interpolated"
                      class="ml-1 rounded-sm bg-elevated px-1 text-[10px] text-dimmed"
                      >{{ t('explore.trip.interp') }}</span
                    >
                    <span
                      v-if="(schedByIndex.get(st.stopSequence - 1)?.schedDwellTimeSec ?? 0) > 0"
                      class="ml-1 text-[10px] text-dimmed"
                      >{{
                        t('explore.trip.dwell', {
                          seconds: schedByIndex.get(st.stopSequence - 1)!.schedDwellTimeSec,
                        })
                      }}</span
                    >
                  </td>
                  <td class="py-1 text-right tabular-nums">
                    <div class="text-highlighted">
                      {{
                        schedByIndex.has(st.stopSequence - 1)
                          ? secToHm(schedByIndex.get(st.stopSequence - 1)!.arrivalSec ?? null)
                          : hm(st.arrivalTime)
                      }}
                    </div>
                    <div
                      v-if="schedByIndex.has(st.stopSequence - 1)"
                      class="text-[10px] text-dimmed"
                    >
                      {{ t('explore.trip.gtfsTime', { time: hm(st.arrivalTime) }) }}
                    </div>
                  </td>
                  <td class="py-1 pr-2 text-right tabular-nums text-muted">
                    {{
                      schedByIndex.has(st.stopSequence - 1)
                        ? secToHm(schedByIndex.get(st.stopSequence - 1)!.departureSec ?? null)
                        : hm(st.departureTime)
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
          </section>
        </aside>
      </template>
      <template #map>
        <div class="relative h-full min-w-0">
          <VehicleRouteMap
            :line="line"
            :stops="stops"
            :vehicle="null"
            route-color="#ff0000"
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
