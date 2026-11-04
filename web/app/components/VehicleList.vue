<script setup lang="ts">
import { formatDottedTimestamp } from '~/utils/clock'
import type { DropdownMenuItem } from '@nuxt/ui'
import type { VehicleRow } from '../composables/useVehiclePolling'
import { adherenceBadge, formatAdherence } from '../utils/adherence'
import { useUnits } from '../composables/useUnits'
import { hexColor, routeTextColor } from '~/utils/gtfs'
import { sortVehicles } from '~/utils/vehicleFilters'
import { coarseAgeLabel } from '~/utils/vehicleTelemetry'
import { useFeeds } from '~/composables/useFeeds'
import { useVehicleListPrefs } from '~/composables/useVehicleListPrefs'

const props = defineProps<{
  vehicles: VehicleRow[]
  selectedVehicleId?: string | null
}>()
const emit = defineEmits<{
  (e: 'select' | 'openDetails', vehicleId: string): void
}>()

const { t, locale } = useI18n()
const units = useUnits()
const { feedPath } = useFeeds()
const { showTripDetails } = useVehicleListPrefs()
const descending = ref(false)
const rowElements = new Map<string, HTMLElement>()

interface TripProgress {
  percentage: number
  travelledM: number
  remainingM: number
}

const rows = computed(() => sortVehicles(props.vehicles, descending.value))

function setRowElement(vehicleId: string, element: unknown) {
  if (element instanceof HTMLElement) rowElements.set(vehicleId, element)
  else rowElements.delete(vehicleId)
}

watch(
  () => props.selectedVehicleId,
  (vehicleId) => {
    if (!vehicleId) return
    nextTick(() => {
      rowElements
        .get(vehicleId)
        ?.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' })
    })
  },
)

function adherenceDotClass(vehicle: VehicleRow) {
  const color = adherenceBadge(vehicle.scheduleAdherenceSec, t).color
  return {
    'bg-error': color === 'error',
    'bg-warning': color === 'warning',
    'bg-success': color === 'success',
    'bg-muted': color === 'neutral',
  }
}

function adherenceTextClass(vehicle: VehicleRow) {
  const color = adherenceBadge(vehicle.scheduleAdherenceSec, t).color
  return {
    'text-error': color === 'error',
    'text-warning': color === 'warning',
    'text-success': color === 'success',
    'text-dimmed': color === 'neutral',
  }
}

function tripProgress(vehicle: VehicleRow): TripProgress | null {
  const totalM = vehicle.pattern?.lengthM
  const distanceAlongM = vehicle.distanceAlongTripM
  if (!vehicle.matched || totalM == null || totalM <= 0 || distanceAlongM == null) return null

  const travelledM = Math.min(totalM, Math.max(0, distanceAlongM))
  return {
    percentage: Math.round((travelledM / totalM) * 100),
    travelledM,
    remainingM: Math.max(0, totalM - travelledM),
  }
}

function reportAge(iso: string): string {
  const seconds = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 1000))
  return coarseAgeLabel(seconds, t)
}

function reportTimestamp(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return t('vehicles.unknown')

  return formatDottedTimestamp(date, locale.value)
}

function rowActions(vehicle: VehicleRow): DropdownMenuItem[][] {
  return [
    [
      {
        label: t('vehicles.list.openDetails'),
        onSelect: () => emit('openDetails', vehicle.vehicleId),
      },
    ],
  ]
}
</script>

<template>
  <div class="divide-y divide-default">
    <div
      v-if="false"
      class="grid grid-cols-[auto_minmax(11rem,1fr)_auto_1rem] items-center gap-4 px-5 py-3 text-sm text-dimmed"
    >
      <button
        data-testid="vehicle-sort"
        class="flex w-fit items-center gap-1.5 font-medium text-muted transition-colors hover:text-highlighted"
        @click="descending = !descending"
      >
        {{ $t('vehicles.field.vehicle') }}
        <UIcon :name="descending ? 'i-lucide-arrow-down' : 'i-lucide-arrow-up'" class="size-3.5" />
      </button>
      <span class="hidden sm:block text-left">{{ $t('vehicles.list.tripProgress') }}</span>
      <span>{{ $t('vehicles.field.adherence') }}</span>
      <span />
    </div>

    <div
      v-for="vehicle in rows"
      :key="vehicle.vehicleId"
      :ref="(element) => setRowElement(vehicle.vehicleId, element)"
      :data-testid="`vehicle-row-${vehicle.vehicleId}`"
      role="button"
      tabindex="0"
      class="group flex cursor-pointer w-full items-center gap-2 px-4 py-2 text-left transition-colors hover:bg-elevated focus-visible:bg-elevated focus-visible:outline-none"
      :class="
        vehicle.vehicleId === selectedVehicleId &&
        'bg-primary/10 shadow-[inset_3px_0_0_var(--ui-primary)]'
      "
      @click="emit('select', vehicle.vehicleId)"
      @keydown.enter="emit('select', vehicle.vehicleId)"
      @keydown.space.prevent="emit('select', vehicle.vehicleId)"
    >
      <div class="flex min-w-18 items-center gap-1.5">
        <span
          class="size-2.5 shrink-0 rounded-full ring-1 ring-white"
          :class="adherenceDotClass(vehicle)"
        />
        <div class="min-w-0">
          <UTooltip :delay-duration="0" :ui="{ content: 'h-auto w-70 items-start p-4 text-sm' }">
            <span
              class="block cursor-help truncate text-sm font-semibold text-highlighted decoration-dotted underline decoration-muted underline-offset-4 transition-colors group-hover:decoration-primary"
              :aria-label="$t('vehicles.list.showUpdateDetails')"
            >
              {{ vehicle.vehicleId }}
            </span>
            <template #content>
              <dl class="space-y-1.5 leading-tight">
                <div>
                  <dt class="sr-only">{{ $t('vehicles.field.vehicleId') }}</dt>
                  <dd class="text-lg font-semibold text-highlighted">
                    {{ $t('vehicles.list.idLabel') }}
                    <span class="text-primary">{{
                      $t('vehicles.list.busValue', { id: vehicle.vehicleId })
                    }}</span>
                  </dd>
                </div>
                <div>
                  <dt class="inline font-medium text-muted">{{ $t('vehicles.list.lastUpdate') }}</dt>
                  <dd class="inline font-semibold tabular-nums text-primary">
                    {{ reportTimestamp(vehicle.reportTs) }}
                  </dd>
                </div>
                <div>
                  <dt class="inline font-medium text-muted">{{ $t('vehicles.list.updated') }}</dt>
                  <dd class="inline font-semibold text-primary">
                    {{ reportAge(vehicle.reportTs) }}
                  </dd>
                </div>
              </dl>
            </template>
          </UTooltip>
        </div>
      </div>

      <div class="min-w-34 flex-1">
        <template v-if="tripProgress(vehicle)">
          <div class="flex items-center justify-between gap-2 text-xs">
            <UBadge
              v-if="vehicle.trip?.route"
              size="xs"
              :style="{
                backgroundColor: hexColor(vehicle.trip.route.routeColor),
                color: routeTextColor(
                  hexColor(vehicle.trip.route.routeColor),
                  vehicle.trip.route.routeTextColor,
                ),
              }"
            >
              {{ vehicle.trip.route.routeShortName ?? vehicle.trip.routeId }}
            </UBadge>
            <span class="ml-auto text-xs tabular-nums text-highlighted">
              {{ tripProgress(vehicle)?.percentage }}%
            </span>
          </div>
          <div
            class="mt-1.5 h-2 overflow-hidden rounded-sm bg-elevated dark:bg-gray-700 ring-1 ring-inset ring-default dark:ring-gray-750"
            :aria-label="
              $t('vehicles.list.tripCompleted', { percent: tripProgress(vehicle)?.percentage })
            "
            role="progressbar"
            aria-valuemin="0"
            aria-valuemax="100"
            :aria-valuenow="tripProgress(vehicle)?.percentage"
          >
            <div
              class="h-full bg-primary transition-[width] duration-300 motion-reduce:transition-none"
              :style="{ width: `${tripProgress(vehicle)?.percentage ?? 0}%` }"
            />
          </div>
          <div class="mt-1 flex justify-between gap-2 text-xs tabular-nums text-muted">
            <span>{{
              $t('vehicles.list.complete', {
                distance: units.distance(tripProgress(vehicle)?.travelledM),
              })
            }}</span>
            <span>{{
              $t('vehicles.list.left', {
                distance: units.distance(tripProgress(vehicle)?.remainingM),
              })
            }}</span>
          </div>
          <div
            v-if="showTripDetails && vehicle.trip"
            class="mt-0.5 flex min-w-0 flex-wrap items-center gap-x-1.5 text-xs text-muted"
          >
            <span v-if="vehicle.block">
              {{ $t('vehicles.list.block') }}
              <NuxtLink
                :to="{
                  path: feedPath(`/explore/blocks/${encodeURIComponent(vehicle.block.blockId)}`),
                  query: { service: vehicle.block.serviceId },
                }"
                class="font-mono text-primary hover:underline"
                @click.stop
                >{{ vehicle.block.blockId }}</NuxtLink
              >
            </span>
            <span>
              {{ $t('vehicles.list.trip') }}
              <NuxtLink
                :to="feedPath(`/explore/trips/${encodeURIComponent(vehicle.trip.tripId)}`)"
                class="font-mono text-primary hover:underline"
                @click.stop
                >{{ vehicle.trip.tripId }}</NuxtLink
              >
            </span>
          </div>
        </template>
        <div v-else class="flex items-center gap-2 text-sm text-dimmed">
          <UIcon name="i-lucide-route-off" class="size-4 shrink-0" />
          <span>{{ $t('vehicles.list.noTripProgress') }}</span>
        </div>
      </div>

      <div class="flex flex-col items-end gap-0.5">
        <span class="text-sm font-medium tabular-nums" :class="adherenceTextClass(vehicle)">
          {{ formatAdherence(vehicle.scheduleAdherenceSec, t) }}
        </span>
        <span class="text-xs text-dimmed">{{ units.speed(vehicle.speedMps) }}</span>
      </div>
      <div>
        <UDropdownMenu
          :items="rowActions(vehicle)"
          :content="{ align: 'end', collisionPadding: 12 }"
        >
          <UButton
            size="xs"
            color="neutral"
            variant="soft"
            icon="i-lucide-ellipsis-vertical"
            :aria-label="$t('vehicles.list.actionsFor', { id: vehicle.vehicleId })"
            @click.stop
          />
        </UDropdownMenu>
      </div>
    </div>
  </div>
</template>
