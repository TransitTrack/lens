<script setup lang="ts">
import { useOptimizationRecommendationsQuery } from '~/composables/useRestOptimization'
import type { OptimizationRecommendationsQuery } from '~/types/restViews'
import {secToHm} from '~/utils/gtfs'
import {formatOptimizationKind, formatSignedDuration} from '~/utils/optimizationPresentation'

const {t} = useI18n()
const props = defineProps<{ runId: string }>()
const selected = defineModel<string[]>('selected', {default: () => []})
const conflictIds = defineModel<string[]>('conflictIds', {default: () => []})

const {result, loading, error, refetch} = useOptimizationRecommendationsQuery(() => ({ variables: { runId: props.runId, status: 'PENDING', offset: 0, limit: 100 } })
)

defineExpose({refetch})

type Recommendation = OptimizationRecommendationsQuery['optimizationRecommendations'][number]
const recommendations = computed<Recommendation[]>(
  () => result.value?.optimizationRecommendations ?? [],
)

function toggle(id: string, checked: boolean) {
  if (checked) {
    if (!selected.value.includes(id)) selected.value = [...selected.value, id]
  } else {
    selected.value = selected.value.filter((x) => x !== id)
  }
}

function targetSummary(row: Recommendation): string {
  const proposed = row.proposedValue as {targets?: Array<Record<string, unknown>>}
  const targets = proposed?.targets ?? []
  if (!targets.length) return t('optimize.target.schedule')

  const first = targets[0]!
  if (targets.length === 1) {
    return first.stopSequence != null
      ? t('optimize.target.tripStop', {trip: first.tripId, stop: first.stopSequence})
      : String(first.tripId)
  }

  const tripCount = new Set(targets.map((target) => String(target.tripId))).size
  const trips = t('optimize.target.inTrips', {count: tripCount}, tripCount)
  // i18n-keys: optimize.target.stopTimes, optimize.target.trips
  const key = row.kind === 'STOP_TIME' ? 'optimize.target.stopTimes' : 'optimize.target.trips'
  return t(key, {count: targets.length, trips}, targets.length)
}

interface TargetGroup {
  tripId: string
  stopSequences: number[]
  count: number
}

function targetGroups(row: Recommendation): TargetGroup[] {
  const proposed = row.proposedValue as {targets?: Array<Record<string, unknown>>}
  const groups = new Map<string, TargetGroup>()
  for (const target of proposed?.targets ?? []) {
    const tripId = String(target.tripId)
    const group = groups.get(tripId) ?? {tripId, stopSequences: [], count: 0}
    group.count += 1
    if (typeof target.stopSequence === 'number') group.stopSequences.push(target.stopSequence)
    groups.set(tripId, group)
  }
  return [...groups.values()].map((group) => ({
    ...group,
    stopSequences: [...new Set(group.stopSequences)].sort((a, b) => a - b),
  }))
}

function hasMultipleTargets(row: Recommendation): boolean {
  return targetGroups(row).some((group) => group.count > 1) || targetGroups(row).length > 1
}

function stopRange(stopSequences: number[]): string {
  if (!stopSequences.length) return t('optimize.target.tripLevel')
  const ranges: string[] = []
  let start = stopSequences[0]!
  let end = start
  for (const stop of stopSequences.slice(1)) {
    if (stop === end + 1) {
      end = stop
    } else {
      ranges.push(start === end ? String(start) : `${start}–${end}`)
      start = stop
      end = stop
    }
  }
  ranges.push(start === end ? String(start) : `${start}–${end}`)
  return t('optimize.target.stops', {ranges: ranges.join(', ')})
}

function timing(row: Recommendation, value: 'currentValue' | 'proposedValue'): string {
  const payload = row[value] as {targets?: Array<Record<string, unknown>>}
  const target = payload?.targets?.[0]
  if (!target) return '—'
  const seconds = row.kind === 'TRIP_SHIFT'
    ? target.startTimeSec
    : target.arrivalSec ?? target.departureSec
  return secToHm(seconds as number | null)
}

function delta(row: Recommendation) {
  return formatSignedDuration(row.deltaSec, t)
}

function deltaText(row: Recommendation): string {
  const {text, direction} = delta(row)
  if (direction === 'later') return t('optimize.deltaLater', {duration: text})
  if (direction === 'earlier') return t('optimize.deltaEarlier', {duration: text})
  return text
}
</script>

<template>
  <section class="flex flex-col gap-4" aria-labelledby="recommendations-heading">
    <div class="flex flex-col justify-between gap-3 sm:flex-row sm:items-end">
      <div>
        <p class="text-xs font-semibold tracking-[0.18em] text-primary uppercase">{{ $t('optimize.review') }}</p>
        <h2 id="recommendations-heading" class="mt-1 text-xl font-semibold text-highlighted">
          {{ $t('optimize.readyHeading', {count: recommendations.length}, recommendations.length) }}
        </h2>
        <p class="mt-1 text-sm text-muted">{{ $t('optimize.selectCompatible') }}</p>
      </div>
      <UBadge color="neutral" variant="subtle" size="lg">
        {{ $t('optimize.selected', {count: selected.length}) }}
      </UBadge>
    </div>

    <div v-if="loading && !recommendations.length" class="grid gap-3">
      <USkeleton class="h-44 w-full" />
      <USkeleton class="h-44 w-full" />
    </div>

    <UAlert
      v-else-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      :title="$t('optimize.recommendationsUnavailable')"
      :description="error.message"
    />

    <div v-else-if="!recommendations.length" class="flex flex-col items-center gap-3 rounded-md border border-dashed border-default py-16 text-center">
      <UIcon name="i-lucide-inbox" class="size-8 text-dimmed" />
      <div>
        <p class="font-medium text-highlighted">{{ $t('optimize.nothingTitle') }}</p>
        <p class="mt-1 text-sm text-muted">{{ $t('optimize.nothingBody') }}</p>
      </div>
    </div>

    <div v-else class="grid gap-3">
      <article
        v-for="recommendation in recommendations"
        :key="recommendation.id"
        data-testid="optimization-recommendation"
        class="rounded-md border bg-default p-4 transition sm:p-5"
        :class="selected.includes(recommendation.id) ? 'border-primary ring-1 ring-primary/25' : 'border-default hover:border-muted'"
      >
        <div class="flex gap-3">
          <UCheckbox
            class="mt-1"
            :model-value="selected.includes(recommendation.id)"
            :aria-label="$t('optimize.selectAria', {kind: formatOptimizationKind(recommendation.kind, t)})"
            @update:model-value="(value: boolean) => toggle(recommendation.id, value)"
          />
          <div class="min-w-0 flex-1">
            <div class="flex flex-col justify-between gap-3 sm:flex-row sm:items-start">
              <div class="min-w-0">
                <div class="flex flex-wrap items-center gap-2">
                  <UBadge color="primary" variant="subtle">{{ formatOptimizationKind(recommendation.kind, t) }}</UBadge>
                  <span class="text-sm text-muted">{{ targetSummary(recommendation) }}</span>
                </div>
                <p class="mt-3 text-sm leading-6 text-highlighted">{{ recommendation.reason }}</p>
              </div>
              <div
                class="shrink-0 rounded-md px-3 py-2 text-right"
                :class="{
                  'bg-success/10 text-success': delta(recommendation).direction === 'later',
                  'bg-warning/10 text-warning': delta(recommendation).direction === 'earlier',
                  'bg-elevated text-muted': delta(recommendation).direction === 'unchanged',
                }"
              >
                <p class="text-[11px] font-semibold tracking-wide uppercase opacity-75">{{ $t('optimize.scheduleChange') }}</p>
                <p class="mt-0.5 text-base font-semibold">{{ deltaText(recommendation) }}</p>
              </div>
            </div>

            <div class="mt-4 grid gap-3 border-t border-default pt-4 sm:grid-cols-[1fr_auto_1fr_auto] sm:items-center">
              <div>
                <p class="text-xs font-medium tracking-wide text-dimmed uppercase">{{ $t('optimize.current') }}</p>
                <p class="mt-1 text-base font-semibold text-highlighted">{{ timing(recommendation, 'currentValue') }}</p>
              </div>
              <UIcon name="i-lucide-arrow-right" class="hidden size-4 text-dimmed sm:block" />
              <div>
                <p class="text-xs font-medium tracking-wide text-dimmed uppercase">{{ $t('optimize.proposed') }}</p>
                <p class="mt-1 text-base font-semibold text-primary">{{ timing(recommendation, 'proposedValue') }}</p>
              </div>
              <div class="rounded-md bg-elevated px-3 py-2 text-sm text-muted">
                <span class="font-semibold text-highlighted">{{ recommendation.sampleCount }}</span> {{ $t('optimize.samples', recommendation.sampleCount) }}
              </div>
            </div>

            <UCollapsible v-if="hasMultipleTargets(recommendation)" class="mt-4">
              <template #default="{open}">
                <UButton
                  color="neutral"
                  variant="ghost"
                  size="sm"
                  :label="open ? $t('optimize.hideTargets') : $t('optimize.showTargets')"
                  :trailing-icon="open ? 'i-lucide-chevron-up' : 'i-lucide-chevron-down'"
                />
              </template>
              <template #content>
                <div class="mt-2 rounded-md border border-default bg-elevated p-3 sm:p-4">
                  <div class="flex items-center gap-2">
                    <div class="grid size-7 place-items-center rounded-md bg-primary/10 text-primary">
                      <UIcon name="i-lucide-git-branch" class="size-3.5" />
                    </div>
                    <div>
                      <p class="text-sm font-medium text-highlighted">{{ $t('optimize.affectedTargets') }}</p>
                      <p class="text-xs text-muted">{{ $t('optimize.groupedByTrip') }}</p>
                    </div>
                  </div>
                  <div class="mt-3 grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                    <div
                      v-for="group in targetGroups(recommendation)"
                      :key="group.tripId"
                      class="min-w-0 rounded-md border border-default bg-default px-3 py-2.5"
                    >
                      <div class="flex items-center justify-between gap-2">
                        <p class="min-w-0 truncate font-mono text-xs font-medium text-highlighted" :title="group.tripId">{{ group.tripId }}</p>
                        <UBadge color="neutral" variant="subtle" size="xs">{{ group.count }}</UBadge>
                      </div>
                      <p class="mt-1 text-xs text-muted">{{ stopRange(group.stopSequences) }}</p>
                    </div>
                  </div>
                </div>
              </template>
            </UCollapsible>

            <div v-if="conflictIds.includes(recommendation.id)" class="mt-4">
              <UBadge color="error" variant="subtle" icon="i-lucide-triangle-alert">
                {{ $t('optimize.conflict') }}
              </UBadge>
            </div>
          </div>
        </div>
      </article>
    </div>
  </section>
</template>
