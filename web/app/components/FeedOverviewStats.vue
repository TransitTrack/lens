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
const validationOpen = ref(false)

interface ValidationSample {
  filename: string | null
  csvRowNumber: number | null
  fieldName: string | null
  fieldValue: string | null
  fallback: string | null
}

function parseValidationSample(raw: string): ValidationSample {
  if (!raw) {
    return { filename: null, csvRowNumber: null, fieldName: null, fieldValue: null, fallback: null }
  }

  try {
    const value = JSON.parse(raw) as unknown
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return {
        filename: null,
        csvRowNumber: null,
        fieldName: null,
        fieldValue: null,
        fallback: raw,
      }
    }
    const record = value as Record<string, unknown>
    const stringValue = (key: string) => (typeof record[key] === 'string' ? record[key] : null)
    const row = record.csvRowNumber
    return {
      filename: stringValue('filename'),
      csvRowNumber: typeof row === 'number' ? row : null,
      fieldName: stringValue('fieldName'),
      fieldValue: stringValue('fieldValue'),
      fallback: null,
    }
  } catch {
    return { filename: null, csvRowNumber: null, fieldName: null, fieldValue: null, fallback: raw }
  }
}

const validationNotices = computed(() =>
  [...(validation.value?.notices ?? [])]
    .filter((notice) => notice.severity === 'ERROR' || notice.severity === 'WARNING')
    .sort((a, b) => {
      const severityOrder = { ERROR: 0, WARNING: 1 }
      return (
        severityOrder[a.severity as keyof typeof severityOrder] -
        severityOrder[b.severity as keyof typeof severityOrder]
      )
    })
    .map((notice) => ({ ...notice, sampleDetails: parseValidationSample(notice.sample) })),
)

const n = (v: number | null) => (v == null ? '—' : v.toLocaleString())

function noticeColor(severity: string): 'error' | 'warning' {
  return severity === 'ERROR' ? 'error' : 'warning'
}
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
          <button
            type="button"
            class="rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            aria-label="View validation problems"
            @click="validationOpen = true"
          >
            <UBadge :color="validation?.errorCount ? 'error' : 'success'" variant="subtle">
              {{ validation ? validation.errorCount : 0 }} errors
            </UBadge>
          </button>
          <button
            v-if="validation?.warningCount"
            type="button"
            class="rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            aria-label="View validation problems"
            @click="validationOpen = true"
          >
            <UBadge color="warning" variant="subtle">
              {{ validation.warningCount }} warnings
            </UBadge>
          </button>
        </div>
        <div class="text-xs text-dimmed">
          <template v-if="info?.feedVersion">v{{ info.feedVersion }} · </template>
          {{ info?.feedPublisherName ?? 'no feed_info.txt' }}
        </div>
      </UCard>
    </div>

    <UModal
      v-model:open="validationOpen"
      title="Validation problems"
      :description="
        validation
          ? `${validation.errorCount} errors · ${validation.warningCount} warnings`
          : 'No validation report is available for this revision.'
      "
    >
      <template #body>
        <div v-if="validationNotices.length" class="max-h-[60vh] space-y-3 overflow-y-auto pr-1">
          <article
            v-for="notice in validationNotices"
            :key="`${notice.severity}:${notice.rule}`"
            class="rounded-lg border border-default bg-elevated/30 p-3"
          >
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="min-w-0 font-medium text-highlighted">{{ notice.rule }}</div>
              <UBadge :color="noticeColor(notice.severity)" variant="subtle">
                {{ notice.count.toLocaleString() }} {{ notice.severity.toLocaleLowerCase()
                }}{{ notice.count === 1 ? '' : 's' }}
              </UBadge>
            </div>
            <div
              v-if="notice.sampleDetails.filename || notice.sampleDetails.fieldName"
              class="mt-3 space-y-2 text-sm"
            >
              <div class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-dimmed">
                <span v-if="notice.sampleDetails.filename" class="font-medium text-muted">
                  {{ notice.sampleDetails.filename }}
                </span>
                <span v-if="notice.sampleDetails.csvRowNumber != null">
                  row {{ notice.sampleDetails.csvRowNumber.toLocaleString() }}
                </span>
              </div>
              <div
                v-if="notice.sampleDetails.fieldName"
                class="text-xs font-medium uppercase tracking-wide text-dimmed"
              >
                {{ notice.sampleDetails.fieldName }}
              </div>
              <p
                v-if="notice.sampleDetails.fieldValue"
                class="whitespace-pre-wrap break-words rounded-md bg-default/70 p-2 text-sm leading-6 text-muted"
              >
                {{ notice.sampleDetails.fieldValue }}
              </p>
            </div>
            <p
              v-else-if="notice.sampleDetails.fallback"
              class="mt-2 whitespace-pre-wrap break-words rounded-md bg-default/70 p-2 text-sm leading-6 text-muted"
            >
              {{ notice.sampleDetails.fallback }}
            </p>
          </article>
        </div>
        <div v-else class="rounded-lg border border-default bg-elevated/30 p-4 text-sm text-muted">
          {{
            validation
              ? 'This revision has no stored validation problems.'
              : 'Run or ingest a revision to see validation results here.'
          }}
        </div>
      </template>
    </UModal>
  </div>
</template>
