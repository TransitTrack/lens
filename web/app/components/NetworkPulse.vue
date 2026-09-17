<script setup lang="ts">
import {formatTimeAgo} from '@vueuse/core'
import type {VehicleRow} from '~/composables/useVehiclePolling'
import NoRealtimeState from '~/components/NoRealtimeState.vue'
import VehicleMap from '~/components/VehicleMap.vue'
import {buildNetworkPulseSignals, type NetworkPulseSignal} from '~/utils/networkPulse'
import type {AvlFeedsQuery} from '~~/generated/graphql'

type AvlFeedHealth = Pick<AvlFeedsQuery['avlFeeds'][number], 'enabled' | 'lastPollStatus'>

const props = defineProps<{
  vehicles: VehicleRow[]
  loading: boolean
  error: Error | null
  hasRealtimeSource: boolean
  feedCode: string | null
  updatedAt: string | null
  /** Feed-health data from the existing AVL feeds query. */
  feeds: AvlFeedHealth[]
}>()

const emit = defineEmits<{selectVehicle: [vehicleId: string]}>()

const showSkeleton = computed(() => props.loading && props.vehicles.length === 0)
const signals = computed(() => buildNetworkPulseSignals(props.vehicles, props.feeds))

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
</script>

<template>
  <section
    class="network-pulse dashboard-enter overflow-hidden rounded-xl border border-default bg-default shadow-sm"
    aria-labelledby="network-pulse-heading"
  >
    <header class="network-pulse__header flex flex-col gap-3 border-b border-default px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-5">
      <div>
        <p class="text-xs font-semibold uppercase tracking-[0.18em] text-primary">Operations overview</p>
        <h2 id="network-pulse-heading" class="mt-1 text-2xl font-semibold text-highlighted">Network pulse</h2>
      </div>
      <div class="flex items-center gap-2 text-sm text-muted">
        <span class="dashboard-live-dot size-2 rounded-full bg-success" aria-hidden="true" />
        <span>Live positions updated {{ updatedAt ? formatTimeAgo(new Date(updatedAt)) : '—' }}</span>
      </div>
    </header>

    <div class="network-pulse__body grid min-h-[28rem] lg:grid-cols-[minmax(0,1.7fr)_minmax(18rem,0.8fr)]">
      <NoRealtimeState v-if="!hasRealtimeSource" class="min-h-[28rem] lg:col-span-2" what="the live network" />

      <div v-else-if="error" class="flex min-h-[28rem] items-center p-4 lg:col-span-2">
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
        <div class="network-pulse__map min-h-[20rem] border-b border-default p-4 lg:border-b-0 lg:border-r">
          <USkeleton class="h-full min-h-[18rem] w-full rounded-lg" />
        </div>
        <aside class="network-pulse__signals flex min-h-[20rem] flex-col gap-3 p-4 sm:p-5" aria-label="Operating signals">
          <div v-for="index in 3" :key="index" class="rounded-lg border border-default p-4">
            <USkeleton class="h-4 w-28" />
            <USkeleton class="mt-3 h-7 w-16" />
            <USkeleton class="mt-2 h-3 w-full" />
          </div>
        </aside>
      </template>

      <template v-else>
        <div class="network-pulse__map min-h-[20rem] border-b border-default lg:border-b-0 lg:border-r">
          <VehicleMap :vehicles="vehicles" :feed-code="feedCode" @select="emit('selectVehicle', $event)" />
        </div>

        <aside class="network-pulse__signals flex flex-col gap-3 p-4 sm:p-5" aria-labelledby="network-pulse-signals-heading">
          <div>
            <h3 id="network-pulse-signals-heading" class="text-sm font-semibold text-highlighted">Operating signals</h3>
            <p class="mt-1 text-xs text-muted">Current fleet health from live vehicle reports</p>
          </div>

          <div
            v-for="signal in signals"
            :key="signal.key"
            class="flex gap-3 rounded-lg border border-default bg-elevated/30 p-3.5"
          >
            <div class="flex flex-col items-center gap-1.5 pt-0.5" aria-hidden="true">
              <UIcon :name="signal.icon" class="size-4" :class="iconClass(signal.color)" />
              <span class="size-1.5 rounded-full" :class="indicatorClass(signal.color)" />
            </div>
            <div class="min-w-0">
              <div class="text-sm font-medium text-muted">{{ signal.label }}</div>
              <div class="mt-0.5 text-xl font-semibold text-highlighted">{{ signal.value }}</div>
              <p class="mt-1 text-xs leading-5 text-muted">{{ signal.detail }}</p>
            </div>
          </div>
        </aside>
      </template>
    </div>
  </section>
</template>
