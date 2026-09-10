<script setup lang="ts">
import SparklineChart from './SparklineChart.vue'

const props = defineProps<{
  /** current speed, already formatted for the active unit system */
  speed: string
  /** recent speeds in m/s, oldest → newest, for the sparkline */
  speedTrail: number[]
  reportAgeSec: number | null
  avgIntervalSec: number | null
  trailCount: number
}>()

function ago(sec: number | null): string {
  if (sec == null) return '—'
  if (sec < 45) return `${Math.round(sec)}s ago`
  if (sec < 3600) return `${Math.round(sec / 60)}m ago`
  return `${Math.round(sec / 3600)}h ago`
}

const hasSpark = computed(() => props.speedTrail.length >= 2)
</script>

<template>
  <div class="flex flex-col gap-2">
    <div class="flex items-center justify-between text-sm font-medium text-muted">
      <span>Telemetry</span>
      <span class="text-lg font-semibold text-highlighted">{{ speed }}</span>
    </div>

    <SparklineChart v-if="hasSpark" :values="speedTrail" :height="36" color="#3b82f6" />

    <dl class="grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
      <dt class="text-dimmed">Last report</dt>
      <dd class="text-muted">{{ ago(reportAgeSec) }}</dd>
      <dt class="text-dimmed">Avg interval</dt>
      <dd class="text-muted">
        {{ avgIntervalSec == null ? '—' : `${avgIntervalSec.toFixed(1)}s` }}
      </dd>
      <dt class="text-dimmed">Trail points</dt>
      <dd class="text-muted">{{ trailCount }}</dd>
    </dl>
  </div>
</template>
