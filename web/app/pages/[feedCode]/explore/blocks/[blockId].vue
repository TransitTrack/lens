<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import BlockTimeline from '~/components/BlockTimeline.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useBlockDetailQuery } from '~~/generated/graphql'
import { hexColor } from '~/utils/gtfs'
import { timeSplit, timelineSegments } from '~/utils/blocks'

const route = useRoute()
const { selectedFeedCode, feedPath } = useFeeds()
const blockId = computed(() => String(route.params.blockId))
const serviceId = computed(() => String(route.query.service ?? ''))

const { result, loading, error } = useBlockDetailQuery(
  () => ({
    feedCode: selectedFeedCode.value ?? '',
    blockId: blockId.value,
    serviceId: serviceId.value,
  }),
  () => ({ enabled: !!selectedFeedCode.value && !!serviceId.value }),
)

const block = computed(() => result.value?.block ?? null)
const trips = computed(() =>
  [...(block.value?.blockTrips ?? [])].sort((a, b) => a.listIndex - b.listIndex),
)

const split = computed(() => timeSplit(trips.value))
const segments = computed(() =>
  block.value
    ? timelineSegments(trips.value, block.value.startTimeSec, block.value.endTimeSec)
    : [],
)

const durationSec = computed(() =>
  block.value ? block.value.endTimeSec - block.value.startTimeSec : 0,
)

function fmtDur(sec: number): string {
  return `${Math.floor(sec / 3600)}h ${Math.round((sec % 3600) / 60)}m`
}

function pct(sec: number): number {
  return durationSec.value ? Math.round((sec / durationSec.value) * 100) : 0
}
</script>

<template>
  <AppPage :title="blockId">
    <template #leading>
      <UButton
        icon="i-lucide-arrow-left"
        color="neutral"
        variant="ghost"
        :to="feedPath('/explore/blocks')"
        aria-label="Back to blocks"
      />
    </template>

    <UAlert
      v-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      title="Block unavailable"
      :description="error.message"
    />
    <UAlert
      v-else-if="!serviceId"
      color="warning"
      variant="soft"
      icon="i-lucide-help-circle"
      title="Missing service"
      description="Open a block from the Blocks list so its service is known."
    />
    <div v-else-if="loading && !block" class="flex flex-col gap-4">
      <USkeleton class="h-24 w-full" />
      <USkeleton class="h-16 w-full" />
      <USkeleton class="h-64 w-full" />
    </div>

    <template v-else-if="block">
      <div class="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
        <div><span class="text-dimmed">Service </span>{{ block.serviceId }}</div>
        <div>
          <span class="text-dimmed">Span </span>
          {{ secToHm(block.startTimeSec) }}–{{ secToHm(block.endTimeSec) }} ({{
            fmtDur(durationSec)
          }})
        </div>
        <div><span class="text-dimmed">Trips </span>{{ block.tripCount }}</div>
      </div>

      <!-- revenue / layover / deadhead split -->
      <div class="flex flex-col gap-1">
        <div class="flex h-3 w-full overflow-hidden rounded-full bg-elevated">
          <div class="bg-primary" :style="{ width: `${pct(split.revenueSec)}%` }" />
          <div class="bg-muted" :style="{ width: `${pct(split.layoverSec)}%` }" />
        </div>
        <div class="flex flex-wrap gap-x-4 text-xs text-muted">
          <span>Revenue {{ fmtDur(split.revenueSec) }} ({{ pct(split.revenueSec) }}%)</span>
          <span>Layover {{ fmtDur(split.layoverSec) }} ({{ pct(split.layoverSec) }}%)</span>
          <span>{{ split.deadheadLegs }} deadhead leg(s)</span>
        </div>
      </div>

      <div class="text-sm font-medium text-muted">Timeline</div>
      <BlockTimeline
        :segments="segments"
        :span-start="block.startTimeSec"
        :span-end="block.endTimeSec"
        :height="72"
      />

      <div class="text-sm font-medium text-muted">Trips</div>
      <ol class="flex flex-col">
        <li
          v-for="bt in trips"
          :key="bt.listIndex"
          class="flex items-center gap-3 border-t border-default py-2 text-sm first:border-t-0"
        >
          <span class="w-5 shrink-0 text-right text-xs text-dimmed">{{ bt.listIndex + 1 }}</span>
          <UBadge
            size="sm"
            :style="{ backgroundColor: hexColor(bt.trip.route?.routeColor, undefined) }"
          >
            {{ bt.trip.route?.routeShortName ?? bt.trip.routeId }}
          </UBadge>
          <NuxtLink
            :to="feedPath(`/explore/trips/${bt.trip.tripId}`)"
            class="min-w-0 flex-1 truncate hover:underline"
          >
            {{ bt.trip.tripHeadsign ?? bt.trip.tripId }}
          </NuxtLink>
          <span class="shrink-0 tabular-nums text-muted">
            {{ secToHm(bt.trip.startTimeSec ?? null) }}–{{ secToHm(bt.trip.endTimeSec ?? null) }}
          </span>
          <span v-if="bt.layoverAfterSec" class="shrink-0 text-xs text-dimmed">
            +{{ Math.round(bt.layoverAfterSec / 60) }}m
          </span>
          <UIcon
            v-if="bt.deadheadAfter"
            name="i-lucide-move-right"
            class="size-3.5 shrink-0 text-warning"
          />
        </li>
      </ol>
    </template>
  </AppPage>
</template>
