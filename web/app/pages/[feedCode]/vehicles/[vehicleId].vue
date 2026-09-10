<script setup lang="ts">
import NavbarActions from '~/components/NavbarActions.vue'
import VehicleRouteMap from '~/components/VehicleRouteMap.vue'
import SparklineChart from '~/components/SparklineChart.vue'
import {useFeeds} from '~/composables/useFeeds'
import {useFeedExtent} from '~/composables/useFeedExtent'
import {usePollControl} from '~/composables/usePollControl'
import {useAdherenceHistory} from '~/composables/useAdherenceHistory'
import {useVehicleDetailQuery, useAvlTrailQuery} from '~~/generated/graphql'
import {adherenceBadge} from '~/utils/adherence'
import {
  routeLine,
  stopFeatureCollection,
  algorithmsIn,
  bestAlgorithm,
  accuracyFor,
  stopRows,
  cardinal,
  trailCoords,
} from '~/utils/vehicleDetail'
import type {GeoJSON} from "geojson";

const route = useRoute()
const {selectedFeedCode, selectedAvlFeedCode, feedPath} = useFeeds()
const {extent} = useFeedExtent(selectedFeedCode)
const {intervalMs} = usePollControl()
const vehicleId = computed(() => String(route.params.vehicleId))

const {result, loading, error} = useVehicleDetailQuery(
  () => ({feedCode: selectedAvlFeedCode.value ?? '', vehicleId: vehicleId.value}),
  () => ({enabled: !!selectedAvlFeedCode.value, pollInterval: intervalMs.value}),
)

const {result: trailResult} = useAvlTrailQuery(
  () => ({feedCode: selectedAvlFeedCode.value ?? '', vehicleId: vehicleId.value, limit: 60}),
  () => ({enabled: !!selectedAvlFeedCode.value, pollInterval: intervalMs.value}),
)

const vehicle = computed(() => result.value?.vehicle ?? null)
const predictions = computed(() => result.value?.vehiclePredictions ?? [])
const accuracy = computed(() => result.value?.predictionAccuracy ?? [])
const trail = computed(() => trailCoords(trailResult.value?.avlReports ?? []))

const follow = ref(false)

const adherenceSec = computed(() => vehicle.value?.scheduleAdherenceSec ?? null)
const {points: adherenceHistory} = useAdherenceHistory(adherenceSec, vehicleId)
const sparkValues = computed(() => adherenceHistory.value.map((p) => p.sec))

const algorithms = computed(() => algorithmsIn(predictions.value))
const algorithm = ref<string | undefined>(undefined)
watch(
  [algorithms, accuracy],
  ([algos, acc]) => {
    if (!algorithm.value || !algos.includes(algorithm.value)) {
      algorithm.value = bestAlgorithm(algos, acc)
    }
  },
  {immediate: true},
)
const selectedAccuracy = computed(() => accuracyFor(algorithm.value, accuracy.value))

const routeColor = computed(() => {
  const raw = vehicle.value?.trip?.route?.routeColor
  if (!raw) return '#3b82f6'
  return raw.startsWith('#') ? raw : `#${raw}`
})

const line = computed(() => (vehicle.value ? routeLine(vehicle.value) : []))
const stops = computed(
  () =>
    (vehicle.value
      ? stopFeatureCollection(vehicle.value)
      : {type: 'FeatureCollection', features: []}) as GeoJSON.FeatureCollection,
)
const marker = computed(() => {
  const v = vehicle.value
  if (!v) return null
  const pos = v.snappedPosition ?? v.position
  return {lng: pos.lon, lat: pos.lat, bearing: v.bearing}
})

const rows = computed(() =>
  vehicle.value ? stopRows(vehicle.value, predictions.value, algorithm.value) : [],
)

const title = computed(() => {
  const v = vehicle.value
  return v ? `Vehicle ${v.label ?? v.vehicleId}` : `Vehicle ${vehicleId.value}`
})

const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => (now.value = Date.now()), 15_000)
})
onBeforeUnmount(() => clearInterval(timer))

function hhmm(iso: string | null): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(undefined, {hour: '2-digit', minute: '2-digit'}).format(
    new Date(iso),
  )
}

function relative(iso: string | null): string {
  if (!iso) return ''
  const deltaSec = Math.round((new Date(iso).getTime() - now.value) / 1000)
  if (deltaSec < -30) return `${Math.round(-deltaSec / 60)}m ago`
  if (deltaSec < 45) return 'now'
  return `in ${Math.round(deltaSec / 60)}m`
}

function signedSec(sec: number | null): string {
  if (sec === null) return '—'
  const s = Math.round(sec)
  return `${s >= 0 ? '+' : '−'}${Math.abs(s)}s`
}

function speedKmh(mps: number | null | undefined): string {
  return mps == null ? '—' : `${Math.round(mps * 3.6)} km/h`
}
</script>

<template>
  <UDashboardPanel id="vehicle-detail" :ui="{ body: 'p-0 sm:p-0 gap-0' }">
    <template #header>
      <UDashboardNavbar :title="title" :ui="{ right: 'gap-3' }">
        <template #leading>
          <UButton
            icon="i-lucide-arrow-left"
            color="neutral"
            variant="ghost"
            :to="feedPath('/vehicles')"
            aria-label="Back to vehicles"
          />
        </template>
        <template #right>
          <UBadge
            v-if="vehicle?.trip?.route?.routeShortName"
            :style="{ backgroundColor: routeColor, color: vehicle.trip.route.routeTextColor ? `#${vehicle.trip.route.routeTextColor.replace('#', '')}` : '#fff' }"
          >
            {{ vehicle.trip.route.routeShortName }}
          </UBadge>
          <UTooltip :text="follow ? 'Stop following' : 'Follow vehicle'">
            <UButton
              :color="follow ? 'primary' : 'neutral'"
              :variant="follow ? 'soft' : 'ghost'"
              size="sm"
              icon="i-lucide-locate-fixed"
              square
              aria-label="Follow vehicle"
              @click="follow = !follow"
            />
          </UTooltip>
          <NavbarActions :updated-at="vehicle?.reportTs"/>
        </template>
      </UDashboardNavbar>
    </template>

    <template #body>
      <UAlert
        v-if="error"
        color="error"
        variant="soft"
        icon="i-lucide-alert-triangle"
        title="Vehicle unavailable"
        :description="error.message"
        class="m-4"
      />
      <div v-else-if="loading && !vehicle" class="flex min-h-0 flex-1">
        <div class="flex w-[26rem] shrink-0 flex-col gap-3 border-l border-default p-4">
          <USkeleton class="h-5 w-2/3"/>
          <USkeleton class="h-6 w-full"/>
          <USkeleton class="h-9 w-full"/>
          <USkeleton v-for="i in 8" :key="i" class="h-12 w-full"/>
        </div>
        <USkeleton class="min-w-0 flex-1 rounded-none"/>
      </div>
      <UAlert
        v-else-if="!vehicle"
        color="warning"
        variant="soft"
        icon="i-lucide-search-x"
        title="Not tracked"
        :description="`No live data for ${vehicleId} on this feed.`"
        class="m-4"
      />

      <div v-else class="flex min-h-0 flex-1">
        <aside class="flex w-104 shrink-0 flex-col gap-4 overflow-y-auto border-l border-default p-4">
          <!-- Header -->
          <div class="flex flex-col gap-2">
            <div class="text-sm text-muted">
              {{ vehicle.trip?.tripHeadsign ?? vehicle.trip?.route?.routeLongName ?? 'Unknown trip' }}
              <span v-if="vehicle.trip?.directionId != null">· dir {{ vehicle.trip.directionId }}</span>
            </div>
            <div class="flex flex-wrap items-center gap-2">
              <UBadge :color="adherenceBadge(vehicle.scheduleAdherenceSec).color" variant="subtle">
                {{ adherenceBadge(vehicle.scheduleAdherenceSec).label }}
              </UBadge>
              <UBadge v-if="vehicle.stale" color="warning" variant="subtle">Stale</UBadge>
              <span class="text-sm text-muted">{{ speedKmh(vehicle.speedMps) }}</span>
              <span
                v-if="cardinal(vehicle.bearing)"
                class="inline-flex items-center gap-1 text-sm text-muted"
              >
                <UIcon name="i-lucide-compass" class="size-3.5"/>
                {{ cardinal(vehicle.bearing) }} · {{ Math.round(vehicle.bearing ?? 0) }}°
              </span>
              <UBadge v-if="vehicle.occupancyStatus" color="neutral" variant="subtle" size="sm">
                {{ vehicle.occupancyStatus }}
              </UBadge>
            </div>
            <div v-if="vehicle.currentStop" class="text-sm">
              Next stop:
              <span class="font-medium text-highlighted">{{ vehicle.currentStop.stopName }}</span>
            </div>

            <div v-if="sparkValues.length >= 2" class="flex flex-col gap-0.5">
              <div class="flex items-center justify-between text-xs text-dimmed">
                <span>Adherence trend (this session)</span>
                <span>{{ signedSec(sparkValues[sparkValues.length - 1] ?? null) }}</span>
              </div>
              <SparklineChart
                :values="sparkValues"
                zero-baseline
                :height="36"
                :color="adherenceBadge(vehicle.scheduleAdherenceSec).color === 'error'
                  ? '#ef4444'
                  : adherenceBadge(vehicle.scheduleAdherenceSec).color === 'warning'
                    ? '#f59e0b'
                    : '#22c55e'"
              />
            </div>
          </div>

          <!-- Algorithm -->
          <div class="flex flex-col gap-1">
            <USelectMenu
              v-model="algorithm"
              :items="algorithms"
              placeholder="Prediction algorithm"
              size="sm"
            />
            <p v-if="selectedAccuracy" class="text-xs text-dimmed">
              avg error ±{{ Math.round(selectedAccuracy.meanAbsErrorSec) }}s
              (bias {{ signedSec(selectedAccuracy.meanErrorSec) }})
              over {{ selectedAccuracy.sampleCount.toLocaleString() }} samples · 7d
            </p>
          </div>

          <!-- Stop list -->
          <ol class="flex flex-col">
            <li
              v-for="r in rows"
              :key="r.stopPathIndex"
              class="flex gap-3 border-l-2 py-2 pl-3"
              :class="{
                'border-primary bg-elevated/40': r.state === 'current',
                'border-default': r.state !== 'current',
                'opacity-55': r.state === 'passed',
              }"
            >
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm font-medium text-highlighted">{{ r.stopName }}</div>
                <div class="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted">
                  <span>sched {{ hhmm(r.scheduledArrival) }}</span>
                  <span v-if="r.eta">
                    {{ r.arrived ? 'arrived' : r.state === 'passed' ? 'was due' : 'eta' }}
                    {{ hhmm(r.eta) }}
                    <template v-if="!r.arrived && r.state !== 'passed'"> ({{ relative(r.eta) }})</template>
                  </span>
                  <span v-if="r.confidenceSec != null">±{{ r.confidenceSec }}s</span>
                </div>
              </div>
              <div class="flex shrink-0 flex-col items-end gap-1">
                <UBadge
                  v-if="r.deltaVsScheduleSec != null"
                  size="sm"
                  :color="adherenceBadge(r.deltaVsScheduleSec).color"
                  variant="subtle"
                >
                  {{ adherenceBadge(r.deltaVsScheduleSec).label }}
                </UBadge>
                <span
                  v-if="r.errorVsPredictionSec != null"
                  class="text-xs"
                  :class="Math.abs(r.errorVsPredictionSec) <= 30 ? 'text-success' : 'text-warning'"
                >
                  pred {{ signedSec(r.errorVsPredictionSec) }}
                </span>
              </div>
            </li>
          </ol>
        </aside>
        <div class="relative min-w-0 flex-1">
          <VehicleRouteMap
            :line="line"
            :stops="stops"
            :vehicle="marker"
            :route-color="routeColor"
            :trail="trail"
            :follow="follow"
            :extent="extent"
          />
        </div>
      </div>
    </template>
  </UDashboardPanel>
</template>
