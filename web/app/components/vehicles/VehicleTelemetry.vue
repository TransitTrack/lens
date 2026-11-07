<script setup lang="ts">
import { ageLabel } from '~/utils/vehicleTelemetry'

defineProps<{
  /** current speed, already formatted for the active unit system */
  speed: string
  reportAgeSec: number | null
  avgIntervalSec: number | null
  trailCount: number
}>()

const { t } = useI18n()

function ago(sec: number | null): string {
  if (sec == null) return '—'
  return ageLabel(sec, t)
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <div class="flex items-center justify-between text-sm font-medium text-muted">
      <span>{{ $t('vehicles.telemetry.title') }}</span>
      <span class="text-lg font-semibold text-highlighted">{{ speed }}</span>
    </div>

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
