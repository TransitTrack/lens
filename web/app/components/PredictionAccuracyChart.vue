<script setup lang="ts">
import { formatNumber } from '~/utils/intlLocale'
import BarChart from './BarChart.vue'
import type { Bar } from '../utils/chart'
import { usePredictionAccuracyQuery } from '../../generated/graphql'

const { t, locale } = useI18n()

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
    hint: [
      t('feed.accuracy.hint', {
        algorithm: r.algorithm,
        error: Math.round(r.meanAbsErrorSec),
        bias: `${r.meanErrorSec >= 0 ? '+' : '−'}${Math.abs(Math.round(r.meanErrorSec))}`,
        samples: t(
          'feed.accuracy.samples',
          { count: formatNumber(r.sampleCount, locale.value) },
          r.sampleCount,
        ),
      }),
      // Range coverage only for algorithms that give a range (rangeSampleCount > 0).
      r.rangeSampleCount > 0 && r.rangeCoverage != null
        ? `${t('vehicles.detail.quality.inRange')} ${Math.round(r.rangeCoverage * 100)}%`
        : null,
    ]
      .filter(Boolean)
      .join(' · '),
  })),
)
</script>

<template>
  <UCard :ui="{ body: 'flex flex-col gap-3' }">
    <div class="flex items-center gap-2 text-sm font-medium text-muted">
      <UIcon name="i-lucide-target" class="size-4" />
      {{ $t('feed.accuracy.title') }}
    </div>
    <USkeleton v-if="loading && !rows.length" class="h-32.5 w-full" />
    <p v-else-if="!rows.length" class="text-sm text-dimmed">{{ $t('feed.accuracy.none') }}</p>
    <BarChart
      v-else
      :bars="bars"
      :height="130"
      :format="(n) => $t('feed.accuracy.errorSeconds', { value: Math.round(n) })"
      animate-updates
    />
    <p class="text-[11px] text-dimmed">{{ $t('feed.accuracy.note') }}</p>
  </UCard>
</template>
