<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import VehicleRouteMap from '~/components/VehicleRouteMap.vue'
import SparklineChart from '~/components/SparklineChart.vue'
import VehicleFactsGrid, {type Fact} from '~/components/VehicleFactsGrid.vue'
import VehicleTelemetry from '~/components/VehicleTelemetry.vue'
import VehicleAvlLog from '~/components/VehicleAvlLog.vue'
import {useFeeds} from '~/composables/useFeeds'
import {useFeedExtent} from '~/composables/useFeedExtent'
import {usePollControl} from '~/composables/usePollControl'
import {useUnits} from '~/composables/useUnits'
import {useAdherenceHistory} from '~/composables/useAdherenceHistory'
import {useVehicleDetailQuery, useAvlTrailQuery} from '~~/generated/graphql'
import {adherenceBadge} from '~/utils/adherence'
import {
  haversineM,
  avgIntervalSec,
  blockTripProgress,
  currentStatusLabel,
} from '~/utils/vehicleTelemetry'
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
import type {GeoJSON} from 'geojson'

const route = useRoute()
const {selectedFeedCode, selectedAvlFeedCode, feedPath} = useFeeds()
const {extent} = useFeedExtent(selectedFeedCode)
const {intervalMs} = usePollControl()
const units = useUnits()
const toast = useToast()
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

// avlReports come back newest-first (backend `OrderByTsDesc`).
const reports = computed(() => trailResult.value?.avlReports ?? [])
const latestReport = computed(() => reports.value[0] ?? null)
const trail = computed(() => trailCoords(reports.value))

const follow = ref(false)
const avlLogOpen = ref(false)

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

// --- details -----------------------------------------------------------------
const blockProgress = computed(() =>
  blockTripProgress(vehicle.value?.block?.blockTrips, vehicle.value?.trip?.tripId),
)

const currentStatus = computed(() => currentStatusLabel(latestReport.value?.currentStatus))

const descMismatch = computed(() => {
  const declared = latestReport.value?.descTripId
  const matched = vehicle.value?.trip?.tripId
  return declared && matched && declared !== matched ? {declared, matched} : null
})

const gpsOffsetM = computed(() =>
  haversineM(vehicle.value?.position, vehicle.value?.snappedPosition),
)

const distToNextStopM = computed(() => {
  const cs = vehicle.value?.currentStop
  return cs ? haversineM(vehicle.value?.position, {lat: cs.stopLat, lon: cs.stopLon}) : null
})

const speedTrail = computed(() =>
  [...reports.value]
    .reverse()
    .map((r) => r.speedMps)
    .filter((s): s is number => s != null),
)

const avgInterval = computed(() => avgIntervalSec(reports.value))

const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => {
  timer = setInterval(() => (now.value = Date.now()), 15_000)
})
onBeforeUnmount(() => clearInterval(timer))

const reportAgeSec = computed(() => {
  const ts = vehicle.value?.reportTs
  const parsed = ts ? Date.parse(ts) : Number.NaN
  return Number.isFinite(parsed) ? (now.value - parsed) / 1000 : null
})

const coords = computed(() => {
  const p = vehicle.value?.position
  return p ? `${p.lat.toFixed(5)}, ${p.lon.toFixed(5)}` : null
})

async function copyCoords() {
  if (!coords.value) return
  try {
    await navigator.clipboard.writeText(coords.value)
    toast.add({title: 'Coordinates copied', color: 'success', icon: 'i-lucide-check'})
  } catch {
    toast.add({title: 'Copy failed', color: 'error'})
  }
}

const facts = computed<Fact[]>(() => {
  const v = vehicle.value
  if (!v) return []
  const dir = v.trip?.directionId
  return [
    {label: 'Vehicle ID', value: v.vehicleId},
    {label: 'Label', value: v.label ?? undefined},
    {label: 'Route', value: v.trip?.route?.routeShortName ?? v.trip?.routeId ?? undefined},
    {label: 'Direction', value: dir == null ? undefined : `Direction ${dir}`},
    {label: 'Headsign', value: v.trip?.tripHeadsign ?? undefined},
    {label: 'Block', value: v.block?.blockId ?? undefined},
    {
      label: 'Trip',
      value: blockProgress.value
        ? `${blockProgress.value.index} of ${blockProgress.value.total}`
        : undefined,
    },
    {label: 'Occupancy', value: v.occupancyStatus ?? undefined},
    {label: 'Status', value: currentStatus.value ?? undefined},
  ]
})

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
</script>

<template>
  <AppPage :title="title" full-bleed>
    <template #leading>
      <UButton
        icon="i-lucide-arrow-left"
        color="neutral"
        variant="ghost"
        :to="feedPath('/vehicles')"
        aria-label="Back to vehicles"
      />
    </template>
    <template #actions>
      <UBadge
        v-if="vehicle?.trip?.route?.routeShortName"
        :style="{
          backgroundColor: routeColor,
          color: vehicle.trip.route.routeTextColor
            ? `#${vehicle.trip.route.routeTextColor.replace('#', '')}`
            : '#fff',
        }"
      >
        {{ vehicle.trip.route.routeShortName }}
      </UBadge>
      <UTooltip :text="follow ? 'Stop following' : 'Follow vehicle'">
        <UButton
          :color="follow ? 'primary' : 'neutral'"
          :variant="follow ? 'soft' : 'ghost'"
          icon="i-lucide-locate-fixed"
          square
          aria-label="Follow vehicle"
          @click="follow = !follow"
        />
      </UTooltip>
      <UTooltip text="AVL log">
        <UButton
          color="neutral"
          variant="ghost"
          icon="i-lucide-list"
          square
          aria-label="AVL log"
          @click="avlLogOpen = true"
        />
      </UTooltip>
      <NavbarActions :updated-at="vehicle?.reportTs"/>
    </template>

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
      <div class="flex w-104 shrink-0 flex-col gap-3 border-l border-default p-4">
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
      <aside class="flex w-104 shrink-0 flex-col gap-4 overflow-y-auto shadow-2xl p-3">

        <UAlert
          v-if="descMismatch"
          color="warning"
          icon="i-lucide-git-compare-arrows"
          title="Descriptor mismatch"
          :description="`Feed reports trip ${descMismatch.declared}; matched to ${descMismatch.matched}.`"
        />

        <div class="flex flex-col gap-2">
          <div class="flex flex-1 flex-row justify-between">
            <div class="text-md font-bold text-muted">
              {{ vehicle.trip?.tripHeadsign ?? vehicle.trip?.route?.routeLongName ?? 'Unknown trip' }}
            </div>
            <div class="flex flex-wrap gap-2">
              <UBadge :color="adherenceBadge(vehicle.scheduleAdherenceSec).color" variant="subtle">
                {{ adherenceBadge(vehicle.scheduleAdherenceSec).label }}
              </UBadge>
              <UBadge v-if="vehicle.stale" color="warning" variant="subtle">Stale</UBadge>
              <span
                v-if="cardinal(vehicle.bearing)"
                class="inline-flex items-center gap-1 text-sm text-muted"
              >
                <UIcon name="i-lucide-compass" class="size-3.5"/>
                {{ cardinal(vehicle.bearing) }} · {{ Math.round(vehicle.bearing ?? 0) }}°
              </span>
            </div>
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
              :color="
                adherenceBadge(vehicle.scheduleAdherenceSec).color === 'error'
                  ? '#ef4444'
                  : adherenceBadge(vehicle.scheduleAdherenceSec).color === 'warning'
                    ? '#f59e0b'
                    : '#22c55e'
              "
            />
          </div>
        </div>

        <VehicleFactsGrid :items="facts"/>

        <!-- Position & progress -->
        <div class="flex flex-col gap-1">
          <div class="text-sm font-medium text-muted">Position &amp; progress</div>
          <dl class="grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
            <dt class="text-dimmed">Along trip</dt>
            <dd class="text-muted">{{ units.distance(vehicle.distanceAlongTripM) }}</dd>
            <dt class="text-dimmed">To next stop</dt>
            <dd class="text-muted">{{ units.distance(distToNextStopM) }}</dd>
            <dt class="text-dimmed">GPS offset</dt>
            <dd class="text-muted">
              {{
                gpsOffsetM == null
                  ? '—'
                  : gpsOffsetM <= 15
                    ? 'on route'
                    : units.distance(gpsOffsetM)
              }}
            </dd>
          </dl>
          <div v-if="vehicle.currentStop" class="text-sm">
            Next stop:
            <span class="font-medium text-highlighted">{{ vehicle.currentStop.stopName }}</span>
          </div>
          <button
            v-if="coords"
            class="inline-flex w-fit items-center gap-1 font-mono text-xs text-dimmed hover:text-muted"
            @click="copyCoords"
          >
            <UIcon name="i-lucide-copy" class="size-3"/>
            {{ coords }}
          </button>
        </div>

        <VehicleTelemetry
          :speed="units.speed(vehicle.speedMps)"
          :speed-trail="speedTrail"
          :report-age-sec="reportAgeSec"
          :avg-interval-sec="avgInterval"
          :trail-count="trail.length"
        />

        <!-- Algorithm -->
        <div class="flex flex-col gap-1">
          <USelectMenu
            v-model="algorithm"
            :items="algorithms"
            placeholder="Prediction algorithm"
          />
          <p v-if="selectedAccuracy" class="text-xs text-dimmed">
            avg error ±{{ Math.round(selectedAccuracy.meanAbsErrorSec) }}s (bias
            {{ signedSec(selectedAccuracy.meanErrorSec) }}) over
            {{ selectedAccuracy.sampleCount.toLocaleString() }} samples · 7d
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
              <NuxtLink
                :to="feedPath(`/explore/stops/${encodeURIComponent(r.stopId)}`)"
                class="block truncate text-sm font-medium text-highlighted hover:text-primary hover:underline"
              >
                {{ r.stopName }}
              </NuxtLink>
              <div class="mt-0.5 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted">
                <span>sched {{ hhmm(r.scheduledArrival) }}</span>
                <span v-if="r.eta">
                  {{ r.arrived ? 'arrived' : r.state === 'passed' ? 'was due' : 'eta' }}
                  {{ hhmm(r.eta) }}
                  <template v-if="!r.arrived && r.state !== 'passed'">
                    ({{ relative(r.eta) }})</template
                  >
                </span>
                <span v-if="r.confidenceSec != null">±{{ r.confidenceSec }}s</span>
              </div>
            </div>
            <div class="flex shrink-0 flex-col items-end gap-1">
              <UBadge
                v-if="r.deltaVsScheduleSec != null"
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

    <VehicleAvlLog
      v-if="selectedAvlFeedCode"
      v-model:open="avlLogOpen"
      :feed-code="selectedAvlFeedCode"
      :vehicle-id="vehicleId"
    />
  </AppPage>
</template>
