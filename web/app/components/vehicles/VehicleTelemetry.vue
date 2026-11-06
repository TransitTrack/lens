<script setup lang="ts">
import SparklineChart from '~/components/charts/SparklineChart.vue'
import { ageLabel } from '~/utils/vehicleTelemetry'

const props = defineProps<{
  /** current speed, already formatted for the active unit system */
  speed: string
  /** recent speeds in m/s, oldest → newest, for the sparkline */
  speedTrail: number[]
  reportAgeSec: number | null
  avgIntervalSec: number | null
  trailCount: number
}>()

const { t } = useI18n()

function ago(sec: number | null): string {
  if (sec == null) return '—'
  return ageLabel(sec, t)
}

const hasSpark = computed(() => props.speedTrail.length >= 2)
</script>

<template>
  <div class="flex flex-col gap-2">
    <div class="flex items-center justify-between text-sm font-medium text-muted">
      <span>{{ $t('vehicles.telemetry.title') }}</span>
      <span class="text-lg font-semibold text-highlighted">{{ speed }}</span>
    </div>

    <SparklineChart v-if="hasSpark" :values="speedTrail" :height="36" color="#3b82f6" />

    <dl class="grid grid-cols-2 gap-x-3 gap-y-1 text-xs">
      <dt class="text-dimmed">{{ $t('vehicles.field.lastReport') }}</dt>
      <dd class="text-muted">{{ ago(reportAgeSec) }}</dd>
      <dt class="text-dimmed">{{ $t('vehicles.telemetry.avgInterval') }}</dt>
      <dd class="text-muted">
        {{ avgIntervalSec == null ? '—' : `${avgIntervalSec.toFixed(1)}s` }}
      </dd>
      <dt class="text-dimmed">{{ $t('vehicles.telemetry.trailPoints') }}</dt>
      <dd class="text-muted">{{ trailCount }}</dd>
    </dl>
  </div>
</template>
