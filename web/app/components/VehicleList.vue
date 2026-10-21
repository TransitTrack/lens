<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import type { VehicleRow } from '../composables/useVehiclePolling'
import { adherenceBadge, formatAdherence } from '../utils/adherence'
import { useUnits } from '../composables/useUnits'
import { hexColor, routeTextColor } from '~/utils/gtfs'

const props = defineProps<{
  vehicles: VehicleRow[]
  selectedVehicleId?: string | null
}>()
const emit = defineEmits<{
  (e: 'select' | 'openDetails', vehicleId: string): void
}>()

const units = useUnits()
const descending = ref(false)
const rowElements = new Map<string, HTMLElement>()

interface TripProgress {
  percentage: number
  travelledM: number
  remainingM: number
}

const rows = computed(() =>
  [...props.vehicles].sort((a, b) => {
    const order = (a.label ?? a.vehicleId).localeCompare(b.label ?? b.vehicleId, undefined, {
      numeric: true,
    })
    return descending.value ? -order : order
  }),
)

function setRowElement(vehicleId: string, element: unknown) {
  if (element instanceof HTMLElement) rowElements.set(vehicleId, element)
  else rowElements.delete(vehicleId)
}

watch(
  () => props.selectedVehicleId,
  (vehicleId) => {
    if (!vehicleId) return
    nextTick(() => {
      rowElements.get(vehicleId)?.scrollIntoView({behavior: 'smooth', block: 'nearest', inline: 'nearest'})
    })
  },
)

function adherenceDotClass(vehicle: VehicleRow) {
  const color = adherenceBadge(vehicle.scheduleAdherenceSec).color
  return {
    'bg-error': color === 'error',
    'bg-warning': color === 'warning',
    'bg-success': color === 'success',
    'bg-muted': color === 'neutral',
  }
}

function adherenceTextClass(vehicle: VehicleRow) {
  const color = adherenceBadge(vehicle.scheduleAdherenceSec).color
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
  if (seconds < 45) return 'a few seconds ago'
  if (seconds < 3600) return `${Math.round(seconds / 60)}m ago`
  return `${Math.round(seconds / 3600)}h ago`
}

function reportTimestamp(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return 'Unknown'

  const parts = new Intl.DateTimeFormat('en-GB', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(date)
  const value = (type: Intl.DateTimeFormatPartTypes) => parts.find((part) => part.type === type)?.value ?? ''

  return `${value('day')}.${value('month')}.${value('year')}, ${value('hour')}:${value('minute')}:${value('second')}`
}

function rowActions(vehicle: VehicleRow): DropdownMenuItem[][] {
  return [[
    {
      label: 'Open details',
      onSelect: () => emit('openDetails', vehicle.vehicleId),
    },
  ]]
}
</script>

<template>
  <div class="divide-y divide-default">
    <div
      class="grid grid-cols-[auto_minmax(11rem,1fr)_auto_1rem] items-center gap-5 px-5 py-3 text-sm text-dimmed"
    >
      <button
        data-testid="vehicle-sort"
        class="flex w-fit items-center gap-1.5 font-medium text-muted transition-colors hover:text-highlighted"
        @click="descending = !descending"
      >
        Vehicle
        <UIcon :name="descending ? 'i-lucide-arrow-down' : 'i-lucide-arrow-up'" class="size-3.5" />
      </button>
      <span class="hidden sm:block text-left">Trip progress</span>
      <span>Adherence</span>
      <span />
    </div>

    <div
      v-for="vehicle in rows"
      :key="vehicle.vehicleId"
      :ref="(element) => setRowElement(vehicle.vehicleId, element)"
      :data-testid="`vehicle-row-${vehicle.vehicleId}`"
      role="button"
      tabindex="0"
      class="group flex w-full items-center gap-5 px-3 py-2 text-left transition-colors hover:bg-elevated/70 focus-visible:bg-elevated focus-visible:outline-none"
      :class="vehicle.vehicleId === selectedVehicleId && 'bg-primary/10 shadow-[inset_3px_0_0_var(--ui-primary)]'"
      @click="emit('select', vehicle.vehicleId)"
      @keydown.enter="emit('select', vehicle.vehicleId)"
      @keydown.space.prevent="emit('select', vehicle.vehicleId)"
    >
      <div class="flex min-w-20 items-center gap-3">
        <span
          class="size-3 shrink-0 rounded-full ring-2 ring-white"
          :class="adherenceDotClass(vehicle)"
        />
        <div class="min-w-0">
          <UTooltip :delay-duration="0" :ui="{ content: 'h-auto w-70 items-start p-4 text-sm' }">
            <span
              class="block cursor-help truncate text-base font-semibold text-highlighted decoration-dotted underline decoration-muted underline-offset-4 transition-colors group-hover:decoration-primary"
              aria-label="Show vehicle update details"
            >
              #{{ vehicle.vehicleId }}
            </span>
            <template #content>
              <dl class="space-y-1.5 leading-tight">
                <div>
                  <dt class="sr-only">Vehicle ID</dt>
                  <dd class="text-lg font-semibold text-highlighted">
                    Id: <span class="text-primary">{{ vehicle.vehicleId }} (Bus)</span>
                  </dd>
                </div>
                <div>
                  <dt class="inline font-medium text-muted">Last update: </dt>
                  <dd class="inline font-semibold tabular-nums text-primary">{{ reportTimestamp(vehicle.reportTs) }}</dd>
                </div>
                <div>
                  <dt class="inline font-medium text-muted">Updated: </dt>
                  <dd class="inline font-semibold text-primary">{{ reportAge(vehicle.reportTs) }}</dd>
                </div>
              </dl>
            </template>
          </UTooltip>
        </div>
      </div>

      <div class="min-w-[11rem] flex-1">
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
            <span class="ml-auto font-semibold tabular-nums text-highlighted">
              {{ tripProgress(vehicle)?.percentage }}%
            </span>
          </div>
          <div
            class="mt-1.5 h-2 overflow-hidden rounded-sm bg-elevated ring-1 ring-inset ring-default"
            :aria-label="`${tripProgress(vehicle)?.percentage}% of trip completed`"
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
            <span>{{ units.distance(tripProgress(vehicle)?.travelledM) }} complete</span>
            <span>{{ units.distance(tripProgress(vehicle)?.remainingM) }} left</span>
          </div>
        </template>
        <div v-else class="flex items-center gap-2 text-sm text-dimmed">
          <UIcon name="i-lucide-route-off" class="size-4 shrink-0" />
          <span>No trip progress</span>
        </div>
      </div>

      <div class="flex flex-col items-end gap-0.5">
        <span class="text-sm font-medium tabular-nums" :class="adherenceTextClass(vehicle)">
          {{ formatAdherence(vehicle.scheduleAdherenceSec) }}
        </span>
        <span class="text-sm text-dimmed">{{ units.speed(vehicle.speedMps) }}</span>
      </div>
      <div>
        <UDropdownMenu
          :items="rowActions(vehicle)"
          :content="{ align: 'end', collisionPadding: 12 }"
        >
          <UButton
            size="xs"
            color="primary"
            variant="soft"
            icon="i-lucide-ellipsis-vertical"
            :aria-label="`Actions for vehicle ${vehicle.vehicleId}`"
            @click.stop
          />
        </UDropdownMenu>
      </div>
    </div>
  </div>
</template>
