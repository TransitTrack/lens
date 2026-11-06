<script setup lang="ts">
import { adherenceBadge, signedAdherence } from '~/utils/adherence'
import { formatClock } from '~/utils/clock'
import { stripLabelIndexes, type StripStop, type TripStrip } from '~/utils/vehicleDetail'

/**
 * Synoptic view of the vehicle's current trip: stops at their real distance along a single
 * line (passed ones muted), and the vehicle as a dot coloured by schedule adherence.
 */
const props = defineProps<{
  strip: TripStrip
  adherenceSec: number | null
  stale?: boolean
  timeZone?: string
}>()

/** Room reserved per stop name; labels that would overlap are left to the tooltip. */
const LABEL_PX = 104

const { t, locale } = useI18n()
const track = ref<HTMLElement | null>(null)
const { width } = useElementSize(track)

const labelled = computed(() => stripLabelIndexes(props.strip.stops, width.value, LABEL_PX))
const passedCount = computed(() => props.strip.stops.filter((stop) => stop.passed).length)

const badge = computed(() => adherenceBadge(props.adherenceSec, t))
const VEHICLE_TONE = {
  success: { dot: 'bg-success', text: 'text-success' },
  warning: { dot: 'bg-warning', text: 'text-warning' },
  error: { dot: 'bg-error', text: 'text-error' },
  neutral: { dot: 'bg-zinc-400', text: 'text-muted' },
} as const
const tone = computed(() => VEHICLE_TONE[badge.value.color])

const pct = (offset: number) => `${(offset * 100).toFixed(3)}%`

const now = useNow({ interval: 15_000 })

/** Second tooltip line, worded like the stop list: `Arrived 21:40`, `Predicted 21:47 · in 3m`. */
function stopTiming(stop: StripStop, index: number): string | null {
  if (!stop.eta) return stop.passed ? t('vehicles.detail.wasDue') : null
  const time = formatClock(stop.eta, { timeZone: props.timeZone, locale: locale.value })
  if (stop.arrived) {
    return `${index === 0 ? t('vehicles.detail.departed') : t('vehicles.detail.arrived')} ${time}`
  }
  const deltaSec = Math.round((new Date(stop.eta).getTime() - now.value.getTime()) / 1000)
  const relative =
    deltaSec < 45
      ? t('vehicles.detail.now')
      : t('vehicles.detail.inMinutes', { count: Math.round(deltaSec / 60) })
  return `${t('vehicles.detail.predicted')} ${time} · ${relative}`
}

/** End labels hug the strip edges; the rest centre under their stop. */
function labelAlign(index: number): string {
  if (index === 0) return 'translate-x-0 text-left'
  if (index === props.strip.stops.length - 1) return '-translate-x-full text-right'
  return '-translate-x-1/2 text-center'
}
</script>

<template>
  <div
    ref="track"
    role="img"
    class="relative mx-4 h-20"
    :aria-label="
      t('vehicles.strip.aria', { passed: passedCount, total: strip.stops.length, adherence: badge.label })
    "
  >
    <div class="absolute inset-x-0 top-9 h-1 -translate-y-1/2 rounded-full bg-accented" />

    <template v-for="(stop, index) in strip.stops" :key="stop.stopPathIndex">
      <UTooltip
        :delay-duration="0"
        :content="{ side: 'bottom', sideOffset: 6 }"
        :ui="{ content: 'h-auto py-1.5' }"
      >
        <!-- 24px hover target centred on the line; the visible dot sits inside it -->
        <span
          class="absolute top-9 flex size-6 -translate-x-1/2 -translate-y-1/2 cursor-default items-center justify-center"
          :style="{ left: pct(stop.offset) }"
        >
          <span
            class="border-2 bg-default"
            :class="[
              stop.timepoint ? 'size-3 rotate-45 rounded-[2px]' : 'size-3.5 rounded-full',
              stop.passed ? 'border-muted bg-muted' : 'border-primary',
              stop.next && 'ring-2 ring-primary/30',
            ]"
          />
        </span>
        <template #content>
          <div class="flex flex-col gap-0.5 py-0.5">
            <span class="font-medium text-highlighted">{{ stop.name }}</span>
            <span v-if="stopTiming(stop, index)" class="tabular-nums text-muted">
              {{ stopTiming(stop, index) }}
            </span>
            <span v-if="stop.timepoint" class="text-dimmed">{{ t('vehicles.strip.timepoint') }}</span>
          </div>
        </template>
      </UTooltip>
      <span
        v-if="labelled.has(stop.stopPathIndex)"
        class="pointer-events-none absolute top-13 max-w-24 truncate text-[11px] leading-4"
        :class="[
          labelAlign(index),
          stop.next ? 'font-semibold text-highlighted' : stop.passed ? 'text-dimmed' : 'text-muted',
        ]"
        :style="{ left: pct(stop.offset) }"
      >
        {{ stop.name }}
      </span>
    </template>

    <template v-if="strip.vehicleOffset !== null">
      <span
        class="pointer-events-none absolute top-0 -translate-x-1/2 rounded bg-elevated px-1.5 text-[11px] font-semibold leading-4 tabular-nums"
        :class="tone.text"
        :style="{ left: pct(strip.vehicleOffset) }"
      >
        {{ adherenceSec === null ? badge.label : signedAdherence(adherenceSec) }}
      </span>
      <UTooltip :text="badge.label">
        <span
          class="absolute top-9 flex size-6 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full shadow-md ring-4 ring-(--ui-bg)"
          :class="[tone.dot, stale && 'opacity-60']"
          :style="{ left: pct(strip.vehicleOffset) }"
        >
          <UIcon name="i-lucide-bus" class="size-3.5 text-white" />
        </span>
      </UTooltip>
    </template>
  </div>
</template>
