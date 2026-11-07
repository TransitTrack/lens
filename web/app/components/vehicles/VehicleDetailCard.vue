<script setup lang="ts">
import { useAvlTrailQuery, useVehicleDetailQuery } from '~/composables/useRestRealtime'
import { useAgenciesDetailQuery } from '~/composables/useRestGtfs'
import { formatClock, formatDottedTimestamp } from '~/utils/clock'
import type { VehicleRow } from '~/composables/useVehiclePolling'
import { adherenceBadge } from '~/utils/adherence'
import { coarseAgeLabel, haversineM } from '~/utils/vehicleTelemetry'
import { algorithmsIn, preferredAlgorithm, stopRows } from '~/utils/vehicleDetail'
import { usePollControl } from '~/composables/usePollControl'
import { useFeeds } from '~/composables/useFeeds'
import { useUnits } from '~/composables/useUnits'


/**
 * Card for the vehicle selected on the overview map: summary facts plus
 * the stop-by-stop timeline with arrival estimates, so nothing here needs a page
 * change. Rendered inside the map popup anchored to the vehicle.
 */
const props = defineProps<{
  vehicle: VehicleRow
  feedCode?: string | null
}>()
const emit = defineEmits<{
  close: []
  viewTrip: [tripId: string]
  viewBlock: [block: { blockId: string; serviceId: string }]
  viewRoute: [routeId: string]
  viewVehicle: [vehicleId: string]
}>()

const { t, locale } = useI18n()
const { intervalMs } = usePollControl()
const { selectedFeed } = useFeeds()
const units = useUnits()

const vehicleId = computed(() => props.vehicle.vehicleId)
const collapsed = ref(false)

const { result: detailResult, loading: detailLoading } = useVehicleDetailQuery(() => ({
  variables: { feedCode: props.feedCode ?? '', vehicleId: vehicleId.value },
  enabled: !!props.feedCode, pollInterval: intervalMs.value,
}))
const detail = computed(() => {
  const v = detailResult.value?.vehicle
  return v?.vehicleId === vehicleId.value ? v : null
})

const { result: agenciesResult } = useAgenciesDetailQuery(() => ({
  variables: { feedCode: props.feedCode ?? '' },
  enabled: !!props.feedCode,
}))
const agencyTimezone = computed(
  () => agenciesResult.value?.agencies.find((agency) => agency.agencyTimezone)?.agencyTimezone,
)

const { result: trailResult } = useAvlTrailQuery(() => ({
  variables: { feedCode: props.feedCode ?? '', vehicleId: vehicleId.value, limit: 2 },
  enabled: !!props.feedCode, pollInterval: intervalMs.value,
}))

const trip = computed(() => detail.value?.trip ?? props.vehicle.trip)
const route = computed(() => trip.value?.route)
const tripId = computed(() => trip.value?.tripId ?? null)
const routeId = computed(() => detail.value?.trip?.routeId ?? props.vehicle.trip?.routeId ?? null)
const blockId = computed(() => detail.value?.block?.blockId ?? props.vehicle.block?.blockId ?? null)
const serviceId = computed(
  () => detail.value?.block?.serviceId ?? props.vehicle.block?.serviceId ?? null,
)

function handleViewBlock() {
  if (blockId.value && serviceId.value)
    emit('viewBlock', { blockId: blockId.value, serviceId: serviceId.value })
}

const estimatedSpeedMps = computed(() => {
  if (detail.value?.speedMps != null || props.vehicle.speedMps != null) return null
  // avlReports are newest-first. A position-based estimate is only useful
  // over a short, valid interval; otherwise GPS drift can look like movement.
  const [latest, previous] = trailResult.value?.avlReports ?? []
  if (
    !latest ||
    !previous ||
    latest.vehicleId !== vehicleId.value ||
    previous.vehicleId !== vehicleId.value
  )
    return null
  const elapsedSec = (Date.parse(latest.ts) - Date.parse(previous.ts)) / 1000
  const distanceM = haversineM(latest.position, previous.position)
  if (!Number.isFinite(elapsedSec) || elapsedSec < 5 || elapsedSec > 300 || distanceM == null)
    return null
  const speedMps = distanceM / elapsedSec
  return speedMps <= 45 ? speedMps : null
})

const speed = computed(() => {
  const reportedMps = detail.value?.speedMps ?? props.vehicle.speedMps
  if (reportedMps != null) return { mps: reportedMps, estimated: false }
  return estimatedSpeedMps.value == null ? null : { mps: estimatedSpeedMps.value, estimated: true }
})

// Same convention as the vehicle-detail page's "GPS offset": the perpendicular
// gap between the raw report and the matched-shape snap.
const gpsOffsetM = computed(() => haversineM(detail.value?.position, detail.value?.snappedPosition))

const tripProgress = computed(() => {
  const totalM = detail.value?.pattern?.lengthM
  const distanceAlongM = detail.value?.distanceAlongTripM
  if (totalM == null || totalM <= 0 || distanceAlongM == null) return null
  const travelledM = Math.min(totalM, Math.max(0, distanceAlongM))
  return {
    percentage: Math.round((travelledM / totalM) * 100),
    travelledKm: travelledM / 1000,
    totalKm: totalM / 1000,
    remainingKm: Math.max(0, totalM - travelledM) / 1000,
  }
})

const algorithm = computed(() =>
  preferredAlgorithm(
    algorithmsIn(detailResult.value?.vehiclePredictions ?? []),
    selectedFeed.value?.predictionAlgorithms ?? [],
  ),
)
const stops = computed(() =>
  detail.value
    ? stopRows(detail.value, detailResult.value?.vehiclePredictions ?? [], algorithm.value)
    : [],
)
const nextStop = computed(() => stops.value.find((stop) => stop.state === 'current') ?? null)

function stopTime(iso: string | null): string {
  return iso ? formatClock(iso, { timeZone: agencyTimezone.value, locale: locale.value }) : '—'
}

function delta(sec: number | null): string | null {
  if (sec == null) return null
  const minutes = Math.round(sec / 60)
  if (minutes === 0) return '±0 min'
  return minutes > 0 ? `+${minutes} min` : `${minutes} min`
}

// Keep the next stop in view as the vehicle advances and on first open.
const stopList = ref<HTMLElement | null>(null)
watch(
  [() => nextStop.value?.stopPathIndex, collapsed],
  async () => {
    await nextTick()
    // Scroll only the list itself: `scrollIntoView` would also scroll the card's
    // clipped wrapper and push its header out of view.
    const list = stopList.value
    const current = list?.querySelector<HTMLElement>('[data-current="true"]')
    if (!list || !current) return
    list.scrollTo({
      top: current.offsetTop - list.clientHeight / 2 + current.offsetHeight / 2,
      behavior: 'smooth',
    })
  },
  { flush: 'post' },
)

function clockTime(seconds: number | null | undefined): string {
  if (seconds == null || seconds < 0) return '—'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = Math.floor(seconds % 60)
  return [hours, minutes, secs].map((value) => String(value).padStart(2, '0')).join(':')
}

function updateAge(iso: string): string {
  const seconds = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 1000))
  return coarseAgeLabel(seconds, t)
}

function updateTimestamp(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return t('vehicles.unknown')
  return formatDottedTimestamp(date, locale.value)
}

const routeBadgeColor = computed(() => {
  const color = route.value?.routeColor
  return color ? `#${color.replace('#', '')}` : undefined
})
const adherence = computed(() => adherenceBadge((detail.value ?? props.vehicle).scheduleAdherenceSec, t))

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') emit('close')
}
onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <aside
    data-testid="vehicle-card"
    :aria-label="$t('vehicles.popup.cardLabel', { id: vehicle.vehicleId })"
    class="flex max-h-[min(36rem,calc(100vh-6rem))] w-80 max-w-[calc(100vw-3rem)] flex-col overflow-hidden text-sm"
  >
    <header class="flex items-start gap-2 p-3" :class="{ 'border-b border-default': !collapsed }">
      <div class="min-w-0 flex-1">
        <p class="flex items-center gap-2">
          <span class="text-lg font-bold text-highlighted">{{ vehicle.vehicleId }}</span>
          <UBadge v-if="route?.routeShortName" size="sm" :style="{ backgroundColor: routeBadgeColor }">
            {{ route.routeShortName }}
          </UBadge>
        </p>
        <p v-if="trip?.tripHeadsign" class="truncate text-xs text-muted">{{ trip.tripHeadsign }}</p>
        <p v-if="collapsed && nextStop" class="truncate text-xs text-muted">
          {{ $t('vehicles.field.nextStop') }}: {{ nextStop.stopName }} · {{ stopTime(nextStop.eta) }}
        </p>
      </div>
      <UButton
        color="neutral"
        variant="ghost"
        size="xs"
        :icon="collapsed ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
        :aria-label="collapsed ? $t('vehicles.popup.expand') : $t('vehicles.popup.collapse')"
        :aria-expanded="!collapsed"
        @click="collapsed = !collapsed"
      />
      <UButton
        color="neutral"
        variant="ghost"
        size="xs"
        icon="i-lucide-x"
        :aria-label="$t('vehicles.popup.close')"
        @click="emit('close')"
      />
    </header>

    <div v-show="!collapsed" class="flex min-h-0 flex-1 flex-col gap-3 overflow-hidden p-3">
      <div v-if="detailLoading && !detail" class="flex items-center gap-2 py-4 text-muted">
        <UIcon name="i-lucide-loader-circle" class="size-4 animate-spin" />
        {{ $t('vehicles.popup.loading') }}
      </div>

      <template v-else-if="detail">
        <section class="flex min-h-0 flex-1 flex-col gap-1.5">
          <div class="flex shrink-0 items-center justify-between gap-3">
            <h3 class="font-semibold text-highlighted">{{ $t('vehicles.popup.stops') }}</h3>
            <UBadge :color="adherence.color" variant="subtle">{{ adherence.label }}</UBadge>
          </div>
          <p v-if="stops.length === 0" class="shrink-0 text-muted">{{ $t('vehicles.popup.noStops') }}</p>
          <ol v-else ref="stopList" class="relative min-h-20 flex-1 overflow-y-auto pr-1 [scrollbar-gutter:stable]">
            <li
              v-for="stop in stops"
              :key="stop.stopPathIndex"
              :data-current="stop.state === 'current'"
              class="flex items-center gap-2 rounded px-1.5 py-1"
              :class="[
                stop.state === 'passed' ? 'text-dimmed' : 'text-default',
                stop.state === 'current' ? 'bg-primary/10 font-semibold text-highlighted' : '',
              ]"
            >
              <span
                class="size-2 shrink-0 rounded-full"
                :class="
                  stop.state === 'current'
                    ? 'bg-primary ring-2 ring-primary/30'
                    : stop.state === 'passed'
                      ? 'bg-muted'
                      : 'border border-primary'
                "
              />
              <span class="min-w-0 flex-1 truncate">{{ stop.stopName }}</span>
              <span
                v-if="delta(stop.deltaVsScheduleSec) && stop.state !== 'passed'"
                class="text-xs tabular-nums text-muted"
              >
                {{ delta(stop.deltaVsScheduleSec) }}
              </span>
              <span class="tabular-nums">{{ stopTime(stop.eta) }}</span>
            </li>
          </ol>
        </section>

        <section v-if="tripProgress" class="shrink-0 space-y-1">
          <div class="flex justify-between gap-3 text-xs">
            <span class="font-semibold text-highlighted">{{
              $t('vehicles.popup.progress', { percent: tripProgress.percentage })
            }}</span>
            <span class="tabular-nums text-muted"
              >{{ tripProgress.travelledKm.toFixed(1) }} / {{ tripProgress.totalKm.toFixed(1) }} km</span
            >
          </div>
          <div class="h-2 overflow-hidden rounded-full bg-elevated ring-1 ring-inset ring-default">
            <div class="h-full bg-primary" :style="{ width: `${tripProgress.percentage}%` }" />
          </div>
          <p class="text-right text-xs tabular-nums text-muted">
            {{ $t('vehicles.popup.remaining', { distance: tripProgress.remainingKm.toFixed(1) }) }}
          </p>
        </section>

        <section class="shrink-0 border-t border-dashed border-default pt-2.5">
          <dl class="space-y-1.5">
            <div v-if="detail.block" class="flex justify-between gap-3">
              <dt class="text-muted">{{ $t('vehicles.field.block') }}</dt>
              <dd class="text-right font-semibold tabular-nums text-highlighted">
                {{ detail.block.blockId }}
                <span
                  v-if="detail.block.startTimeSec != null || detail.block.endTimeSec != null"
                  class="ml-1 text-xs font-medium text-muted"
                  >{{ clockTime(detail.block.startTimeSec) }}–{{ clockTime(detail.block.endTimeSec) }}</span
                >
              </dd>
            </div>
            <div v-if="detail.trip" class="flex justify-between gap-3">
              <dt class="text-muted">{{ $t('vehicles.field.trip') }}</dt>
              <dd class="text-right font-semibold tabular-nums text-highlighted">
                {{ detail.trip.tripId }}
              </dd>
            </div>
            <div v-if="detail.trip" class="flex justify-between gap-3">
              <dt class="text-muted">{{ $t('vehicles.field.scheduled') }}</dt>
              <dd class="text-right font-semibold tabular-nums text-highlighted">
                {{ clockTime(detail.trip.startTimeSec) }}–{{ clockTime(detail.trip.endTimeSec) }}
              </dd>
            </div>
            <div class="flex justify-between gap-3">
              <dt class="text-muted">{{ $t('vehicles.popup.at') }}</dt>
              <dd class="max-w-52 text-right font-semibold text-highlighted">
                {{ detail.currentStop?.stopName ?? $t('vehicles.popup.betweenStops') }}
              </dd>
            </div>
            <div class="flex justify-between gap-3">
              <dt class="text-muted">{{ $t('vehicles.field.speed') }}</dt>
              <dd class="text-right font-semibold tabular-nums text-highlighted">
                {{ speed ? `${Math.round(speed.mps * 3.6)} km/h` : $t('vehicles.popup.unavailable') }}
                <span v-if="speed?.estimated" class="ml-1 text-xs font-medium text-muted">{{
                  $t('vehicles.popup.estimated')
                }}</span>
              </dd>
            </div>
            <div class="flex justify-between gap-3">
              <dt class="text-muted">{{ $t('vehicles.popup.fromRoute') }}</dt>
              <dd class="text-right font-semibold tabular-nums text-highlighted">
                {{
                  gpsOffsetM == null
                    ? '—'
                    : gpsOffsetM <= 15
                      ? $t('vehicles.field.onRoute')
                      : units.distance(gpsOffsetM)
                }}
              </dd>
            </div>
            <div class="flex justify-between gap-3">
              <dt class="text-muted">{{ $t('vehicles.popup.lastUpdate') }}</dt>
              <dd class="text-right font-semibold tabular-nums text-highlighted">
                {{ updateTimestamp(detail.reportTs) }} · {{ updateAge(detail.reportTs) }}
              </dd>
            </div>
          </dl>
        </section>
      </template>

      <div v-else class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
        <UBadge :color="adherence.color" variant="subtle">{{ adherence.label }}</UBadge>
        <span class="text-muted">
          {{ speed ? `${Math.round(speed.mps * 3.6)} km/h` : $t('vehicles.popup.speedUnavailable') }}
          <template v-if="speed?.estimated"> {{ $t('vehicles.popup.estimatedParens') }}</template>
        </span>
        <span v-if="vehicle.stale" class="text-warning">{{ $t('vehicles.popup.stale') }}</span>
      </div>

      <div class="flex shrink-0 gap-1.5">
        <UTooltip :text="$t('vehicles.popup.viewTrip')">
          <UButton
            color="neutral"
            variant="subtle"
            size="sm"
            icon="i-lucide-milestone"
            :aria-label="$t('vehicles.popup.viewTrip')"
            :disabled="!tripId"
            @click="tripId && emit('viewTrip', tripId)"
          />
        </UTooltip>
        <UTooltip :text="$t('vehicles.popup.viewBlock')">
          <UButton
            color="neutral"
            variant="subtle"
            size="sm"
            icon="i-lucide-layers"
            :aria-label="$t('vehicles.popup.viewBlock')"
            :disabled="!blockId || !serviceId"
            @click="handleViewBlock"
          />
        </UTooltip>
        <UTooltip :text="$t('vehicles.popup.viewRoute')">
          <UButton
            color="neutral"
            variant="subtle"
            size="sm"
            icon="i-lucide-route"
            :aria-label="$t('vehicles.popup.viewRoute')"
            :disabled="!routeId"
            @click="routeId && emit('viewRoute', routeId)"
          />
        </UTooltip>
        <UTooltip :text="$t('vehicles.popup.viewVehicle')">
          <UButton
            color="neutral"
            variant="subtle"
            size="sm"
            icon="i-lucide-bus"
            :aria-label="$t('vehicles.popup.viewVehicle')"
            @click="emit('viewVehicle', vehicle.vehicleId)"
          />
        </UTooltip>
      </div>
    </div>
  </aside>
</template>
