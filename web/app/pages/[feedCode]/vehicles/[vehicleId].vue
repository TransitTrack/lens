<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import VehicleRouteMap from '~/components/VehicleRouteMap.vue'
import SparklineChart from '~/components/SparklineChart.vue'
import VehicleFactsGrid, { type Fact } from '~/components/VehicleFactsGrid.vue'
import VehicleTelemetry from '~/components/VehicleTelemetry.vue'
import VehicleAvlLog from '~/components/VehicleAvlLog.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useFeedExtent } from '~/composables/useFeedExtent'
import { usePollControl } from '~/composables/usePollControl'
import { useUnits } from '~/composables/useUnits'
import { useAdherenceHistory } from '~/composables/useAdherenceHistory'
import {
  useVehicleDetailQuery,
  useAvlTrailQuery,
  useAgenciesDetailQuery,
} from '~~/generated/graphql'
import { adherenceBadge } from '~/utils/adherence'
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
import type { GeoJSON } from 'geojson'

const route = useRoute()
const { selectedFeedCode, selectedAvlFeedCode, feedPath } = useFeeds()
const { extent } = useFeedExtent(selectedFeedCode)
const { intervalMs } = usePollControl()
const units = useUnits()
const toast = useToast()
const vehicleId = computed(() => String(route.params.vehicleId))

const { result, loading, error } = useVehicleDetailQuery(
  () => ({ feedCode: selectedAvlFeedCode.value ?? '', vehicleId: vehicleId.value }),
  () => ({ enabled: !!selectedAvlFeedCode.value, pollInterval: intervalMs.value }),
)

const { result: trailResult } = useAvlTrailQuery(
  () => ({ feedCode: selectedAvlFeedCode.value ?? '', vehicleId: vehicleId.value, limit: 60 }),
  () => ({ enabled: !!selectedAvlFeedCode.value, pollInterval: intervalMs.value }),
)
const { result: agenciesResult } = useAgenciesDetailQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const agencyTimezone = computed(
  () => agenciesResult.value?.agencies.find((agency) => agency.agencyTimezone)?.agencyTimezone,
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
const mapExpanded = ref(false)

const adherenceSec = computed(() => vehicle.value?.scheduleAdherenceSec ?? null)
const { points: adherenceHistory } = useAdherenceHistory(adherenceSec, vehicleId)
const sparkValues = computed(() => adherenceHistory.value.map((p) => p.sec))

const algorithms = computed(() => algorithmsIn(predictions.value))
const algorithm = ref<string | undefined>(undefined)
function algorithmLabel(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word[0]!.toUpperCase() + word.slice(1))
    .join(' ')
}
const algorithmOptions = computed(() =>
  algorithms.value.map((value) => ({label: algorithmLabel(value), value})),
)
watch(
  [algorithms, accuracy],
  ([algos, acc]) => {
    if (!algorithm.value || !algos.includes(algorithm.value)) {
      algorithm.value = bestAlgorithm(algos, acc)
    }
  },
  { immediate: true },
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
      : { type: 'FeatureCollection', features: [] }) as GeoJSON.FeatureCollection,
)
const marker = computed(() => {
  const v = vehicle.value
  if (!v) return null
  const pos = v.snappedPosition ?? v.position
  return { lng: pos.lon, lat: pos.lat, bearing: v.bearing }
})

const rows = computed(() =>
  vehicle.value ? stopRows(vehicle.value, predictions.value, algorithm.value) : [],
)

const title = computed(() => {
  const v = vehicle.value
  return v ? `Vehicle ${v.label ?? v.vehicleId}` : `Vehicle ${vehicleId.value}`
})

const description = computed(() => {
  const v = vehicle.value
  return v ? `#${v.vehicleId ?? v.label}` : `#${vehicleId.value}`
})


// --- details -----------------------------------------------------------------
const blockProgress = computed(() =>
  blockTripProgress(vehicle.value?.block?.blockTrips, vehicle.value?.trip?.tripId),
)

const currentStatus = computed(() => currentStatusLabel(latestReport.value?.currentStatus))

const descMismatch = computed(() => {
  const declared = latestReport.value?.descTripId
  const matched = vehicle.value?.trip?.tripId
  return declared && matched && declared !== matched ? { declared, matched } : null
})

const gpsOffsetM = computed(() =>
  haversineM(vehicle.value?.position, vehicle.value?.snappedPosition),
)

const distToNextStopM = computed(() => {
  const cs = vehicle.value?.currentStop
  return cs ? haversineM(vehicle.value?.position, { lat: cs.stopLat, lon: cs.stopLon }) : null
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
    toast.add({ title: 'Coordinates copied', color: 'success', icon: 'i-lucide-check' })
  } catch {
    toast.add({ title: 'Copy failed', color: 'error' })
  }
}

const facts = computed<Fact[]>(() => {
  const v = vehicle.value
  if (!v) return []
  const dir = v.trip?.directionId
  return [
    { label: 'Vehicle ID', value: v.vehicleId },
    { label: 'Label', value: v.label ?? undefined },
    { label: 'Route', value: v.trip?.route?.routeShortName ?? v.trip?.routeId ?? undefined },
    { label: 'Direction', value: dir == null ? undefined : `Direction ${dir}` },
    { label: 'Headsign', value: v.trip?.tripHeadsign ?? undefined },
    { label: 'Block', value: v.block?.blockId ?? undefined },
    {
      label: 'Trip',
      value: blockProgress.value
        ? `${blockProgress.value.index} of ${blockProgress.value.total}`
        : undefined,
    },
    { label: 'Occupancy', value: v.occupancyStatus ?? undefined },
    { label: 'Status', value: currentStatus.value ?? undefined },
  ]
})

function hhmm(iso: string | null): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    timeZone: agencyTimezone.value,
  }).format(new Date(iso))
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

function adherenceDelta(sec: number): string {
  const total = Math.abs(Math.round(sec))
  if (total === 0) return 'On schedule'
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  const duration = minutes
    ? `${minutes}m${seconds ? ` ${seconds}s` : ''}`
    : `${seconds}s`
  return `${duration} ${sec > 0 ? 'late' : 'early'}`
}

function adherenceTextClass(sec: number | null): string {
  switch (adherenceBadge(sec).color) {
    case 'error':
      return 'text-error'
    case 'warning':
      return 'text-warning'
    case 'success':
      return 'text-success'
    default:
      return 'text-muted'
  }
}
</script>

<template>
  <AppPage :title="title" :description="description" >
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
      <NavbarActions :updated-at="vehicle?.reportTs" />
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
      <div class="flex w-104 shrink-0 flex-col gap-3 border-r border-default p-4">
        <USkeleton class="h-5 w-2/3" />
        <USkeleton class="h-6 w-full" />
        <USkeleton class="h-9 w-full" />
        <USkeleton v-for="i in 8" :key="i" class="h-12 w-full" />
      </div>
      <USkeleton class="min-w-0 flex-1 rounded-none" />
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
      <aside
        class="flex w-100 shrink-0 flex-col gap-4 overflow-y-auto p-4"
      >
        <UAlert
          v-if="descMismatch"
          color="warning"
          icon="i-lucide-git-compare-arrows"
          title="Descriptor mismatch"
          :description="`Feed reports trip ${descMismatch.declared}; matched to ${descMismatch.matched}.`"
        />

        <div
          :class="mapExpanded
            ? 'fixed inset-0 z-50 overflow-hidden bg-default'
            : 'relative h-56 shrink-0 overflow-hidden rounded-lg border border-default'"
        >
          <UButton
            class="absolute right-3 top-3 z-10"
            color="neutral"
            size="xs"
            variant="solid"
            :icon="mapExpanded ? 'i-lucide-minimize-2' : 'i-lucide-maximize-2'"
            :aria-pressed="mapExpanded"
            @click="mapExpanded = !mapExpanded"
          />
          <VehicleRouteMap
            :line="line"
            :stops="stops"
            :vehicle="marker"
            route-color="#ff0000"
            :trail="trail"
            :follow="true"
            :extent="extent"
            :fullscreen="mapExpanded"
          />
        </div>

        <section class="flex flex-col gap-3 p-1">
          <div class="flex flex-1 flex-row justify-between gap-3">
            <div class="min-w-0">
              <div class="text-xs font-medium uppercase tracking-wide text-dimmed">
                Live assignment
              </div>
              <div class="mt-1 truncate text-sm font-semibold text-highlighted">
                {{
                  vehicle.trip?.tripHeadsign ?? vehicle.trip?.route?.routeLongName ?? 'Unknown trip'
                }}
              </div>
            </div>
            <div class="flex shrink-0 flex-wrap justify-end gap-1.5">
              <UBadge :color="adherenceBadge(vehicle.scheduleAdherenceSec).color" variant="subtle">
                {{ adherenceBadge(vehicle.scheduleAdherenceSec).label }}
              </UBadge>
              <UBadge v-if="vehicle.stale" color="warning" variant="subtle">Stale</UBadge>
              <span
                v-if="cardinal(vehicle.bearing)"
                class="inline-flex items-center gap-1 text-sm text-muted"
              >
                <UIcon name="i-lucide-compass" class="size-3.5" />
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
        </section>

        <section class="border-t border-default pt-4 p-1">
          <div class="mb-2 text-sm font-medium text-highlighted">Vehicle &amp; trip</div>
          <VehicleFactsGrid :items="facts" />
        </section>

        <!-- Position & progress -->
        <section class="flex flex-col gap-2 rounded-lg border border-default bg-default p-3">
          <div class="text-sm font-medium text-highlighted">Position &amp; progress</div>
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
            <UIcon name="i-lucide-copy" class="size-3" />
            {{ coords }}
          </button>
        </section>

        <section class="rounded-lg border border-default bg-default p-3">
          <VehicleTelemetry
            :speed="units.speed(vehicle.speedMps)"
            :speed-trail="speedTrail"
            :report-age-sec="reportAgeSec"
            :avg-interval-sec="avgInterval"
            :trail-count="trail.length"
          />
        </section>

      </aside>
      <div class="flex min-h-0 min-w-0 flex-1 flex-col">
        <section class="min-h-0 flex-1 overflow-y-auto bg-default">
          <header class="sticky top-0 z-1 border-b border-default bg-elevated/80 backdrop-blur">
            <div class="flex flex-col gap-3 border-b border-default px-4 py-3 xl:flex-row xl:items-center xl:justify-between">
              <div class="min-w-0">
                <div class="flex items-center gap-2 text-highlighted">
                  <span class="flex size-6 items-center justify-center rounded-md bg-primary/12 text-primary">
                    <UIcon name="i-lucide-map-pinned" class="size-3.5" />
                  </span>
                  <h2 class="text-lg font-semibold tracking-tight">
                    <template v-if="vehicle.trip?.tripId">
                      Trip
                      <NuxtLink
                        :to="feedPath(`/explore/trips/${encodeURIComponent(vehicle.trip.tripId)}`)"
                        class="text-primary underline-offset-4 hover:underline focus:outline-none focus-visible:rounded focus-visible:ring-2 focus-visible:ring-primary"
                        :title="`Open trip ${vehicle.trip.tripId}`"
                      >
                        #{{ vehicle.trip.tripId }}
                      </NuxtLink>
                      stops
                    </template>
                    <template v-else>Trip stops</template>
                  </h2>
                </div>
                <div class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted">
                  <span>
                    Vehicle <strong class="font-medium text-highlighted">{{ vehicle.vehicleId }}</strong>
                  </span>
                  <span class="hidden h-3 w-px bg-accented sm:block" aria-hidden="true" />
                  <span v-if="vehicle.block">
                    Block <strong class="font-medium text-highlighted">{{ vehicle.block.blockId }}</strong>
                    <template v-if="blockProgress"> trip {{ blockProgress.index }} of {{ blockProgress.total }}</template>
                  </span>
                  <span v-else>Block unavailable</span>
                </div>
                <div class="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-dimmed">
                  <span class="inline-flex items-center gap-1">
                    <UIcon name="i-lucide-clock-3" class="size-3" />
                    {{ agencyTimezone ? `${agencyTimezone}` : 'Service time unavailable' }}
                  </span>
                  <span class="hidden h-3 w-px bg-accented sm:block" aria-hidden="true" />
                  <span>{{ rows.length }} {{ rows.length === 1 ? 'stop' : 'stops' }}</span>
                </div>
              </div>

              <div class="flex w-full max-w-xl flex-col gap-2 sm:flex-row sm:items-end sm:justify-end">
                <label class="min-w-56 flex-1 text-xs font-medium text-muted sm:max-w-64">
                  Prediction model
                  <USelectMenu
                    v-model="algorithm"
                    :items="algorithmOptions"
                    value-key="value"
                    placeholder="Prediction algorithm"
                    class="mt-1 w-full"
                  />
                </label>
                <div
                  v-if="selectedAccuracy"
                  class="flex min-w-56 flex-wrap items-center gap-x-3 gap-y-1 border-l border-default pl-3 text-xs"
                >
                  <span class="w-full font-medium text-muted">Forecast quality · last 7 days</span>
                  <span class="text-dimmed">
                    Mean error
                    <strong class="font-semibold text-highlighted">±{{ Math.round(selectedAccuracy.meanAbsErrorSec) }}s</strong>
                  </span>
                  <span class="text-dimmed">
                    Bias
                    <strong class="font-semibold text-highlighted">{{ signedSec(selectedAccuracy.meanErrorSec) }}</strong>
                  </span>
                  <span class="text-dimmed">{{ selectedAccuracy.sampleCount.toLocaleString() }} samples</span>
                </div>
                <p v-else class="border-l border-default pl-3 text-xs text-dimmed">
                  Forecast performance is unavailable for this trip.
                </p>
              </div>
            </div>
            <div
              class="hidden grid-cols-[minmax(15rem,1.4fr)_minmax(8rem,0.7fr)_minmax(10rem,0.9fr)_minmax(9rem,0.8fr)] gap-3 bg-default/35 px-4 py-1.5 text-xs font-medium text-muted lg:grid"
            >
              <span>Stop</span>
              <span>Scheduled</span>
              <span>Live time</span>
              <span class="text-right">Adherence</span>
            </div>
          </header>
          <ol class="divide-y divide-default">
            <li
              v-for="r in rows"
              :key="r.stopPathIndex"
              class="grid gap-x-3 gap-y-2 border-l-2 px-4 py-2 sm:grid-cols-2 lg:grid-cols-[minmax(15rem,1.4fr)_minmax(8rem,0.7fr)_minmax(10rem,0.9fr)_minmax(9rem,0.8fr)] lg:items-center"
              :class="{
                'border-primary bg-elevated/40': r.state === 'current',
                'border-default': r.state !== 'current',
                'opacity-55': r.state === 'passed',
              }"
            >
              <div class="min-w-0 sm:col-span-2 lg:col-span-1">
                <NuxtLink
                  :to="feedPath(`/explore/stops/${encodeURIComponent(r.stopId)}`)"
                  class="block truncate text-sm font-medium leading-4 text-highlighted hover:text-primary hover:underline"
                >
                  {{ r.stopName }}
                </NuxtLink>
                <div class="mt-0.5 flex flex-wrap items-center gap-2 text-xs leading-4 text-muted">
                  <span v-if="r.state === 'current'" class="font-medium text-primary">Current stop</span>
                  <span v-else-if="r.state === 'passed'">Passed</span>
                  <span v-else>Upcoming</span>
                  <span v-if="r.confidenceSec != null" class="text-dimmed">±{{ r.confidenceSec }}s confidence</span>
                </div>
              </div>

              <div class="min-w-0">
                <div class="text-xs font-medium text-dimmed lg:hidden">Scheduled</div>
                <time class="block text-sm font-semibold leading-4 tabular-nums text-highlighted">
                  {{ hhmm(r.scheduledArrival) }}
                </time>
              </div>

              <div class="min-w-0">
                <div class="text-xs font-medium text-dimmed">
                  {{ r.arrived ? 'Arrived' : r.state === 'passed' ? 'Was due' : 'Predicted' }}
                </div>
                <time
                  class="block text-sm font-semibold leading-4 tabular-nums"
                  :class="adherenceTextClass(r.deltaVsScheduleSec)"
                >
                  {{ r.eta ? hhmm(r.eta) : '—' }}
                </time>
                <span v-if="r.eta && !r.arrived && r.state !== 'passed'" class="block text-xs leading-4 text-muted">
                  {{ relative(r.eta) }}
                </span>
              </div>

              <div class="min-w-0 lg:text-right">
                <template v-if="r.deltaVsScheduleSec != null">
                  <div
                    class="mt-0.5 text-sm font-semibold leading-4 tabular-nums"
                    :class="adherenceTextClass(r.deltaVsScheduleSec)"
                  >
                    {{ adherenceDelta(r.deltaVsScheduleSec) }}
                  </div>
                  <div v-if="!r.arrived" class="text-xs leading-4 text-muted">
                    {{ r.arrived ? 'actual vs schedule' : 'no computation yet' }}
                  </div>
                </template>
                <span v-else class="text-xs text-dimmed">No schedule comparison</span>
                <span
                  v-if="r.errorVsPredictionSec != null"
                  class="block text-xs leading-4 text-red-500"
                >
                  vs prediction {{ signedSec(r.errorVsPredictionSec) }}
                </span>
              </div>
            </li>
          </ol>
        </section>
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
