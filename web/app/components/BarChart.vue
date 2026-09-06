<script setup lang="ts">
import type { Bar } from '../utils/chart'

const props = withDefaults(
  defineProps<{
    bars: Bar[]
    /** dashed horizontal marker line, in the same units as the values */
    referenceValue?: number | null
    referenceLabel?: string
    height?: number
    format?: (n: number) => string
  }>(),
  {
    referenceValue: null,
    referenceLabel: '',
    height: 150,
    format: (n: number) => String(Math.round(n)),
  },
)

const VW = 300
const VH = 120

const max = computed(() => {
  const vals = props.bars.map((b) => Math.max(0, b.value))
  return Math.max(...vals, props.referenceValue ?? 0, 1) * 1.15
})

const slot = computed(() => (props.bars.length ? VW / props.bars.length : VW))
const barW = computed(() => slot.value * 0.62)

function x(i: number) {
  return i * slot.value + (slot.value - barW.value) / 2
}
function barH(v: number) {
  return (Math.max(0, v) / max.value) * VH
}

const refY = computed(() =>
  props.referenceValue == null ? null : VH - barH(props.referenceValue),
)
</script>

<template>
  <div class="flex flex-col gap-1">
    <svg
      :viewBox="`0 0 ${VW} ${VH}`"
      class="w-full text-muted"
      :style="{ height: `${height}px` }"
      preserveAspectRatio="none"
    >
      <line
        v-for="frac in [0.25, 0.5, 0.75, 1]"
        :key="frac"
        x1="0"
        :x2="VW"
        :y1="frac * VH"
        :y2="frac * VH"
        stroke="currentColor"
        stroke-width="1"
        opacity="0.15"
      />
      <rect
        v-for="(bar, i) in bars"
        :key="bar.label"
        :x="x(i)"
        :y="VH - barH(bar.value)"
        :width="barW"
        :height="barH(bar.value)"
        :fill="bar.color ?? 'currentColor'"
      >
        <title>{{ bar.hint ?? `${bar.label}: ${format(bar.value)}` }}</title>
      </rect>
      <line
        v-if="refY != null"
        x1="0"
        :x2="VW"
        :y1="refY"
        :y2="refY"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-dasharray="5 4"
        opacity="0.7"
      />
    </svg>

    <div class="flex">
      <div
        v-for="bar in bars"
        :key="bar.label"
        class="min-w-0 flex-1 truncate px-0.5 text-center text-[10px] text-muted"
        :title="bar.hint ?? bar.label"
      >
        {{ bar.label }}
      </div>
    </div>

    <p v-if="referenceLabel && referenceValue != null" class="text-[11px] text-dimmed">
      – – {{ referenceLabel }}: {{ format(referenceValue) }}
    </p>
  </div>
</template>
