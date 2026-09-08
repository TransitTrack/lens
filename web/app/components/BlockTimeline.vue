<script setup lang="ts">
import type {TimelineSegment} from '~/utils/blocks'

const props = withDefaults(
  defineProps<{
    segments: TimelineSegment[]
    spanStart: number
    spanEnd: number
    height?: number
  }>(),
  {height: 64},
)

const VW = 1000

const hourTicks = computed(() => {
  const span = Math.max(1, props.spanEnd - props.spanStart)
  const first = Math.ceil(props.spanStart / 3600)
  const last = Math.floor(props.spanEnd / 3600)
  const ticks: { x: number, label: string }[] = []
  for (let h = first; h <= last; h++) {
    ticks.push({x: ((h * 3600 - props.spanStart) / span) * VW, label: `${h % 24}`})
  }
  return ticks
})
</script>

<template>
  <div class="flex flex-col gap-1">
    <svg
      :viewBox="`0 0 ${VW} ${height}`"
      class="w-full text-muted"
      :style="{ height: `${height}px` }"
      preserveAspectRatio="none"
    >
      <line
        v-for="t in hourTicks"
        :key="`g${t.x}`"
        :x1="t.x"
        :x2="t.x"
        y1="0"
        :y2="height - 14"
        stroke="currentColor"
        stroke-width="1"
        opacity="0.15"
      />

      <template v-for="seg in segments" :key="seg.key">
        <rect
          v-if="seg.kind === 'trip'"
          :x="seg.x * VW"
          y="6"
          :width="seg.w * VW"
          :height="height - 26"
          :fill="seg.color"
          rx="2"
        >
          <title>{{ seg.routeLabel }} {{ seg.headsign ?? '' }} · {{ secToHm(seg.startSec) }}–{{ secToHm(seg.endSec) }}</title>
        </rect>
        <rect
          v-else
          :x="seg.x * VW"
          :y="(height - 26) / 2"
          :width="seg.w * VW"
          :height="6"
          fill="currentColor"
          opacity="0.4"
        >
          <title>Layover {{ secToHm(seg.startSec) }}–{{ secToHm(seg.endSec) }}</title>
        </rect>
        <circle
          v-if="seg.kind === 'trip' && seg.deadheadAfter"
          :cx="(seg.x + seg.w) * VW"
          :cy="(height - 20) / 2"
          r="3"
          fill="#f59e0b"
        >
          <title>Deadhead after</title>
        </circle>
      </template>

      <text
        v-for="t in hourTicks"
        :key="`l${t.x}`"
        :x="t.x"
        :y="height - 2"
        font-size="9"
        text-anchor="middle"
        fill="currentColor"
      >{{ t.label }}
      </text>
    </svg>
    <div class="flex items-center gap-3 text-[11px] text-dimmed">
      <span class="flex items-center gap-1"><span class="size-2 rounded-sm bg-primary"/> trip</span>
      <span class="flex items-center gap-1"><span class="h-1 w-3 rounded bg-muted"/> layover</span>
      <span class="flex items-center gap-1"><span class="size-2 rounded-full bg-warning"/> deadhead</span>
    </div>
  </div>
</template>
