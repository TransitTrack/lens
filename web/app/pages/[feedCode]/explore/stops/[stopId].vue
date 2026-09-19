<script setup lang="ts">
import gql from 'graphql-tag'
import { useApolloClient } from '@vue/apollo-composable'
import AppPage from '~/components/AppPage.vue'
import StopsMap from '~/components/StopsMap.vue'
import BarChart from '~/components/BarChart.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useFeedExtent } from '~/composables/useFeedExtent'
import { useExplorePatterns } from '~/composables/useExplorePatterns'
import { useStopDetailQuery, useExploreCalendarQuery } from '~~/generated/graphql'
import { hexColor, routeTextColor } from '~/utils/gtfs'
import {
  servingPatterns,
  serviceKinds,
  departureHistogram,
  scheduledDepartureGroups,
  type ScheduledDepartureInput,
  type ServiceKind,
} from '~/utils/stopSchedule'

const route = useRoute()
const { selectedFeedCode, feedPath } = useFeeds()
const { extent } = useFeedExtent(selectedFeedCode)
const { client } = useApolloClient()
const stopId = computed(() => String(route.params.stopId))

const { result: stopResult, loading: stopLoading } = useStopDetailQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '', stopId: stopId.value }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const stop = computed(() => stopResult.value?.stop ?? null)

const { patterns } = useExplorePatterns(ref(true))
const { result: calResult } = useExploreCalendarQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)

const serving = computed(() => servingPatterns(patterns.value, stopId.value))

const routes = computed(() => {
  const map = new Map<string, { name: string; color: string | null; id: string }>()
  for (const s of serving.value) {
    const r = s.pattern.route
    if (r && !map.has(s.pattern.routeId)) {
      map.set(s.pattern.routeId, {
        id: s.pattern.routeId,
        name: r.routeShortName ?? s.pattern.routeId,
        color: r.routeColor ?? null,
      })
    }
  }
  return [...map.values()]
})

const tripTotals = computed(() => {
  let total = 0,
    dir0 = 0,
    dir1 = 0
  for (const s of serving.value) {
    total += s.pattern.tripCount
    if (s.pattern.directionId === 0) dir0 += s.pattern.tripCount
    else if (s.pattern.directionId === 1) dir1 += s.pattern.tripCount
  }
  return { total, dir0, dir1 }
})

// --- departures histogram (fetched per serving pattern in one aliased query) --
const kind = ref<ServiceKind>('weekday')
const patternTrips = ref<ScheduledDepartureInput[]>([])
const tripsLoading = ref(false)
const departuresOpen = ref(false)

watch(
  [serving, selectedFeedCode],
  async ([list, fc]) => {
    patternTrips.value = []
    if (!fc || list.length === 0) return
    const keys = list.slice(0, 30).map((s) => s.pattern.patternKey)
    const body = keys
      .map(
        (k, i) =>
          `p${i}: tripPattern(feedCode: $fc, patternKey: ${JSON.stringify(k)}) { patternKey trips { startTimeSec serviceId scheduleTimes { stopPathIndex departureSec } } }`,
      )
      .join('\n')
    tripsLoading.value = true
    try {
      const { data } = await client.query<
        Record<
          string,
          {
            patternKey: string
            trips: {
              startTimeSec: number | null
              serviceId: string
              scheduleTimes: { stopPathIndex: number; departureSec: number | null }[]
            }[]
          } | null
        >
      >({
        query: gql`query StopPatternTrips($fc: String!) { ${body} }`,
        variables: { fc },
        fetchPolicy: 'cache-first',
      })
      patternTrips.value = Object.values(data ?? {}).filter((v): v is NonNullable<typeof v> => !!v)
    } finally {
      tripsLoading.value = false
    }
  },
  { immediate: true },
)

const kinds = computed(() => serviceKinds(calResult.value?.calendars ?? []))
const histogram = computed(() =>
  departureHistogram(serving.value, patternTrips.value, kinds.value, kind.value),
)
const departureGroups = computed(() =>
  scheduledDepartureGroups(serving.value, patternTrips.value, kinds.value, kind.value),
)

const kindItems = [
  { label: 'Weekday', value: 'weekday' as const },
  { label: 'Saturday', value: 'saturday' as const },
  { label: 'Sunday', value: 'sunday' as const },
]

const mapFeatures = computed(
  () =>
    ({
      type: 'FeatureCollection',
      features:
        stop.value?.stopLat != null && stop.value?.stopLon != null
          ? [
              {
                type: 'Feature',
                geometry: { type: 'Point', coordinates: [stop.value.stopLon, stop.value.stopLat] },
                properties: {
                  stopId: stop.value.stopId,
                  name: stop.value.stopName ?? stop.value.stopId,
                },
              },
            ]
          : [],
    }) as GeoJSON.FeatureCollection,
)
</script>

<template>
  <AppPage :title="stop?.stopName ?? stopId" full-bleed>
    <template #leading>
      <UButton
        icon="i-lucide-arrow-left"
        color="neutral"
        variant="ghost"
        :to="feedPath('/explore/stops')"
        aria-label="Back to stops"
      />
    </template>
    <template v-if="stop?.stopCode || stop?.platformCode" #actions>
      <UBadge color="neutral" variant="subtle" icon="i-lucide-map-pin">
        {{ stop.platformCode ? `Platform ${stop.platformCode}` : stop.stopCode }}
      </UBadge>
    </template>

    <div v-if="stopLoading && !stop" class="flex min-h-0 flex-1">
      <USkeleton class="min-w-0 flex-1 rounded-none" />
      <div class="w-104 shrink-0 border-l border-default p-4">
        <USkeleton v-for="i in 8" :key="i" class="mb-2 h-10 w-full" />
      </div>
    </div>

    <div v-else-if="stop" class="flex min-h-0 flex-1">
      <aside
        class="flex w-104 shrink-0 flex-col gap-4 overflow-y-auto border-r border-default bg-elevated/20 p-4"
      >
        <section class="flex flex-col gap-3 rounded-lg border border-default bg-default p-3">
          <div class="flex items-start justify-between gap-3">
            <div>
              <div class="text-xs font-medium uppercase tracking-wide text-dimmed">
                Stop details
              </div>
              <div class="mt-1 text-sm text-muted">{{ stop.stopId }}</div>
            </div>
            <UBadge
              v-if="stop.wheelchairBoarding === 1"
              color="success"
              variant="subtle"
              icon="i-lucide-accessibility"
            >
              Accessible
            </UBadge>
          </div>
          <dl class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
            <dt class="text-dimmed">Code</dt>
            <dd>{{ stop.stopCode ?? '—' }}</dd>
            <dt class="text-dimmed">Location</dt>
            <dd>{{ stop.stopLat?.toFixed(5) }}, {{ stop.stopLon?.toFixed(5) }}</dd>
            <template v-if="stop.stopDesc">
              <dt class="text-dimmed">About</dt>
              <dd>{{ stop.stopDesc }}</dd>
            </template>
            <template v-if="stop.zoneId">
              <dt class="text-dimmed">Zone</dt>
              <dd>{{ stop.zoneId }}</dd>
            </template>
            <template v-if="stop.parentStation">
              <dt class="text-dimmed">Station</dt>
              <dd>{{ stop.parentStation }}</dd>
            </template>
          </dl>
        </section>

        <section class="flex flex-col gap-3">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-highlighted">Service at this stop</span>
            <span class="text-xs text-dimmed">{{ tripTotals.total.toLocaleString() }} trips</span>
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div class="rounded-md border border-default bg-default px-3 py-2">
              <div class="text-lg font-semibold text-highlighted">{{ routes.length }}</div>
              <div class="text-xs text-dimmed">routes</div>
            </div>
            <div class="rounded-md border border-default bg-default px-3 py-2">
              <div class="text-lg font-semibold text-highlighted">{{ serving.length }}</div>
              <div class="text-xs text-dimmed">patterns</div>
            </div>
            <div class="col-span-2 rounded-md border border-default bg-default px-3 py-2">
              <div class="text-sm font-semibold text-highlighted">
                {{ secToHm(histogram.firstSec) }}–{{ secToHm(histogram.lastSec) }}
              </div>
              <div class="text-xs text-dimmed">typical {{ kind }} service span</div>
            </div>
          </div>
          <div v-if="routes.length" class="flex flex-wrap gap-1">
            <UBadge
              v-for="r in routes"
              :key="r.id"
              class="cursor-pointer"
              :style="{ backgroundColor: hexColor(r.color, undefined) }"
              @click="navigateTo(feedPath(`/explore/routes/${r.id}`))"
            >
              {{ r.name }}
            </UBadge>
          </div>
        </section>

        <section class="flex flex-col gap-2 rounded-lg border border-default bg-default p-3">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-highlighted">Departures by hour</span>
            <UFieldGroup>
              <UButton
                v-for="k in kindItems"
                :key="k.value"
                :color="kind === k.value ? 'primary' : 'neutral'"
                :variant="kind === k.value ? 'solid' : 'outline'"
                :label="k.label"
                @click="kind = k.value"
              />
            </UFieldGroup>
          </div>
          <USkeleton v-if="tripsLoading && !patternTrips.length" class="h-32.5 w-full" />
          <template v-else>
            <BarChart :bars="histogram.bars" :height="130" :format="(n) => `${n}`" />
            <p class="text-xs text-dimmed">
              {{ histogram.total.toLocaleString() }} scheduled departures on a typical {{ kind }} ·
              estimated from typical segment times
            </p>
            <UButton
              label="View scheduled departures"
              icon="i-lucide-list-clock"
              color="neutral"
              variant="outline"
              block
              @click="departuresOpen = true"
            />
          </template>
        </section>
      </aside>
      <div class="relative min-w-0 flex-1">
        <div
          class="pointer-events-none absolute left-4 top-4 z-1 rounded-md border border-default bg-default/90 px-3 py-2 text-xs text-muted shadow-sm backdrop-blur"
        >
          {{ stop.stopName ?? stop.stopId }}
        </div>
        <StopsMap :stops="mapFeatures" :focus-id="stop.stopId" :extent="extent" />
      </div>

      <UModal
        v-model:open="departuresOpen"
        :title="`Scheduled departures from ${stop.stopName ?? stop.stopId}`"
        :description="`Typical ${kind} service`"
        :ui="{ content: 'sm:max-w-5xl' }"
        scrollable
      >
        <template #body>
          <div v-if="tripsLoading && !patternTrips.length" class="space-y-3">
            <USkeleton v-for="i in 6" :key="i" class="h-12 w-full" />
          </div>
          <div v-else-if="departureGroups.length" class="space-y-4">
            <section v-for="group in departureGroups" :key="group.hour">
              <h3 class="mb-1 text-xs font-medium uppercase tracking-wide text-dimmed">
                {{ String(group.hour).padStart(2, '0') }}:00–{{
                  String(group.hour).padStart(2, '0')
                }}:59
              </h3>
              <table class="w-full text-sm">
                <tbody>
                  <tr
                    v-for="departure in group.departures"
                    :key="`${departure.routeLabel}:${departure.headsign}:${departure.departureSec}`"
                    class="border-b border-default last:border-b-0"
                  >
                    <td class="w-16 py-2 pr-3">
                      <UBadge
                        color="neutral"
                        variant="subtle"
                        :style="{
                          backgroundColor: hexColor(departure.routeColor, undefined),
                          color: routeTextColor(
                            hexColor(departure.routeColor),
                            departure.routeTextColor,
                          ),
                        }"
                        >{{ departure.routeLabel }}</UBadge
                      >
                    </td>
                    <td class="py-2 text-highlighted">{{ departure.headsign ?? '—' }}</td>
                    <td class="py-2 text-right font-medium tabular-nums text-highlighted">
                      {{ secToHm(departure.departureSec) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </section>
          </div>
          <p v-else class="py-8 text-center text-sm text-dimmed">
            No scheduled departures for this service day.
          </p>
        </template>
      </UModal>
    </div>
  </AppPage>
</template>
