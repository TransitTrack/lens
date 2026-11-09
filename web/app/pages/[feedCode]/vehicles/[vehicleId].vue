<script setup lang="ts">
import { useQuery, useQueryCache, type EntryKey } from '@pinia/colada'
import { vehicleQuery } from '~/composables/api/queries/avl/vehicleQuery'
import { vehiclePredictionsQuery } from '~/composables/api/queries/avl/vehiclePredictionsQuery'
import { vehicleTrailQuery } from '~/composables/api/queries/avl/vehicleTrailQuery'
import { predictionAccuracyQuery } from '~/composables/api/queries/avl/predictionAccuracyQuery'
import { avlReportsQuery } from '~/composables/api/queries/avl/avlReportsQuery'
import { tripsByKeysQuery } from '~/composables/api/queries/gtfs/tripsByKeysQuery'
import { routesByRevisionQuery } from '~/composables/api/queries/gtfs/routesByRevisionQuery'
import { agenciesQuery } from '~/composables/api/queries/gtfs/agenciesQuery'
import { tripQuery } from '~/composables/api/queries/gtfs/tripQuery'
import { routeQuery } from '~/composables/api/queries/gtfs/routeQuery'
import { routeStopsQuery } from '~/composables/api/queries/gtfs/routeStopsQuery'
import { shapeQuery } from '~/composables/api/queries/gtfs/shapeQuery'
import { stopsByIdsQuery } from '~/composables/api/queries/gtfs/stopsByIdsQuery'
import { tripPatternQuery } from '~/composables/api/queries/schedule/tripPatternQuery'
import { stopPathsQuery } from '~/composables/api/queries/schedule/stopPathsQuery'
import { blockQuery } from '~/composables/api/queries/schedule/blockQuery'
import { blockTripsQuery } from '~/composables/api/queries/schedule/blockTripsQuery'
import type { PredictionAlgorithm } from '~/api'
import type { AvlLogQuery, PredictionAccuracy } from '~/types/restViews'
import { reportRouteIdsByRevision, reportTripKeys, reportsWithTripsByRevision } from '~/utils/joins/reports'
import { gtfsFeedCodeOf } from '~/utils/joins/feeds'
import { missingStopIds, needsShape, vehicleDetail, vehiclePattern, type VehicleDetail } from '~/utils/joins/vehicleDetail'
import SplitterGrip from '~/components/shell/SplitterGrip.vue'
import { formatNumber } from '~/utils/intlLocale'
import type { SplitterItem } from '@nuxt/ui'
import AppPage from '~/components/shell/AppPage.vue'
import PollControl from '~/components/shell/PollControl.vue'
import VehicleFactsGrid, { type Fact } from '~/components/vehicles/VehicleFactsGrid.vue'
import VehicleTelemetry from '~/components/vehicles/VehicleTelemetry.vue'
import VehicleAvlLog from '~/components/vehicles/VehicleAvlLog.vue'
import VehicleTripStrip from '~/components/vehicles/VehicleTripStrip.vue'
import { useFeeds } from '~/composables/useFeeds'
import { usePollControl } from '~/composables/usePollControl'
import { useUnits } from '~/composables/useUnits'

import { adherenceBadge, signedAdherence } from '~/utils/adherence'
import { formatClock } from '~/utils/clock'
import {
  haversineM,
  blockTripProgress,
  currentStatusLabel,
  occupancyLabel,
  ageLabel,
} from '~/utils/vehicleTelemetry'
import {
  chosenAlgorithm,
  accuracyFor,
  stopRows,
  trackCoords,
  tripStrip,
  speedProfile,
} from '~/utils/vehicleDetail'
import { useSplitLayout } from '~/composables/useSplitLayout'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const { selectedFeedCode, selectedFeed, selectedAvlFeedCode, feedPath, avlFeeds } = useFeeds()
const { paused, intervalMs } = usePollControl()
const units = useUnits()
const toast = useToast()
const vehicleId = computed(() => String(route.params.vehicleId))

// Which algorithm's predictions to load: the selector's options are the feed's configured algorithms,
// and the pick is sent with the request (the server returns only that algorithm's rows).
const algorithms = computed(() => selectedFeed.value?.predictionAlgorithms ?? [])
const selectedAlgorithm = ref<string | undefined>()
const algorithm = computed({
  get: () => chosenAlgorithm(algorithms.value, selectedAlgorithm.value),
  set: (value: string | undefined) => {
    selectedAlgorithm.value = value
  },
})

// --- the vehicle and its relations --------------------------------------------
// The vehicle and its predictions poll; what they point into is pinned to the vehicle's revision and read once.
// Pausing stops auto-refetch; a paused query still loads once when its own key has no cached data yet. The
// cache is read by key, not through `data` (which holds the previous key's placeholder).
const queryCache = useQueryCache()
const pollInterval = () => (paused.value ? false : intervalMs.value)
const pollEnabled = (base: boolean, key: EntryKey) =>
  base && (!paused.value || queryCache.getQueryData(key) === undefined)

const vehicleQ = useQuery(() => {
  const options = vehicleQuery({ avlFeedCode: selectedAvlFeedCode.value ?? '', vehicleId: vehicleId.value })
  return { ...options, enabled: pollEnabled(!!selectedAvlFeedCode.value, options.key), autoRefetch: pollInterval }
})
const predictionsQ = useQuery(() => {
  const options = vehiclePredictionsQuery({
    avlFeedCode: selectedAvlFeedCode.value ?? '', vehicleId: vehicleId.value,
    algorithm: algorithm.value as PredictionAlgorithm | undefined,
  })
  return { ...options, enabled: pollEnabled(!!selectedAvlFeedCode.value, options.key), autoRefetch: pollInterval }
})
const liveVehicle = computed(() => (selectedAvlFeedCode.value ? vehicleQ.data.value ?? null : null))
const livePredictions = computed(() => (selectedAvlFeedCode.value ? predictionsQ.data.value ?? [] : []))

const gtfsFeedCode = computed(() =>
  selectedAvlFeedCode.value ? gtfsFeedCodeOf(avlFeeds.value, selectedAvlFeedCode.value) : null,
)
const scope = computed(() => {
  const revisionId = liveVehicle.value?.revisionId
  return gtfsFeedCode.value && revisionId ? { feedCode: gtfsFeedCode.value, revisionId } : null
})
const pinned = () => scope.value ?? { feedCode: '' }

const tripEnabled = computed(() => !!scope.value && !!liveVehicle.value?.tripId)
const tripQ = useQuery(() => ({
  ...tripQuery({ ...pinned(), tripId: liveVehicle.value?.tripId ?? '' }),
  enabled: tripEnabled.value,
}))
const trip = computed(() => (tripEnabled.value ? tripQ.data.value ?? null : null))

const routeEnabled = computed(() => !!trip.value)
const routeQ = useQuery(() => ({
  ...routeQuery({ ...pinned(), routeId: trip.value?.routeId ?? '' }),
  enabled: routeEnabled.value,
}))
const patternEnabled = computed(() => !!trip.value?.patternKey)
const patternQ = useQuery(() => ({
  ...tripPatternQuery({ ...pinned(), patternKey: trip.value?.patternKey ?? '' }),
  enabled: patternEnabled.value,
}))
const stopPathsQ = useQuery(() => ({
  ...stopPathsQuery({ ...pinned(), routeId: trip.value?.routeId, patternKey: trip.value?.patternKey ?? undefined }),
  enabled: patternEnabled.value,
}))
const routeStopsQ = useQuery(() => ({
  ...routeStopsQuery({ ...pinned(), routeId: trip.value?.routeId ?? '' }),
  enabled: patternEnabled.value,
}))
const blockEnabled = computed(() => !!trip.value?.blockId)
const blockParams = () => ({ ...pinned(), blockId: trip.value?.blockId ?? '', serviceId: trip.value?.serviceId ?? '' })
const blockQ = useQuery(() => ({ ...blockQuery(blockParams()), enabled: blockEnabled.value }))
const blockTripsQ = useQuery(() => ({ ...blockTripsQuery(blockParams()), enabled: blockEnabled.value }))

/** Still waiting on an enabled read (no data of its own key yet, and no error). */
const pending = (enabled: boolean, q: { data: Readonly<Ref<unknown>>, error: Readonly<Ref<unknown>>, isPlaceholderData: Readonly<Ref<boolean>> }) =>
  enabled && !q.error.value && (q.data.value === undefined || q.isPlaceholderData.value)

const pattern = computed(() => (patternEnabled.value ? patternQ.data.value ?? null : null))
const patternSettled = computed(() =>
  !pending(tripEnabled.value, tripQ)
  && !pending(patternEnabled.value, patternQ) && !pending(patternEnabled.value, stopPathsQ) && !pending(patternEnabled.value, routeStopsQ),
)
const joinedPattern = computed(() =>
  trip.value ? vehiclePattern(trip.value, pattern.value, stopPathsQ.data.value ?? [], routeStopsQ.data.value ?? []) : null,
)
// The route line falls back to the GTFS shape only when the pattern has no usable geometry.
const shapeEnabled = computed(() => patternSettled.value && !!trip.value?.shapeId && needsShape(pattern.value))
const shapeQ = useQuery(() => ({
  ...shapeQuery({ ...pinned(), shapeId: trip.value?.shapeId ?? '' }),
  enabled: shapeEnabled.value,
}))
// The current and predicted stops sit on the pattern; read only those that aren't there.
const missingStops = computed(() =>
  patternSettled.value && !pending(true, vehicleQ) && !pending(true, predictionsQ)
    ? missingStopIds(liveVehicle.value, livePredictions.value, joinedPattern.value)
    : [],
)
const stopsEnabled = computed(() => !!scope.value && missingStops.value.length > 0)
const stopsQ = useQuery(() => ({
  ...stopsByIdsQuery({ ...pinned(), stopIds: missingStops.value }),
  enabled: stopsEnabled.value,
}))

const chain = computed(() => [
  { enabled: !!selectedAvlFeedCode.value, q: vehicleQ },
  { enabled: !!selectedAvlFeedCode.value, q: predictionsQ },
  { enabled: tripEnabled.value, q: tripQ },
  { enabled: routeEnabled.value, q: routeQ },
  { enabled: patternEnabled.value, q: patternQ },
  { enabled: patternEnabled.value, q: stopPathsQ },
  { enabled: patternEnabled.value, q: routeStopsQ },
  { enabled: blockEnabled.value, q: blockQ },
  { enabled: blockEnabled.value, q: blockTripsQ },
  { enabled: shapeEnabled.value, q: shapeQ },
  { enabled: stopsEnabled.value, q: stopsQ },
])
// Like the single composite read it replaces: the joined detail appears once every read it needs has
// answered, and the previous detail stays on screen meanwhile (a new trip, another vehicle).
const detail = computed<VehicleDetail | null>((previous) => {
  if (chain.value.some(({ enabled, q }) => pending(enabled, q))) return previous ?? null
  return vehicleDetail({
    vehicle: liveVehicle.value,
    predictions: livePredictions.value,
    trip: trip.value,
    route: routeEnabled.value ? routeQ.data.value ?? null : null,
    shape: shapeEnabled.value ? shapeQ.data.value ?? null : null,
    pattern: pattern.value,
    stopPaths: patternEnabled.value ? stopPathsQ.data.value ?? [] : [],
    routeStops: patternEnabled.value ? routeStopsQ.data.value ?? [] : [],
    block: blockEnabled.value ? blockQ.data.value ?? null : null,
    blockTrips: blockEnabled.value ? blockTripsQ.data.value ?? [] : [],
    stops: stopsEnabled.value ? stopsQ.data.value ?? [] : [],
  })
})
const loading = computed(() => chain.value.some(({ enabled, q }) => enabled && q.isLoading.value))
const error = computed(() => chain.value.find(({ enabled, q }) => enabled && q.error.value)?.q.error.value ?? null)

const { data: trailData } = useQuery(() => {
  const options = vehicleTrailQuery({ avlFeedCode: selectedAvlFeedCode.value ?? '', vehicleId: vehicleId.value, limit: 200 })
  return { ...options, enabled: pollEnabled(!!selectedAvlFeedCode.value, options.key), autoRefetch: pollInterval }
})
const { data: agencies } = useQuery(() => ({
  ...agenciesQuery({ feedCode: selectedFeedCode.value ?? '' }),
  enabled: !!selectedFeedCode.value,
}))
const agencyTimezone = computed(
  () => agencies.value?.find((agency) => agency.agencyTimezone)?.agencyTimezone,
)

const vehicle = computed(() => detail.value?.vehicle ?? null)
const predictions = computed(() => detail.value?.vehiclePredictions ?? [])
// Feed-wide aggregate (last 7 days) — slow to compute and the same for every vehicle, so poll it rarely.
const { data: accuracyData } = useQuery(() => {
  const options = predictionAccuracyQuery({ avlFeedCode: selectedAvlFeedCode.value ?? '' })
  return {
    ...options,
    enabled: pollEnabled(!!selectedAvlFeedCode.value, options.key),
    autoRefetch: () => (paused.value ? false : 5 * 60_000),
  }
})
const accuracy = computed(() => (accuracyData.value ?? []) as unknown as PredictionAccuracy)

// The backend summarises the vehicle's recent movement (track, cadence, newest status, speeds).
const vehicleTrail = computed(() => (selectedAvlFeedCode.value ? trailData.value ?? null : null))
const trail = computed(() => trackCoords(vehicleTrail.value?.track ?? []))

// --- AVL log ------------------------------------------------------------------
// Read (and polled) only while the log is open. Reports may span revisions: their trips are read per
// (revision, trip) and their routes per revision, and each report joins its own revision's route.
const avlLogOpen = ref(false)
const logEnabled = computed(() => avlLogOpen.value && !!selectedAvlFeedCode.value)
const logReportsQ = useQuery(() => {
  const options = avlReportsQuery({ avlFeedCode: selectedAvlFeedCode.value ?? '', vehicleId: vehicleId.value, limit: 100 })
  return {
    ...options,
    enabled: pollEnabled(logEnabled.value, options.key),
    autoRefetch: () => (logEnabled.value && !paused.value ? intervalMs.value : false),
  }
})
const logReports = computed(() => (logEnabled.value ? logReportsQ.data.value ?? [] : []))
const logTripsEnabled = computed(() => logEnabled.value && !!gtfsFeedCode.value && !pending(true, logReportsQ))
const logTripsQ = useQuery(() => ({
  ...tripsByKeysQuery({ feedCode: gtfsFeedCode.value ?? '', keys: reportTripKeys(logReports.value) }),
  enabled: logTripsEnabled.value,
}))
const logTrips = computed(() => (logTripsEnabled.value ? logTripsQ.data.value : undefined) ?? new Map())
const logRoutesEnabled = computed(() => logTripsEnabled.value && !pending(true, logTripsQ))
const logRoutesQ = useQuery(() => ({
  ...routesByRevisionQuery({ feedCode: gtfsFeedCode.value ?? '', routeIdsByRevision: reportRouteIdsByRevision(logTrips.value) }),
  enabled: logRoutesEnabled.value,
}))
const logChain = computed(() => [
  { enabled: logEnabled.value, q: logReportsQ },
  { enabled: logTripsEnabled.value, q: logTripsQ },
  { enabled: logRoutesEnabled.value, q: logRoutesQ },
])
// Rows appear once the trips and routes of the newest reports have answered; the previous rows stay meanwhile.
const logRows = computed<AvlLogQuery['avlReports']>((previous) => {
  if (!logEnabled.value) return []
  if (logChain.value.some(({ enabled, q }) => pending(enabled, q))) return previous ?? []
  const routes = (logRoutesEnabled.value ? logRoutesQ.data.value : undefined) ?? new Map()
  return reportsWithTripsByRevision(logReports.value, logTrips.value, routes) as unknown as AvlLogQuery['avlReports']
})
const logLoading = computed(() => logChain.value.some(({ enabled, q }) => enabled && q.isLoading.value))
const logError = computed(() => logChain.value.find(({ enabled, q }) => enabled && q.error.value)?.q.error.value ?? null)

// Details panel beside (desktop) or above (mobile) the trip workspace.
const { splitterOrientation: pageSplitterOrientation } = useSplitLayout()
const pageSplitterItems: SplitterItem[] = [
  {
    id: 'details',
    slot: 'details',
    sizeUnit: 'px',
    defaultSize: 340,
    minSize: 280,
    maxSize: 680,
    class: 'flex min-h-0 flex-col overflow-hidden',
  },
  { id: 'workspace', slot: 'workspace', class: 'flex min-h-0 flex-col overflow-hidden' },
]

watch(selectedAvlFeedCode, () => {
  selectedAlgorithm.value = undefined
})
function algorithmLabel(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((word) => word[0]!.toUpperCase() + word.slice(1))
    .join(' ')
}
const algorithmOptions = computed(() =>
  algorithms.value.map((value) => ({ label: algorithmLabel(value), value })),
)
const selectedAccuracy = computed(() => accuracyFor(algorithm.value, accuracy.value))

const routeColor = computed(() => {
  const raw = vehicle.value?.trip?.route?.routeColor
  if (!raw) return '#3b82f6'
  return raw.startsWith('#') ? raw : `#${raw}`
})

const rows = computed(() =>
  vehicle.value ? stopRows(vehicle.value, predictions.value, algorithm.value) : [],
)
const strip = computed(() => (vehicle.value ? tripStrip(vehicle.value, rows.value) : null))
const stripSpeeds = computed(() =>
  strip.value ? speedProfile(strip.value, vehicleTrail.value?.speeds ?? []) : [],
)
const currentStopRow = ref<HTMLElement | null>(null)
// const currentStopKey = computed(() => {
//   const current = rows.value.find((row) => row.state === 'current')
//   return current ? `${vehicle.value?.trip?.tripId ?? ''}:${current.stopPathIndex}` : null
// })

function setCurrentStopRow(element: unknown, isCurrent: boolean) {
  if (isCurrent) currentStopRow.value = element instanceof HTMLElement ? element : null
}

// watch(
//   currentStopKey,
//   async (key) => {
//     if (!key) return
//     await nextTick()
//     currentStopRow.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
//   },
//   { immediate: true, flush: 'post' },
// )

const title = computed(() => {
  const v = vehicle.value
  return t('vehicles.detail.title', { name: v ? (v.label ?? v.vehicleId) : vehicleId.value })
})

const description = computed(() => {
  const v = vehicle.value
  return v ? `#${v.vehicleId ?? v.label}` : `#${vehicleId.value}`
})

// --- details -----------------------------------------------------------------
const blockProgress = computed(() =>
  blockTripProgress(vehicle.value?.block?.blockTrips, vehicle.value?.trip?.tripId),
)

const currentStatus = computed(() => currentStatusLabel(vehicleTrail.value?.currentStatus, t))

const descMismatch = computed(() => {
  const declared = vehicleTrail.value?.descTripId
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

const avgInterval = computed(() => vehicleTrail.value?.avgReportIntervalSec ?? null)

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

const reportAgeLabel = computed(() => {
  const seconds = reportAgeSec.value
  if (seconds == null) return t('vehicles.detail.noRecentReport')
  return ageLabel(seconds, t)
})

const coords = computed(() => {
  const p = vehicle.value?.position
  return p ? `${p.lat.toFixed(5)}, ${p.lon.toFixed(5)}` : null
})

async function copyCoords() {
  if (!coords.value) return
  try {
    await navigator.clipboard.writeText(coords.value)
    toast.add({
      title: t('vehicles.detail.coordsCopied'),
      color: 'success',
      icon: 'i-lucide-check',
    })
  } catch {
    toast.add({ title: t('vehicles.detail.copyFailed'), color: 'error' })
  }
}

const facts = computed<Fact[]>(() => {
  const v = vehicle.value
  if (!v) return []
  const dir = v.trip?.directionId
  return [
    { label: t('vehicles.field.vehicleId'), value: v.vehicleId },
    { label: t('vehicles.detail.fact.label'), value: v.label ?? undefined },
    {
      label: t('vehicles.field.route'),
      value: v.trip?.route?.routeShortName ?? v.trip?.routeId ?? undefined,
    },
    {
      label: t('vehicles.detail.fact.direction'),
      value: dir == null ? undefined : t('vehicles.detail.fact.directionN', { n: dir }),
    },
    { label: t('vehicles.detail.fact.headsign'), value: v.trip?.tripHeadsign ?? undefined },
    { label: t('vehicles.field.block'), value: v.block?.blockId ?? undefined },
    {
      label: t('vehicles.field.trip'),
      value: blockProgress.value
        ? t('vehicles.detail.fact.tripOf', {
            index: blockProgress.value.index,
            total: blockProgress.value.total,
          })
        : undefined,
    },
    {
      label: t('vehicles.detail.fact.occupancy'),
      value: v.occupancyStatus ? occupancyLabel(v.occupancyStatus, t) : undefined,
    },
    { label: t('vehicles.detail.fact.status'), value: currentStatus.value ?? undefined },
  ]
})

function hhmm(iso: string | null): string {
  return formatClock(iso, { timeZone: agencyTimezone.value, seconds: true, locale: locale.value })
}

function relative(iso: string | null): string {
  if (!iso) return ''
  const deltaSec = Math.round((new Date(iso).getTime() - now.value) / 1000)
  if (deltaSec < -30) return t('poll.minutesAgo', { count: Math.round(-deltaSec / 60) })
  if (deltaSec < 45) return t('vehicles.detail.now')
  return t('vehicles.detail.inMinutes', { count: Math.round(deltaSec / 60) })
}

function signedSec(sec: number | null): string {
  if (sec === null) return '—'
  const s = Math.round(sec)
  return `${s >= 0 ? '+' : '−'}${Math.abs(s)}s`
}

function adherenceTextClass(sec: number | null): string {
  switch (adherenceBadge(sec, t).color) {
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
  <AppPage :title="title" :description="description">
    <template #leading>
      <UButton
        icon="i-lucide-arrow-left"
        color="neutral"
        variant="ghost"
        :aria-label="t('vehicles.detail.goBack')"
        @click="router.back()"
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
      <UTooltip :text="t('vehicles.avl.title')">
        <UButton
          color="neutral"
          variant="ghost"
          icon="i-lucide-list"
          square
          :aria-label="t('vehicles.avl.title')"
          @click="avlLogOpen = true"
        />
      </UTooltip>
      <PollControl :updated-at="vehicle?.reportTs" />
    </template>

    <UAlert
      v-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      :title="t('vehicles.detail.unavailable')"
      :description="error.message"
      class="m-4"
    />
    <div v-else-if="loading && !vehicle" class="flex min-h-0 flex-1 flex-col lg:flex-row">
      <div
        class="flex w-full shrink-0 flex-col gap-3 border-b border-default p-4 lg:w-104 lg:border-b-0 lg:border-r"
      >
        <USkeleton class="h-5 w-2/3" />
        <USkeleton class="h-6 w-full" />
        <USkeleton class="h-9 w-full" />
        <USkeleton v-for="i in 8" :key="i" class="h-12 w-full" />
      </div>
      <USkeleton class="min-h-56 min-w-0 flex-1 rounded-none" />
    </div>
    <UAlert
      v-else-if="!vehicle"
      color="warning"
      variant="soft"
      icon="i-lucide-search-x"
      :title="t('vehicles.detail.notTracked')"
      :description="t('vehicles.detail.notTrackedBody', { id: vehicleId })"
      class="m-4"
    />

    <template v-else>
      <section v-if="strip" class="shrink-0 border border-default pb-2 shadow-sm bg-primary/5">
        <VehicleTripStrip
          :strip="strip"
          :adherence-sec="vehicle.scheduleAdherenceSec ?? null"
          :speeds="stripSpeeds"
          :stale="vehicle.stale"
          :time-zone="agencyTimezone ?? undefined"
        />
      </section>
      <USplitter
        id="vehicle-page-splitter"
        :orientation="pageSplitterOrientation"
        :items="pageSplitterItems"
        class="min-h-0 flex-1"
      >
        <template #details>
          <aside
            class="flex h-full w-full flex-col gap-4 overflow-y-auto border-r border-default pr-2"
          >
            <UAlert
              v-if="descMismatch"
              color="warning"
              icon="i-lucide-git-compare-arrows"
              :title="t('vehicles.detail.mismatchTitle')"
              :description="t('vehicles.detail.mismatchBody', descMismatch)"
            />

            <section class="app-frame">
              <header class="flex items-start justify-between gap-3 border-b border-default p-3">
                <div class="min-w-0">
                  <div class="text-xs font-medium uppercase tracking-wide text-dimmed">
                    {{ t('vehicles.detail.liveService') }}
                  </div>
                  <div class="mt-1 truncate text-base font-semibold text-highlighted">
                    {{
                      vehicle.trip?.tripHeadsign ??
                      vehicle.trip?.route?.routeLongName ??
                      t('vehicles.detail.noActiveTrip')
                    }}
                  </div>
                </div>
                <div class="flex shrink-0 flex-wrap justify-end gap-1.5">
                  <UBadge
                    v-if="vehicle.trip?.route?.routeShortName"
                    :style="{ backgroundColor: routeColor, color: '#fff' }"
                  >
                    {{ vehicle.trip.route.routeShortName }}
                  </UBadge>
                  <UBadge
                    :color="adherenceBadge(vehicle.scheduleAdherenceSec, t).color"
                    variant="subtle"
                  >
                    {{ adherenceBadge(vehicle.scheduleAdherenceSec, t).label }}
                  </UBadge>
                  <UBadge v-if="vehicle.stale" color="warning" variant="subtle">{{
                    t('vehicles.detail.stale')
                  }}</UBadge>
                </div>
              </header>

              <div class="grid grid-cols-2">
                <div class="col-span-2 border-b border-default p-3">
                  <div class="flex items-center gap-1.5 text-xs font-medium text-dimmed">
                    <UIcon name="i-lucide-map-pin" class="size-3.5 text-primary" />
                    {{ t('vehicles.field.nextStop') }}
                  </div>
                  <div class="mt-1 truncate text-base font-semibold text-highlighted">
                    {{ vehicle.currentStop?.stopName ?? t('vehicles.detail.noNextStop') }}
                  </div>
                </div>
                <div class="border-r border-default p-3">
                  <div class="text-xs font-medium text-dimmed">
                    {{ t('vehicles.field.position') }}
                  </div>
                  <div class="mt-1 text-sm font-semibold text-highlighted">
                    {{
                      distToNextStopM == null
                        ? t('vehicles.detail.distanceUnavailable')
                        : t('vehicles.detail.distanceToStop', {
                            distance: units.distance(distToNextStopM),
                          })
                    }}
                  </div>
                </div>
                <div class="p-3">
                  <div class="text-xs font-medium text-dimmed">
                    {{ t('vehicles.field.lastReport') }}
                  </div>
                  <div class="mt-1 text-sm font-semibold text-highlighted">
                    {{ reportAgeLabel }}
                  </div>
                </div>
              </div>
            </section>

            <!-- Position & progress -->
            <section class="app-frame flex flex-col gap-2 p-3">
              <div class="text-sm font-medium text-highlighted">
                {{ t('vehicles.field.position') }}
              </div>
              <dl class="grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
                <dt class="text-dimmed">{{ t('vehicles.detail.alongTrip') }}</dt>
                <dd class="text-muted">{{ units.distance(vehicle.distanceAlongTripM) }}</dd>
                <dt class="text-dimmed">{{ t('vehicles.detail.toNextStop') }}</dt>
                <dd class="text-muted">{{ units.distance(distToNextStopM) }}</dd>
                <dt class="text-dimmed">{{ t('vehicles.detail.gpsOffset') }}</dt>
                <dd class="text-muted">
                  {{
                    gpsOffsetM == null
                      ? '—'
                      : gpsOffsetM <= 15
                        ? t('vehicles.field.onRoute')
                        : units.distance(gpsOffsetM)
                  }}
                </dd>
              </dl>
              <button
                v-if="coords"
                class="inline-flex w-fit items-center gap-1 font-mono text-xs text-dimmed hover:text-muted"
                @click="copyCoords"
              >
                <UIcon name="i-lucide-copy" class="size-3" />
                {{ coords }}
              </button>
            </section>

            <section class="app-frame p-3">
              <VehicleTelemetry
                :speed="units.speed(vehicle.speedMps)"
                :report-age-sec="reportAgeSec"
                :avg-interval-sec="avgInterval"
                :trail-count="trail.length"
              />
            </section>

            <section class="app-frame p-3">
              <div class="mb-2 text-sm font-medium text-highlighted">
                {{ t('vehicles.detail.vehicleAndTrip') }}
              </div>
              <VehicleFactsGrid :items="facts" />
            </section>
          </aside>
        </template>

        <template #workspace>
          <section class="min-h-0 flex-1 overflow-y-auto bg-default">
            <header class="sticky top-0 z-1 border border-default bg-elevated/80 backdrop-blur">
              <div
                class="flex flex-col gap-3 border-b border-default px-4 py-3 xl:flex-row xl:items-center xl:justify-between"
              >
                <div class="min-w-0">
                  <div class="flex items-center gap-2 text-highlighted">
                    <span
                      class="flex size-6 items-center justify-center rounded-md bg-primary/12 text-primary"
                    >
                      <UIcon name="i-lucide-map-pinned" class="size-3.5" />
                    </span>
                    <div class="min-w-0">
                      <div class="text-xs font-medium uppercase tracking-wide text-dimmed">
                        {{ t('vehicles.detail.currentTrip') }}
                      </div>
                      <h2 class="mt-0.5 truncate text-base font-semibold tracking-tight">
                        <template v-if="vehicle.trip?.tripId">
                          <NuxtLink
                            :to="
                              feedPath(`/explore/trips/${encodeURIComponent(vehicle.trip.tripId)}`)
                            "
                            class="text-primary underline-offset-4 hover:underline focus:outline-none focus-visible:rounded focus-visible:ring-2 focus-visible:ring-primary"
                            :title="t('vehicles.detail.openTrip', { id: vehicle.trip.tripId })"
                          >
                            #{{ vehicle.trip.tripId }}
                          </NuxtLink>
                          <span class="text-highlighted">
                            ·
                            {{
                              t('vehicles.detail.stopsHeader', { count: rows.length }, rows.length)
                            }}</span
                          >
                        </template>
                        <template v-else>{{ t('vehicles.detail.tripStops') }}</template>
                      </h2>
                    </div>
                  </div>
                  <div class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted">
                    <span>
                      {{ t('vehicles.field.vehicle') }}
                      <strong class="font-medium text-highlighted">{{ vehicle.vehicleId }}</strong>
                    </span>
                    <span class="hidden h-3 w-px bg-accented sm:block" aria-hidden="true" />
                    <span v-if="vehicle.block">
                      {{ t('vehicles.field.block') }}
                      <strong class="font-medium text-highlighted">{{
                        vehicle.block.blockId
                      }}</strong>
                      <template v-if="blockProgress">
                        {{
                          t('vehicles.detail.blockProgress', {
                            index: blockProgress.index,
                            total: blockProgress.total,
                          })
                        }}</template
                      >
                    </span>
                    <span v-else>{{ t('vehicles.detail.blockUnavailable') }}</span>
                  </div>
                  <div
                    class="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-dimmed"
                  >
                    <span class="inline-flex items-center gap-1">
                      <UIcon name="i-lucide-clock-3" class="size-3" />
                      {{
                        agencyTimezone
                          ? `${agencyTimezone}`
                          : t('vehicles.detail.serviceTimeUnavailable')
                      }}
                    </span>
                    <span class="hidden h-3 w-px bg-accented sm:block" aria-hidden="true" />
                    <span>{{
                      t('vehicles.detail.stopsCount', { count: rows.length }, rows.length)
                    }}</span>
                  </div>
                </div>

                <div
                  class="flex w-full max-w-xl flex-col gap-2 sm:flex-row sm:items-end sm:justify-end"
                >
                  <label class="min-w-56 flex-1 text-xs font-medium text-muted sm:max-w-64">
                    {{ t('vehicles.detail.predictionModel') }}
                    <USelectMenu
                      v-model="algorithm"
                      :items="algorithmOptions"
                      value-key="value"
                      :placeholder="t('vehicles.detail.predictionAlgorithm')"
                      class="mt-1 w-full"
                    />
                  </label>
                  <div
                    v-if="selectedAccuracy"
                    class="flex min-w-56 flex-wrap items-center gap-x-3 gap-y-1 border-l border-default pl-3 text-xs"
                  >
                    <span class="w-full font-medium text-muted">{{
                      t('vehicles.detail.quality.title')
                    }}</span>
                    <span class="text-dimmed">
                      {{ t('vehicles.detail.quality.pairedMae') }}
                      <strong class="font-semibold text-highlighted">{{
                        selectedAccuracy.meanAbsErrorSec == null
                          ? '—'
                          : `${Math.round(selectedAccuracy.meanAbsErrorSec)}s`
                      }}</strong>
                    </span>
                    <span class="text-dimmed">
                      {{ t('vehicles.detail.quality.bias') }}
                      <strong class="font-semibold text-highlighted">{{
                        selectedAccuracy.meanErrorSec == null
                          ? '—'
                          : signedSec(selectedAccuracy.meanErrorSec)
                      }}</strong>
                    </span>
                    <span class="text-dimmed">
                      {{ t('vehicles.detail.quality.p90p95') }}
                      <strong class="font-semibold text-highlighted"
                        >{{
                          selectedAccuracy.p90AbsErrorSec == null
                            ? '—'
                            : `${Math.round(selectedAccuracy.p90AbsErrorSec)}s`
                        }}
                        /
                        {{
                          selectedAccuracy.p95AbsErrorSec == null
                            ? '—'
                            : `${Math.round(selectedAccuracy.p95AbsErrorSec)}s`
                        }}</strong
                      >
                    </span>
                    <span class="text-dimmed"
                      >{{ t('vehicles.detail.quality.coverage') }}
                      <strong class="font-semibold text-highlighted"
                        >{{ Math.round(selectedAccuracy.predictionCoverage * 100) }}%</strong
                      ></span
                    >
                    <span
                      v-if="selectedAccuracy.confidenceCoverage != null"
                      class="text-dimmed"
                      :title="
                        t(
                          'vehicles.detail.quality.spread',
                          { count: selectedAccuracy.confidenceSampleCount },
                          selectedAccuracy.confidenceSampleCount,
                        )
                      "
                      >{{ t('vehicles.detail.quality.withinSd') }}
                      <strong class="font-semibold text-highlighted"
                        >{{ Math.round(selectedAccuracy.confidenceCoverage * 100) }}%</strong
                      ></span
                    >
                    <span class="text-dimmed">{{
                      t('vehicles.detail.quality.pairedEligible', {
                        paired: formatNumber(selectedAccuracy.pairedSampleCount, locale),
                        eligible: formatNumber(selectedAccuracy.eligibleCount, locale),
                      })
                    }}</span>
                  </div>
                  <p v-else class="border-l border-default pl-3 text-xs text-dimmed">
                    {{ t('vehicles.detail.quality.none') }}
                  </p>
                </div>
              </div>
              <div
                class="hidden grid-cols-[minmax(15rem,1.4fr)_minmax(8rem,0.7fr)_minmax(10rem,0.9fr)_minmax(9rem,0.8fr)] gap-3 bg-default/35 px-4 py-1.5 text-xs font-medium text-muted lg:grid"
              >
                <span>{{ t('vehicles.field.stop') }}</span>
                <span>{{ t('vehicles.field.scheduled') }}</span>
                <span>{{ t('vehicles.detail.liveTime') }}</span>
                <span class="text-right">{{ t('vehicles.field.adherence') }}</span>
              </div>
            </header>
            <ol class="divide-y divide-default">
              <li
                v-for="r in rows"
                :key="r.stopPathIndex"
                :ref="(element) => setCurrentStopRow(element, r.state === 'current')"
                class="grid gap-x-3 gap-y-2 border-l border-default border-r px-4 py-2 sm:grid-cols-2 lg:grid-cols-[minmax(15rem,1.4fr)_minmax(8rem,0.7fr)_minmax(10rem,0.9fr)_minmax(9rem,0.8fr)] lg:items-center"
                :class="{
                  'border-l-primary bg-elevated/40 border-l-4': r.state === 'current',
                  'border-l-default': r.state !== 'current',
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
                  <div
                    class="mt-0.5 flex flex-wrap items-center gap-2 text-xs leading-4 text-muted"
                  >
                    <span v-if="r.state === 'current'" class="font-medium text-primary">{{
                      t('vehicles.field.nextStop')
                    }}</span>
                    <span v-else-if="r.state === 'passed'">{{ t('vehicles.detail.passed') }}</span>
                    <span v-else>{{ t('vehicles.detail.upcoming') }}</span>
                    <span v-if="r.confidenceSec != null" class="text-dimmed">{{
                      t('vehicles.detail.confidence', { seconds: r.confidenceSec })
                    }}</span>
                    <span
                      v-if="r.arrived && r.actualReliable === false"
                      class="text-warning"
                      :title="t('vehicles.detail.interpolated', { seconds: r.actualReportGapSec })"
                      >{{ t('vehicles.detail.sparseGps') }}</span
                    >
                  </div>
                </div>

                <div class="min-w-0">
                  <div class="text-xs font-medium text-dimmed lg:hidden">
                    {{ t('vehicles.field.scheduled') }}
                  </div>
                  <time class="block text-sm font-semibold leading-4 tabular-nums text-highlighted">
                    {{ hhmm(r.scheduledArrival) }}
                  </time>
                </div>

                <div class="min-w-0">
                  <div class="text-xs font-medium text-dimmed">
                    {{
                      r.arrived
                        ? r.stopPathIndex === 0
                          ? t('vehicles.detail.departed')
                          : t('vehicles.detail.arrived')
                        : r.state === 'passed'
                          ? t('vehicles.detail.wasDue')
                          : t('vehicles.detail.predicted')
                    }}
                  </div>
                  <time
                    class="block text-sm font-semibold leading-4 tabular-nums"
                    :class="adherenceTextClass(r.deltaVsScheduleSec)"
                  >
                    {{ r.eta ? hhmm(r.eta) : '—' }}
                  </time>
                  <span
                    v-if="r.eta && !r.arrived && r.state !== 'passed'"
                    class="block text-xs leading-4 text-muted"
                  >
                    {{ relative(r.eta) }}
                  </span>
                </div>

                <div class="min-w-0 lg:text-right">
                  <template v-if="r.deltaVsScheduleSec != null">
                    <div
                      class="mt-0.5 text-sm font-semibold leading-4 tabular-nums"
                      :class="adherenceTextClass(r.deltaVsScheduleSec)"
                    >
                      {{ signedAdherence(r.deltaVsScheduleSec) }}
                    </div>
                    <div v-if="!r.arrived" class="text-xs leading-4 text-muted">
                      {{
                        r.arrived
                          ? t('vehicles.detail.actualVsSchedule')
                          : t('vehicles.detail.noComputation')
                      }}
                    </div>
                  </template>
                  <span v-else class="text-xs text-dimmed">{{
                    t('vehicles.detail.noScheduleComparison')
                  }}</span>
                  <span
                    v-if="r.errorVsPredictionSec != null"
                    class="block text-xs leading-4 text-red-500"
                  >
                    {{
                      t('vehicles.detail.vsPrediction', {
                        value: signedAdherence(r.errorVsPredictionSec),
                      })
                    }}
                  </span>
                </div>
              </li>
            </ol>
          </section>
        </template>

        <template #resize-handle>
          <SplitterGrip :orientation="pageSplitterOrientation" />
        </template>
      </USplitter>
    </template>

    <VehicleAvlLog
      v-if="selectedAvlFeedCode"
      v-model:open="avlLogOpen"
      :rows="logRows"
      :loading="logLoading"
      :error="logError"
    />
  </AppPage>
</template>
