<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import BlockTimeline from '~/components/BlockTimeline.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useBlockDetailQuery } from '~~/generated/graphql'
import { hexColor, routeTextColor } from '~/utils/gtfs'
import { layoverLabel, timeSplit, timelineSegments } from '~/utils/blocks'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { selectedFeedCode, feedPath } = useFeeds()
const blockId = computed(() => String(route.params.blockId))
const serviceId = computed(() => String(route.query.service ?? ''))
const timelineOpen = ref(false)

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
  return t('explore.duration', { hours: Math.floor(sec / 3600), minutes: Math.round((sec % 3600) / 60) })
}

function pct(sec: number): number {
  return durationSec.value ? Math.round((sec / durationSec.value) * 100) : 0
}
</script>

<template>
  <AppPage
    :title="t('explore.block.title', { id: blockId })"
    :description="t('explore.block.description')"
  >
    <template #leading>
      <UButton
        icon="i-lucide-arrow-left"
        color="neutral"
        variant="ghost"
        :aria-label="t('common.goBack')"
        @click="router.back()"
      />
    </template>

    <UAlert
      v-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      :title="t('explore.block.unavailable')"
      :description="error.message"
    />
    <UAlert
      v-else-if="!serviceId"
      color="warning"
      variant="soft"
      icon="i-lucide-help-circle"
      :title="t('explore.block.missingService')"
      :description="t('explore.block.missingServiceBody')"
    />
    <div v-else-if="loading && !block" class="flex flex-col gap-4">
      <USkeleton class="h-24 w-full" />
      <USkeleton class="h-16 w-full" />
      <USkeleton class="h-64 w-full" />
    </div>

    <template v-else-if="block">
      <section class="grid sm:grid-cols-2 xl:grid-cols-4" :aria-label="t('explore.block.summary')">
        <div class="border border-default bg-elevated/35 p-4">
          <div
            class="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-dimmed"
          >
            <UIcon name="i-lucide-calendar-days" class="size-3.5" /> {{ t('explore.col.service') }}
          </div>
          <p class="mt-2 truncate font-mono text-lg font-semibold text-highlighted">
            {{ block.serviceId }}
          </p>
        </div>
        <div class="border border-default bg-elevated/35 p-4">
          <div
            class="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-dimmed"
          >
            <UIcon name="i-lucide-clock-3" class="size-3.5" /> {{ t('explore.block.dutySpan') }}
          </div>
          <p class="mt-2 text-lg font-semibold tabular-nums text-highlighted">
            {{ fmtDur(durationSec) }}
          </p>
          <p class="mt-0.5 text-xs tabular-nums text-muted">
            {{ secToHm(block.startTimeSec) }}–{{ secToHm(block.endTimeSec) }}
          </p>
        </div>
        <div class="border border-default bg-elevated/35 p-4">
          <div
            class="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-dimmed"
          >
            <UIcon name="i-lucide-route" class="size-3.5" /> {{ t('explore.block.scheduledTrips') }}
          </div>
          <p class="mt-2 text-lg font-semibold text-highlighted">{{ block.tripCount }}</p>
          <p class="mt-0.5 text-xs text-muted">{{ t('explore.block.revenueLegs') }}</p>
        </div>
        <div class="border border-default bg-elevated/35 p-4">
          <div
            class="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-dimmed"
          >
            <UIcon name="i-lucide-arrow-right-left" class="size-3.5" /> {{ t('explore.block.deadheadMoves') }}
          </div>
          <p class="mt-2 text-lg font-semibold text-highlighted">{{ split.deadheadLegs }}</p>
          <p class="mt-0.5 text-xs text-muted">{{ t('explore.block.betweenTrips') }}</p>
        </div>
      </section>

      <section class="border border-default bg-elevated/35 p-4" aria-labelledby="duty-composition">
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h2 id="duty-composition" class="text-sm font-semibold text-highlighted">
              {{ t('explore.block.dutyComposition') }}
            </h2>
            <p class="mt-0.5 text-xs text-dimmed">{{ t('explore.block.howAllocated') }}</p>
          </div>
          <span class="text-xs text-muted">{{ t('explore.block.inService', { percent: pct(split.revenueSec) }) }}</span>
        </div>
        <div class="mt-4 flex h-3 w-full overflow-hidden rounded-full bg-muted/30">
          <div class="bg-primary" :style="{ width: `${pct(split.revenueSec)}%` }" />
          <div class="bg-warning/80" :style="{ width: `${pct(split.layoverSec)}%` }" />
        </div>
        <div class="mt-3 grid gap-3 text-sm sm:grid-cols-2">
          <div class="rounded-md bg-default/50 px-3 py-2">
            <p class="text-xs text-dimmed">{{ t('explore.block.revenueService') }}</p>
            <p class="mt-1 font-medium tabular-nums text-highlighted">
              {{ fmtDur(split.revenueSec) }}
              <span class="font-normal text-muted">· {{ pct(split.revenueSec) }}%</span>
            </p>
          </div>
          <div class="rounded-md bg-default/50 px-3 py-2">
            <p class="text-xs text-dimmed">{{ t('explore.block.layoverRecovery') }}</p>
            <p class="mt-1 font-medium tabular-nums text-highlighted">
              {{ fmtDur(split.layoverSec) }}
              <span class="font-normal text-muted">· {{ pct(split.layoverSec) }}%</span>
            </p>
          </div>
        </div>
      </section>

      <section
        class="overflow-scroll border border-default bg-elevated/35"
        aria-labelledby="trip-sequence"
      >
        <div class="flex items-center justify-between border-b border-default px-4 py-3">
          <div>
            <h2 id="trip-sequence" class="text-sm font-semibold text-highlighted">
              {{ t('explore.block.tripSequence') }}
            </h2>
            <p class="mt-0.5 text-xs text-dimmed">{{ t('explore.block.orderedAs') }}</p>
          </div>
          <div class="flex items-center gap-3">
            <span class="hidden text-xs text-muted sm:inline">{{
              t('explore.count.legs', { count: trips.length }, trips.length)
            }}</span>
            <UButton
              icon="i-lucide-gantt-chart"
              :label="t('explore.block.viewTimeline')"
              color="neutral"
              variant="soft"
              size="sm"
              @click="timelineOpen = true"
            />
          </div>
        </div>
        <ol class="divide-y divide-default px-4">
          <li
            v-for="bt in trips"
            :key="bt.listIndex"
            class="grid grid-cols-[1.5rem_auto_minmax(0,1fr)_auto] items-center gap-x-3 gap-y-1 py-3 text-sm sm:grid-cols-[1.75rem_auto_minmax(0,1fr)_auto_auto]"
          >
            <span
              class="row-span-2 flex size-6 items-center justify-center rounded-full bg-muted/60 text-xs font-semibold text-muted"
              >{{ bt.listIndex + 1 }}</span
            >
            <UBadge
              :style="{
                backgroundColor: hexColor(bt.trip.route?.routeColor),
                color: routeTextColor(
                  hexColor(bt.trip.route?.routeColor),
                  bt.trip.route?.routeTextColor,
                ),
              }"
              >{{ bt.trip.route?.routeShortName ?? bt.trip.routeId }}</UBadge
            >
            <NuxtLink
              :to="feedPath(`/explore/trips/${bt.trip.tripId}`)"
              class="min-w-0 truncate font-medium text-highlighted hover:text-primary hover:underline"
              >{{ bt.trip.tripHeadsign ?? bt.trip.tripId }}</NuxtLink
            >
            <span class="col-start-2 col-end-4 text-xs text-dimmed sm:col-start-3">{{
              bt.trip.tripId
            }}</span>
            <span
              class="col-start-4 row-span-2 shrink-0 self-center tabular-nums text-muted sm:col-start-4"
              >{{ secToHm(bt.trip.startTimeSec ?? null) }}–{{
                secToHm(bt.trip.endTimeSec ?? null)
              }}</span
            >
            <UIcon
              v-if="bt.deadheadAfter"
              name="i-lucide-move-right"
              class="col-start-4 row-span-2 size-4 shrink-0 self-center text-warning sm:col-start-5"
            />
            <p
              v-if="bt.layoverAfterSec"
              class="col-span-full ml-9 flex items-center gap-1.5 pt-1 text-xs text-dimmed"
            >
              <span class="h-px w-4 bg-muted" />{{
                layoverLabel(bt.layoverAfterSec, !!bt.deadheadAfter, t)
              }}
            </p>
          </li>
        </ol>
      </section>

      <UModal
        v-model:open="timelineOpen"
        :title="t('explore.block.dutyTimeline')"
        :description="
          t(
            'explore.block.timelineDescription',
            {
              count: trips.length,
              from: secToHm(block.startTimeSec),
              to: secToHm(block.endTimeSec),
            },
            trips.length,
          )
        "
        scrollable
        fullscreen
      >
        <template #body>
          <BlockTimeline
            :segments="segments"
            :span-start="block.startTimeSec"
            :span-end="block.endTimeSec"
          />
        </template>
      </UModal>
    </template>
  </AppPage>
</template>
