<script setup lang="ts">
import { formatTimeAgo } from '@vueuse/core'
import type { VehicleRow } from '~/composables/useVehiclePolling'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import VehicleMap from '~/components/VehicleMap.vue'
import type { LngLatBoundsExtent } from '~/composables/useFeedExtent'
import {
  buildNetworkPulseSignals,
  type AvlFeedHealth,
  type NetworkPulseSignal,
} from '~/utils/networkPulse'
import type { VehicleStatus } from '~/utils/vehicleFilters'

const props = defineProps<{
  vehicles: VehicleRow[]
  loading: boolean
  error: Error | null
  hasRealtimeSource: boolean
  selectedAvlFeedCode: string | null
  feedCode: string | null
  extent: LngLatBoundsExtent | null
  updatedAt: string | null
  /** Feed-health data from the existing AVL feeds query. */
  feeds: AvlFeedHealth[]
}>()

const emit = defineEmits<{
  selectVehicle: [vehicleId: string]
  viewVehicles: [statuses: VehicleStatus[]]
}>()

const showSkeleton = computed(() => props.loading && props.vehicles.length === 0)
const signals = computed(() =>
  buildNetworkPulseSignals(props.vehicles, props.feeds, props.selectedAvlFeedCode),
)
const hasLivePositions = computed(() => props.hasRealtimeSource && !props.error)

function indicatorClass(color: NetworkPulseSignal['color']): string {
  return {
    success: 'bg-success',
    warning: 'bg-warning',
    neutral: 'bg-neutral',
  }[color]
}

function iconClass(color: NetworkPulseSignal['color']): string {
  return {
    success: 'text-success',
    warning: 'text-warning',
    neutral: 'text-muted',
  }[color]
}

function signalStatuses(signal: NetworkPulseSignal): VehicleStatus[] {
  if (signal.key === 'attention') return ['attention']
  if (signal.key === 'onTime') return ['ontime']
  return []
}

function canViewVehicles(signal: NetworkPulseSignal): boolean {
  return hasLivePositions.value && signal.value !== '—'
}
</script>

<template>
  <section
    class="network-pulse overflow-hidden border border-default bg-default shadow-sm"
    :class="{ 'dashboard-enter': hasLivePositions }"
    aria-labelledby="network-pulse-heading"
  >
    <header
      class="network-pulse__header flex flex-col gap-3 border-b border-default px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-5"
    >
      <div>
        <p class="text-xs font-semibold uppercase tracking-[0.18em] text-primary">
          Operations overview
        </p>
        <h2 id="network-pulse-heading" class="mt-1 text-2xl font-semibold text-highlighted">
          Network pulse
        </h2>
      </div>
      <div class="flex items-center gap-2 text-sm text-muted">
        <template v-if="hasLivePositions">
          <span class="dashboard-live-dot size-2 rounded-full bg-success" aria-hidden="true" />
          <span
            >Live positions updated {{ updatedAt ? formatTimeAgo(new Date(updatedAt)) : '—' }}</span
          >
        </template>
        <template v-else>
          <span class="size-2 rounded-full bg-neutral" aria-hidden="true" />
          <span>Live positions unavailable</span>
        </template>
      </div>
    </header>

    <div
      class="network-pulse__body grid min-h-112 lg:grid-cols-[minmax(0,1.7fr)_minmax(18rem,0.8fr)]"
    >
      <NoRealtimeState
        v-if="!hasRealtimeSource"
        class="min-h-112 lg:col-span-2"
        what="the live network"
      />

      <div v-else-if="error" class="flex min-h-112 items-center p-4 lg:col-span-2">
        <UAlert
          class="w-full"
          color="error"
          variant="soft"
          icon="i-lucide-alert-triangle"
          title="Vehicle feed unavailable"
          :description="error.message"
        />
      </div>

      <template v-else-if="showSkeleton">
        <div
          class="network-pulse__map min-h-80 border-b border-default p-4 lg:border-b-0 lg:border-r"
        >
          <USkeleton class="h-full min-h-72 w-full rounded-lg" />
        </div>
        <aside
          class="network-pulse__signals flex min-h-80 flex-col gap-3 p-4 sm:p-5"
          aria-label="Operating signals"
        >
          <div v-for="index in 3" :key="index" class="rounded-lg border border-default p-4">
            <USkeleton class="h-4 w-28" />
            <USkeleton class="mt-3 h-7 w-16" />
            <USkeleton class="mt-2 h-3 w-full" />
          </div>
        </aside>
      </template>

      <template v-else>
        <div class="network-pulse__map min-h-80 border-b border-default lg:border-b-0 lg:border-r">
          <VehicleMap
            :vehicles="vehicles"
            :extent="extent"
            :feed-code="feedCode"
            @select="emit('selectVehicle', $event)"
          />
        </div>

        <aside
          class="network-pulse__signals flex flex-col gap-3 p-4 sm:p-5"
          aria-labelledby="network-pulse-signals-heading"
        >
          <div>
            <h3 id="network-pulse-signals-heading" class="text-sm font-semibold text-highlighted">
              Operating signals
            </h3>
            <p class="mt-1 text-xs text-muted">Current fleet health from live vehicle reports</p>
          </div>

          <button
            v-for="(signal, index) in signals"
            :key="signal.key"
            type="button"
            class="dashboard-signal group flex w-full gap-3 rounded-lg border border-default bg-elevated/30 p-3.5 text-left transition hover:border-primary/50 hover:bg-elevated/60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-default disabled:hover:border-default disabled:hover:bg-elevated/30"
            :style="{ '--dashboard-signal-delay': `${index * 45}ms` }"
            :disabled="!canViewVehicles(signal)"
            :aria-label="
              canViewVehicles(signal)
                ? `View ${signal.label.toLocaleLowerCase()} vehicles`
                : undefined
            "
            @click="emit('viewVehicles', signalStatuses(signal))"
          >
            <div class="flex flex-col items-center gap-1.5 pt-0.5" aria-hidden="true">
              <UIcon :name="signal.icon" class="size-4" :class="iconClass(signal.color)" />
              <span class="size-1.5 rounded-full" :class="indicatorClass(signal.color)" />
            </div>
            <div class="min-w-0 flex-1">
              <div class="flex items-center justify-between gap-2 text-sm font-medium text-muted">
                {{ signal.label }}
                <UIcon
                  v-if="canViewVehicles(signal)"
                  name="i-lucide-arrow-up-right"
                  class="size-3.5 shrink-0 opacity-0 transition group-hover:opacity-100 group-focus-visible:opacity-100"
                  aria-hidden="true"
                />
              </div>
              <div class="mt-0.5 text-xl font-semibold text-highlighted">
                <span :key="`${signal.key}:${signal.value}`" class="dashboard-signal__value">
                  {{ signal.value }}
                </span>
              </div>
              <p class="mt-1 text-xs leading-5 text-muted">{{ signal.detail }}</p>
            </div>
          </button>
        </aside>
      </template>
    </div>
  </section>
</template>
