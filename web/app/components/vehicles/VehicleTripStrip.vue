<script setup lang="ts">
import { adherenceBadge, signedAdherence } from '~/utils/adherence'
import { formatClock } from '~/utils/clock'
import { useUnits } from '~/composables/useUnits'
import {
  adherenceProfile,
  segmentSpeeds,
  standstills,
  stripLabelIndexes,
  worstLoss,
  type SpeedPoint,
  type StripStop,
  type TripStrip,
} from '~/utils/vehicleDetail'

/**
 * Synoptic view of the vehicle's current trip along one line at real distances:
 * - above the line, the delay curve over the stretch already driven (where time was lost);
 * - on the line, the stops (passed ones muted) and the vehicle, coloured by adherence;
 * - below the line, the reported speed over the same stretch, with standstills between stops.
 */
const props = defineProps<{
  strip: TripStrip
  adherenceSec: number | null
  /** reported speeds along the current trip (`speedProfile`) */
  speeds: SpeedPoint[]
  stale?: boolean
  timeZone?: string
}>()

// Vertical layout, in px from the top of the strip.
const CURVE_TOP = 16
const CURVE_H = 44
const LINE_Y = 70
const SPEED_TOP = 76
const SPEED_H = 24
const HEIGHT = 124
/** Room reserved per stop name; labels that would overlap are left to the tooltip. */
const LABEL_PX = 104
/** Thresholds shared with `adherenceBadge`: within a minute is on time, beyond five is off. */
const ON_TIME_SEC = 60
const OFF_SEC = 300

const { t, locale } = useI18n()
const units = useUnits()
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
const x = (offset: number) => offset * width.value

// --- delay curve --------------------------------------------------------------------------

const profile = computed(() => adherenceProfile(props.strip, props.adherenceSec))
const worst = computed(() => worstLoss(profile.value))

/** Vertical scale: always show at least the on-time band above zero, and zero itself. */
const range = computed(() => {
  const secs = profile.value.map((p) => p.sec)
  return { hi: Math.max(ON_TIME_SEC, ...secs), lo: Math.min(0, ...secs) }
})
const y = (sec: number) =>
  CURVE_TOP + (CURVE_H * (range.value.hi - sec)) / (range.value.hi - range.value.lo)

const curvePath = computed(() =>
  profile.value
    .map((p, i) => `${i ? 'L' : 'M'}${x(p.offset).toFixed(1)},${y(p.sec).toFixed(1)}`)
    .join(''),
)
const areaPath = computed(() => {
  const points = profile.value
  if (points.length < 2) return ''
  const base = y(0).toFixed(1)
  return `${curvePath.value}L${x(points.at(-1)!.offset).toFixed(1)},${base}L${x(points[0]!.offset).toFixed(1)},${base}Z`
})

/** Hard colour bands by delay, mapped onto the curve's height as a vertical gradient. */
const curveStops = computed(() => {
  const { hi, lo } = range.value
  const bands: [number, number, string][] = [
    [Infinity, OFF_SEC, 'var(--ui-error)'],
    [OFF_SEC, ON_TIME_SEC, 'var(--ui-warning)'],
    [ON_TIME_SEC, -ON_TIME_SEC, 'var(--ui-success)'],
    [-ON_TIME_SEC, -OFF_SEC, 'var(--ui-warning)'],
    [-OFF_SEC, -Infinity, 'var(--ui-error)'],
  ]
  const at = (sec: number) => (hi - sec) / (hi - lo)
  return bands.flatMap(([top, bottom, color]) => {
    const from = Math.min(hi, top)
    const to = Math.max(lo, bottom)
    return from > to
      ? [
          { at: at(from), color },
          { at: at(to), color },
        ]
      : []
  })
})

/** Reference lines closer than this would overlap their labels; the more important one stays. */
const GRID_MIN_GAP_PX = 12

/**
 * Dashed reference lines for the thresholds the curve reaches, plus zero when it dips early.
 * Zero wins over the five-minute lines, which win over the one-minute lines.
 */
const gridLines = computed(() => {
  const { hi, lo } = range.value
  const shown: { sec: number; y: number; label: string }[] = []
  for (const sec of [0, OFF_SEC, -OFF_SEC, ON_TIME_SEC, -ON_TIME_SEC]) {
    if (!(sec < hi && sec > lo) || (sec === 0 && lo >= 0)) continue
    const lineY = y(sec)
    if (shown.every((line) => Math.abs(line.y - lineY) >= GRID_MIN_GAP_PX)) {
      shown.push({ sec, y: lineY, label: sec === 0 ? '0' : signedAdherence(sec) })
    }
  }
  return shown
})

const gradientId = `trip-strip-delay-${useId()}`

/** Current offset at the head of the curve; flips left near the right edge. */
const vehicleTag = computed(() => {
  const offset = props.strip.vehicleOffset
  if (offset === null || props.adherenceSec === null) return null
  const atRightEdge = x(offset) > width.value - 64
  return {
    left: `${x(offset) + (atRightEdge ? -10 : 10)}px`,
    top: `${y(props.adherenceSec) - 8}px`,
    flip: atRightEdge,
  }
})

// --- speed --------------------------------------------------------------------------------

/** Reports more than this far apart are drawn as separate pieces rather than bridged. */
const SPEED_GAP_M = 500
const speedPaths = computed(() => {
  const points = props.speeds
  if (points.length < 2) return []
  const max = Math.max(13.9, ...points.map((p) => p.speedMps)) // at least 50 km/h of headroom
  const depth = (mps: number) => SPEED_TOP + (SPEED_H * mps) / max
  const runs: SpeedPoint[][] = [[points[0]!]]
  for (const point of points.slice(1)) {
    const run = runs.at(-1)!
    if (point.distanceM - run.at(-1)!.distanceM > SPEED_GAP_M) runs.push([point])
    else run.push(point)
  }
  return runs
    .filter((run) => run.length >= 2)
    .map((run) => {
      const line = run
        .map((p, i) => `${i ? 'L' : 'M'}${x(p.offset).toFixed(1)},${depth(p.speedMps).toFixed(1)}`)
        .join('')
      return {
        line,
        area: `${line}L${x(run.at(-1)!.offset).toFixed(1)},${SPEED_TOP}L${x(run[0]!.offset).toFixed(1)},${SPEED_TOP}Z`,
      }
    })
})
const stillPoints = computed(() => standstills(props.strip, props.speeds))
const speedByStop = computed(() => segmentSpeeds(props.strip, props.speeds))

// --- tooltips -----------------------------------------------------------------------------

const now = useNow({ interval: 15_000 })

/** Worded like the stop list: `Arrived 21:40`, `Predicted 21:47 · in 3m`. */
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

/** `Adherence +02:40 · +01:35 since previous stop` for reached stops. */
function stopDelay(stop: StripStop): string | null {
  if (stop.adherenceSec === null) return null
  const parts = [t('vehicles.strip.adherenceAt', { value: signedAdherence(stop.adherenceSec) })]
  if (stop.lossSec !== null) {
    parts.push(t('vehicles.strip.sincePrevious', { value: signedAdherence(stop.lossSec) }))
  }
  return parts.join(' · ')
}

function stopSpeed(stop: StripStop): string | null {
  const mps = speedByStop.value.get(stop.stopPathIndex)
  return mps === undefined ? null : t('vehicles.strip.avgSpeed', { speed: units.speed(mps) })
}

/** End labels hug the strip edges; the rest centre under their stop. */
function labelAlign(index: number): string {
  if (index === 0) return 'translate-x-0 text-left'
  if (index === props.strip.stops.length - 1) return '-translate-x-full text-right'
  return '-translate-x-1/2 text-center'
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <div
      ref="track"
      role="img"
      class="relative mx-4"
      :style="{ height: `${HEIGHT}px` }"
      :aria-label="
        t('vehicles.strip.aria', {
          passed: passedCount,
          total: strip.stops.length,
          adherence: badge.label,
        })
      "
    >
      <svg
        v-if="width > 0"
        class="pointer-events-none absolute inset-0 overflow-visible"
        :width="width"
        :height="HEIGHT"
        :viewBox="`0 0 ${width} ${HEIGHT}`"
        aria-hidden="true"
      >
        <defs>
          <linearGradient
            :id="gradientId"
            gradientUnits="userSpaceOnUse"
            x1="0"
            x2="0"
            :y1="CURVE_TOP"
            :y2="CURVE_TOP + CURVE_H"
          >
            <stop
              v-for="(stop, i) in curveStops"
              :key="i"
              :offset="stop.at"
              :style="{ stopColor: stop.color }"
            />
          </linearGradient>
        </defs>

        <!-- delay curve -->
        <g v-for="line in gridLines" :key="line.sec">
          <line
            x1="0"
            :x2="width"
            :y1="line.y"
            :y2="line.y"
            stroke-dasharray="2 4"
            :style="{ stroke: 'var(--ui-border-accented)' }"
          />
          <text
            x="0"
            :y="line.y - 3"
            font-size="9"
            class="tabular-nums"
            :style="{ fill: 'var(--ui-text-dimmed)' }"
          >
            {{ line.label }}
          </text>
        </g>
        <path v-if="areaPath" :d="areaPath" :fill="`url(#${gradientId})`" opacity="0.16" />
        <path
          v-if="profile.length >= 2"
          :d="curvePath"
          fill="none"
          :stroke="`url(#${gradientId})`"
          stroke-width="2"
          stroke-linejoin="round"
        />
        <text
          v-if="worst"
          :x="(x(worst.from.offset) + x(worst.to.offset)) / 2"
          :y="Math.min(y(worst.from.sec), y(worst.to.sec)) - 5"
          text-anchor="middle"
          font-size="11"
          font-weight="700"
          class="tabular-nums"
          :style="{ fill: worst.lossSec >= ON_TIME_SEC ? 'var(--ui-error)' : 'var(--ui-warning)' }"
        >
          {{ signedAdherence(worst.lossSec) }}
        </text>

        <!-- route line -->
        <line
          x1="0"
          :x2="width"
          :y1="LINE_Y"
          :y2="LINE_Y"
          stroke-width="4"
          stroke-linecap="round"
          :style="{ stroke: 'var(--ui-bg-accented)' }"
        />

        <!-- speed below the line -->
        <g v-for="(piece, i) in speedPaths" :key="i">
          <path :d="piece.area" opacity="0.22" :style="{ fill: 'var(--ui-primary)' }" />
          <path
            :d="piece.line"
            fill="none"
            stroke-width="1.25"
            opacity="0.7"
            :style="{ stroke: 'var(--ui-primary)' }"
          />
        </g>
        <circle
          v-for="(point, i) in stillPoints"
          :key="`still-${i}`"
          :cx="x(point.offset)"
          :cy="SPEED_TOP + 3"
          r="2.5"
          :style="{ fill: 'var(--ui-warning)' }"
        />
      </svg>

      <!-- hover zones explaining the two graphs; the stop dots sit above them -->
      <UTooltip
        v-if="profile.length >= 2"
        :delay-duration="150"
        :content="{ side: 'top', sideOffset: 4 }"
        :ui="{ content: 'h-auto max-w-80 py-1.5' }"
      >
        <div class="absolute inset-x-0 top-0" :style="{ height: `${LINE_Y - 14}px` }" />
        <template #content>
          <div class="flex flex-col gap-0.5 whitespace-normal py-0.5">
            <span class="font-medium text-highlighted">{{ t('vehicles.strip.legend.delay') }}</span>
            <span class="text-muted">{{ t('vehicles.strip.legend.delayHint') }}</span>
          </div>
        </template>
      </UTooltip>
      <UTooltip
        v-if="speedPaths.length"
        :delay-duration="150"
        :content="{ side: 'bottom', sideOffset: 4 }"
        :ui="{ content: 'h-auto max-w-80 py-1.5' }"
      >
        <div
          class="absolute inset-x-0"
          :style="{ top: `${LINE_Y + 13}px`, height: `${SPEED_TOP + SPEED_H - LINE_Y - 13}px` }"
        />
        <template #content>
          <div class="flex flex-col gap-0.5 whitespace-normal py-0.5">
            <span class="font-medium text-highlighted">{{ t('vehicles.strip.legend.speed') }}</span>
            <span class="text-muted">{{ t('vehicles.strip.legend.speedHint') }}</span>
          </div>
        </template>
      </UTooltip>

      <template v-for="(stop, index) in strip.stops" :key="stop.stopPathIndex">
        <UTooltip
          :delay-duration="0"
          :content="{ side: 'bottom', sideOffset: 6 }"
          :ui="{ content: 'h-auto py-1.5' }"
        >
          <!-- 24px hover target centred on the line; the visible dot sits inside it -->
          <span
            class="absolute flex size-6 -translate-x-1/2 -translate-y-1/2 cursor-default items-center justify-center"
            :style="{ left: pct(stop.offset), top: `${LINE_Y}px` }"
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
              <span v-if="stopDelay(stop)" class="tabular-nums text-muted">{{
                stopDelay(stop)
              }}</span>
              <span v-if="stopSpeed(stop)" class="tabular-nums text-muted">{{
                stopSpeed(stop)
              }}</span>
              <span v-if="stop.timepoint" class="text-dimmed">{{
                t('vehicles.strip.timepoint')
              }}</span>
            </div>
          </template>
        </UTooltip>
        <span
          v-if="labelled.has(stop.stopPathIndex)"
          class="pointer-events-none absolute max-w-24 truncate text-[11px] leading-4"
          :class="[
            labelAlign(index),
            stop.next
              ? 'font-semibold text-highlighted'
              : stop.passed
                ? 'text-dimmed'
                : 'text-muted',
          ]"
          :style="{ left: pct(stop.offset), top: `${SPEED_TOP + SPEED_H + 4}px` }"
        >
          {{ stop.name }}
        </span>
      </template>

      <template v-if="strip.vehicleOffset !== null">
        <span
          v-if="vehicleTag"
          class="pointer-events-none absolute rounded bg-elevated px-1.5 text-[11px] font-semibold leading-4 tabular-nums"
          :class="[tone.text, vehicleTag.flip && '-translate-x-full']"
          :style="{ left: vehicleTag.left, top: vehicleTag.top }"
        >
          {{ signedAdherence(adherenceSec!) }}
        </span>
        <UTooltip :text="badge.label">
          <span
            class="absolute flex size-6 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full shadow-md ring-4 ring-bg"
            :class="[tone.dot, stale && 'opacity-60']"
            :style="{ left: pct(strip.vehicleOffset), top: `${LINE_Y}px` }"
          >
            <UIcon name="i-lucide-bus" class="size-3.5 text-white" />
          </span>
        </UTooltip>
      </template>
    </div>

    <!-- legend -->
    <ul class="mx-4 flex flex-wrap items-center gap-x-4 gap-y-1 text-[11px] text-muted">
      <UTooltip
        :text="t('vehicles.strip.legend.delayHint')"
        :ui="{ content: 'h-auto max-w-80 whitespace-normal py-1.5' }"
      >
        <li class="flex cursor-help items-center gap-1.5">
          <span class="h-0.5 w-5 rounded-full bg-linear-to-r from-success via-warning to-error" />
          {{ t('vehicles.strip.legend.delay') }}
        </li>
      </UTooltip>
      <UTooltip
        :text="t('vehicles.strip.legend.speedHint')"
        :ui="{ content: 'h-auto max-w-80 whitespace-normal py-1.5' }"
      >
        <li class="flex cursor-help items-center gap-1.5">
          <span class="h-2 w-5 rounded-b-sm border-t border-primary bg-primary/25" />
          {{ t('vehicles.strip.legend.speed') }}
        </li>
      </UTooltip>
      <li class="flex items-center gap-1.5">
        <span class="size-1.5 rounded-full bg-warning" />
        {{ t('vehicles.strip.legend.standstill') }}
      </li>
      <li class="flex items-center gap-1.5">
        <span class="size-2.5 rounded-full border-2 border-muted bg-muted" />
        {{ t('vehicles.strip.legend.passed') }}
      </li>
      <li class="flex items-center gap-1.5">
        <span class="size-2.5 rounded-full border-2 border-primary" />
        {{ t('vehicles.strip.legend.upcoming') }}
      </li>
      <li class="flex items-center gap-1.5">
        <span class="size-2 rotate-45 rounded-[1px] border-2 border-primary" />
        {{ t('vehicles.strip.timepoint') }}
      </li>
    </ul>
  </div>
</template>
