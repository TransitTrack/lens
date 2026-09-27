<script setup lang="ts">
import type { VehicleRow } from '../composables/useVehiclePolling'
import { adherenceBadge } from '../utils/adherence'
import { useUnits } from '../composables/useUnits'
import { hexColor, routeTextColor } from '~/utils/gtfs'

const props = defineProps<{ vehicles: VehicleRow[] }>()
const emit = defineEmits<{ (e: 'select', vehicleId: string): void }>()

const units = useUnits()
const descending = ref(false)

const rows = computed(() =>
  [...props.vehicles].sort((a, b) => {
    const order = (a.label ?? a.vehicleId).localeCompare(b.label ?? b.vehicleId, undefined, {
      numeric: true,
    })
    return descending.value ? -order : order
  }),
)

function state(v: VehicleRow): { label: string; color: 'success' | 'warning' | 'neutral' } {
  if (v.stale) return { label: 'Stale', color: 'warning' }
  if (v.matched) return { label: 'Matched', color: 'success' }
  return { label: 'Unmatched', color: 'neutral' }
}

function reportAge(iso: string): string {
  const seconds = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 1000))
  if (seconds < 45) return 'now'
  if (seconds < 3600) return `${Math.round(seconds / 60)}m ago`
  return `${Math.round(seconds / 3600)}h ago`
}
</script>

<template>
  <div class="divide-y divide-default">
    <div
      class="grid grid-cols-[12rem_minmax(12rem,1fr)_auto_auto] items-center gap-5 px-5 py-3 text-sm text-dimmed"
    >
      <button
        data-testid="vehicle-sort"
        class="flex w-fit items-center gap-1.5 font-medium text-muted transition-colors hover:text-highlighted"
        @click="descending = !descending"
      >
        Vehicle
        <UIcon :name="descending ? 'i-lucide-arrow-down' : 'i-lucide-arrow-up'" class="size-3.5" />
      </button>
      <span class="hidden sm:block text-left">Current trip</span>
      <span>Adherence</span>
      <span class="text-right">Report</span>
    </div>

    <button
      v-for="vehicle in rows"
      :key="vehicle.vehicleId"
      :data-testid="`vehicle-row-${vehicle.vehicleId}`"
      class="group flex w-full items-center gap-5 px-3 py-2 text-left transition-colors hover:bg-elevated/70 focus-visible:bg-elevated focus-visible:outline-none"
      @click="emit('select', vehicle.vehicleId)"
    >
      <div class="flex min-w-50 items-center gap-3">
        <span
          class="size-3 shrink-0 rounded-full ring-2 ring-white"
          :class="{
            'bg-success': state(vehicle).color === 'success',
            'bg-warning': state(vehicle).color === 'warning',
            'bg-muted': state(vehicle).color === 'neutral',
          }"
        />
        <div class="min-w-0">
          <div class="truncate text-base font-semibold text-highlighted">
            {{ vehicle.label ?? vehicle.vehicleId }}
          </div>
          <div class="truncate text-sm text-muted">
            {{ vehicle.label ? `Vehicle ${vehicle.vehicleId}` : state(vehicle).label }}
          </div>
        </div>
      </div>

      <div class="min-w-0 items-center gap-2 flex grow">
        <UBadge
          v-if="vehicle.trip?.route"
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
        <span class="truncate text-sm text-muted">{{
          vehicle.trip?.tripHeadsign ?? state(vehicle).label
        }}</span>
      </div>

      <div class="flex flex-col items-end gap-0.5">
        <UBadge :color="adherenceBadge(vehicle.scheduleAdherenceSec).color" variant="subtle">
          {{ adherenceBadge(vehicle.scheduleAdherenceSec).label }}
        </UBadge>
        <span class="text-sm text-dimmed">{{ units.speed(vehicle.speedMps) }}</span>
      </div>

      <div class="flex flex-col items-end min-w-15 gap-0.5">
        <span class="text-sm text-muted">{{ reportAge(vehicle.reportTs) }}</span>
        <UIcon
          name="i-lucide-arrow-up-right"
          class="size-4 text-dimmed transition-transform group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
        />
      </div>
    </button>
  </div>
</template>
