<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    values: number[]
    /** draw a baseline at y = 0 (values are expected to straddle it) */
    zeroBaseline?: boolean
    color?: string
    height?: number
  }>(),
  { zeroBaseline: false, color: 'currentColor', height: 40 },
)

const VW = 200

const bounds = computed(() => {
  const vs = props.values.length ? props.values : [0]
  let min = Math.min(...vs)
  let max = Math.max(...vs)
  if (props.zeroBaseline) {
    min = Math.min(min, 0)
    max = Math.max(max, 0)
  }
  if (min === max) {
    min -= 1
    max += 1
  }
  return { min, max }
})

function y(v: number) {
  const { min, max } = bounds.value
  return props.height - ((v - min) / (max - min)) * props.height
}

const path = computed(() => {
  const n = props.values.length
  if (n === 0) return ''
  return props.values
    .map((v, i) => `${i === 0 ? 'M' : 'L'} ${(i / Math.max(1, n - 1)) * VW} ${y(v)}`)
    .join(' ')
})

const zeroY = computed(() => (props.zeroBaseline ? y(0) : null))
</script>

<template>
  <svg
    :viewBox="`0 0 ${VW} ${height}`"
    class="w-full"
    :style="{ height: `${height}px`, color }"
    preserveAspectRatio="none"
  >
    <line
      v-if="zeroY != null"
      x1="0"
      :x2="VW"
      :y1="zeroY"
      :y2="zeroY"
      stroke="currentColor"
      stroke-width="1"
      opacity="0.3"
      stroke-dasharray="3 3"
    />
    <path :d="path" fill="none" stroke="currentColor" stroke-width="2" vector-effect="non-scaling-stroke" />
    <circle
      v-if="values.length"
      :cx="VW"
      :cy="y(values[values.length - 1]!)"
      r="2.5"
      fill="currentColor"
      vector-effect="non-scaling-stroke"
    />
  </svg>
</template>
