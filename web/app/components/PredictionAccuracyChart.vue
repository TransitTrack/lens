<script setup lang="ts">
import BarChart from './BarChart.vue'
import type { Bar } from '../utils/chart'
import { usePredictionAccuracyQuery } from '../../generated/graphql'

const props = defineProps<{ feedCode: string | null }>()

const { result, loading } = usePredictionAccuracyQuery(
  () => ({ feedCode: props.feedCode ?? '', sinceDays: 7 }),
  () => ({ enabled: !!props.feedCode, pollInterval: 300_000 }),
)

const rows = computed(() =>
  [...(result.value?.predictionAccuracy ?? [])].sort(
    (a, b) => a.meanAbsErrorSec - b.meanAbsErrorSec,
  ),
)

function short(algorithm: string): string {
  return algorithm
    .split('_')
    .map((w) => w[0])
    .join('')
}

const bars = computed<Bar[]>(() =>
  rows.value.map((r, i) => ({
    label: short(r.algorithm),
    value: r.meanAbsErrorSec,
    color: i === 0 ? '#22c55e' : i === rows.value.length - 1 ? '#ef4444' : '#f59e0b',
    hint: `${r.algorithm}: ±${Math.round(r.meanAbsErrorSec)}s abs error, bias ${
      r.meanErrorSec >= 0 ? '+' : '−'
    }${Math.abs(Math.round(r.meanErrorSec))}s (${r.sampleCount.toLocaleString()} samples)`,
  })),
)
</script>

<template>
  <UCard :ui="{ body: 'flex flex-col gap-3' }">
    <div class="flex items-center gap-2 text-sm font-medium text-muted">
      <UIcon name="i-lucide-target" class="size-4" />
      Prediction accuracy · 7d
    </div>
    <USkeleton v-if="loading && !rows.length" class="h-[130px] w-full" />
    <p v-else-if="!rows.length" class="text-sm text-dimmed">No prediction samples yet.</p>
    <BarChart
      v-else
      :bars="bars"
      :height="130"
      :format="(n) => `±${Math.round(n)}s`"
      animate-updates
    />
    <p class="text-[11px] text-dimmed">Mean absolute arrival error — lower is better.</p>
  </UCard>
</template>
