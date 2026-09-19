<script setup lang="ts">
import type { TimelineSegment } from '~/utils/blocks'
import { ganttRows, layoverLabel, timelineTicks } from '~/utils/blocks'

const props = defineProps<{
  segments: TimelineSegment[]
  spanStart: number
  spanEnd: number
}>()

const VW = 1000
const ticks = computed(() => {
  const span = Math.max(1, props.spanEnd - props.spanStart)
  return timelineTicks(props.spanStart, props.spanEnd).map((tick) => ({
    ...tick,
    x: ((tick.sec - props.spanStart) / span) * VW,
  }))
})

const rows = computed(() => ganttRows(props.segments))
</script>

<template>
  <div class="p-0">
    <div class="overflow-x-auto pb-1">
      <div class="min-w-152">
        <div class="grid grid-cols-[10rem_minmax(0,1fr)] border-b border-default pb-2">
          <p class="text-xs font-medium text-dimmed">Trip</p>
          <div class="relative h-4">
            <span
              v-for="tick in ticks"
              :key="`header-${tick.sec}`"
              class="absolute -translate-x-1/2 text-[10px] tabular-nums text-muted"
              :class="tick.endpoint ? 'font-semibold text-highlighted' : ''"
              :style="{ left: `${(tick.x / VW) * 100}%` }"
              >{{ tick.label }}</span
            >
          </div>
        </div>
        <div
          v-for="row in rows"
          :key="row.trip.key"
          class="grid grid-cols-[10rem_minmax(0,1fr)] items-center gap-3 border-b border-default/60 py-2 last:border-b-0"
        >
          <div class="min-w-0">
            <p class="truncate text-xs font-semibold text-highlighted">{{ row.trip.routeLabel }}</p>
            <p class="truncate text-xs text-dimmed">
              {{ row.trip.headsign ?? row.trip.routeLabel }}
            </p>
          </div>
          <div class="relative h-10 overflow-hidden rounded-md bg-muted/15">
            <span
              v-for="tick in ticks"
              :key="`grid-${row.trip.key}-${tick.sec}`"
              class="absolute inset-y-0 border-l"
              :class="tick.endpoint ? 'border-default' : 'border-default/40'"
              :style="{ left: `${(tick.x / VW) * 100}%` }"
            />
            <div
              class="absolute inset-x-auto top-1.5 bottom-4 rounded-sm shadow-sm"
              :style="{
                left: `${row.trip.x * 100}%`,
                width: `${row.trip.w * 100}%`,
                backgroundColor: row.trip.color,
              }"
              :title="`${row.trip.routeLabel} ${row.trip.headsign ?? ''} · ${secToHm(row.trip.startSec)}–${secToHm(row.trip.endSec)}`"
            />
            <div
              v-if="row.layoverAfter"
              class="absolute top-1/2 h-px -translate-y-1/2 border-t border-dashed border-muted"
              :style="{
                left: `${row.layoverAfter.x * 100}%`,
                width: `${row.layoverAfter.w * 100}%`,
              }"
              :title="`Layover ${secToHm(row.layoverAfter.startSec)}–${secToHm(row.layoverAfter.endSec)}`"
            />
            <span
              v-if="row.layoverAfter"
              class="absolute bottom-0.5 max-w-[85%] -translate-x-1/2 truncate text-[10px] leading-none text-dimmed"
              :style="{ left: `${(row.layoverAfter.x + row.layoverAfter.w / 2) * 100}%` }"
              :title="
                layoverLabel(
                  row.layoverAfter.endSec - row.layoverAfter.startSec,
                  row.trip.deadheadAfter,
                )
              "
              >{{
                layoverLabel(
                  row.layoverAfter.endSec - row.layoverAfter.startSec,
                  row.trip.deadheadAfter,
                )
              }}</span
            >
            <span
              v-if="row.trip.deadheadAfter"
              class="absolute top-1/2 size-2 -translate-x-1/2 -translate-y-1/2 rounded-full bg-warning ring-2 ring-elevated"
              :style="{ left: `${(row.trip.x + row.trip.w) * 100}%` }"
              title="Deadhead after"
            />
          </div>
        </div>
      </div>
    </div>
    <p class="mt-3 text-xs text-dimmed">
      Bars are revenue trips; dotted extensions show layovers; amber dots mark deadheads.
    </p>
  </div>
</template>
