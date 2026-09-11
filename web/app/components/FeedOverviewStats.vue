<script setup lang="ts">
import { formatTimeAgo } from '@vueuse/core'
import { useFeeds } from '~/composables/useFeeds'
import { useFeedDetailQuery } from '~~/generated/graphql'
import { gtfsDate } from '~/utils/gtfs'
import { revisionStatusMeta, rowCount } from '~/utils/feedStatus'

const { selectedFeedCode } = useFeeds()

const { result, loading, error } = useFeedDetailQuery(
  () => ({ code: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value, pollInterval: 60_000 }),
)

const feed = computed(() => result.value?.feed ?? null)
const info = computed(() => result.value?.feedInfo ?? null)
const rev = computed(() => feed.value?.activeRevision ?? null)
const counts = computed(() => (rev.value?.rowCounts ?? null) as Record<string, number> | null)

const statusMeta = computed(() => revisionStatusMeta(rev.value?.status))

const daysLeft = computed(() => {
  const raw = rev.value?.feedEndDate
  if (!raw) return null
  const s = raw.length === 8 ? `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)}` : raw
  const end = new Date(s).getTime()
  if (Number.isNaN(end)) return null
  return Math.round((end - Date.now()) / 86_400_000)
})

const validation = computed(() => rev.value?.validationSummary ?? null)

const n = (v: number | null) => (v == null ? '—' : v.toLocaleString())
</script>

<template>
  <div class="flex flex-col gap-3">
    <div class="flex items-center gap-2 text-sm font-medium text-muted">
      <UIcon name="i-lucide-database" class="size-4" />
      Feed
      <span v-if="feed" class="text-dimmed">· {{ feed.source }}</span>
    </div>

    <div v-if="loading && !feed" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <USkeleton v-for="i in 4" :key="i" class="h-26 w-full" />
    </div>

    <p v-else-if="error || !feed" class="text-sm text-dimmed">Feed metadata unavailable.</p>

    <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <UCard :ui="{ body: 'flex flex-col gap-2' }">
        <div class="flex items-center gap-2 text-sm text-muted">
          <UIcon name="i-lucide-git-commit-horizontal" class="size-4 shrink-0" />
          Active revision
        </div>
        <div class="flex items-center gap-2">
          <UBadge :color="statusMeta.color" variant="subtle">{{ statusMeta.label }}</UBadge>
          <span v-if="rev" class="text-sm text-highlighted">#{{ rev.id }}</span>
        </div>
        <div v-if="rev?.activatedAt" class="text-xs text-dimmed">
          activated {{ formatTimeAgo(new Date(rev.activatedAt)) }}
        </div>
      </UCard>

      <UCard :ui="{ body: 'flex flex-col gap-2' }">
        <div class="flex items-center gap-2 text-sm text-muted">
          <UIcon name="i-lucide-calendar-range" class="size-4 shrink-0" />
          Service window
        </div>
        <div class="text-sm font-semibold text-highlighted">
          {{ gtfsDate(rev?.feedStartDate) }} → {{ gtfsDate(rev?.feedEndDate) }}
        </div>
        <div v-if="daysLeft != null" class="text-xs text-dimmed">
          {{ daysLeft >= 0 ? `${daysLeft} days left` : `expired ${-daysLeft} days ago` }}
        </div>
      </UCard>

      <UCard :ui="{ body: 'flex flex-col gap-2' }">
        <div class="flex items-center gap-2 text-sm text-muted">
          <UIcon name="i-lucide-route" class="size-4 shrink-0" />
          Content
        </div>
        <div class="text-2xl font-semibold text-highlighted">
          {{ n(rowCount(counts, 'route')) }} routes
        </div>
        <div class="text-xs text-dimmed">
          {{ n(rowCount(counts, 'stop')) }} stops · {{ n(rowCount(counts, 'trip')) }} trips ·
          {{ n(rowCount(counts, 'agency')) }} agencies
        </div>
      </UCard>

      <UCard :ui="{ body: 'flex flex-col gap-2' }">
        <div class="flex items-center gap-2 text-sm text-muted">
          <UIcon name="i-lucide-shield-check" class="size-4 shrink-0" />
          Validation
        </div>
        <div class="flex flex-wrap items-center gap-1.5">
          <UBadge :color="validation?.errorCount ? 'error' : 'success'" variant="subtle">
            {{ validation ? validation.errorCount : 0 }} errors
          </UBadge>
          <UBadge v-if="validation?.warningCount" color="warning" variant="subtle">
            {{ validation.warningCount }} warnings
          </UBadge>
        </div>
        <div class="text-xs text-dimmed">
          <template v-if="info?.feedVersion">v{{ info.feedVersion }} · </template>
          {{ info?.feedPublisherName ?? 'no feed_info.txt' }}
        </div>
      </UCard>
    </div>
  </div>
</template>
