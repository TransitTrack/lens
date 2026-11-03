<script setup lang="ts">
import { formatNumber } from '~/utils/intlLocale'
import { useFeeds } from '~/composables/useFeeds'
import { useFeedDetailQuery } from '~~/generated/graphql'
import { gtfsDate } from '~/utils/gtfs'
import { revisionStatusMeta, rowCount } from '~/utils/feedStatus'

const { t, locale } = useI18n()
const timeAgo = useRelativeTime()
const { selectedFeedCode } = useFeeds()

const { result, loading, error } = useFeedDetailQuery(
  () => ({ code: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value, pollInterval: 60_000 }),
)

const feed = computed(() => result.value?.feed ?? null)
const info = computed(() => result.value?.feedInfo ?? null)
const rev = computed(() => feed.value?.activeRevision ?? null)
const counts = computed(() => (rev.value?.rowCounts ?? null) as Record<string, number> | null)

const statusMeta = computed(() => revisionStatusMeta(rev.value?.status, t))

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
  context: ValidationSampleField[]
  fields: ValidationSampleField[]
  fallback: string | null
}

interface ValidationSampleField {
  label: string
  value: string
}

function labelForValidationKey(key: string): string {
  return key
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/_/g, ' ')
    .replace(/^./, (character) => character.toUpperCase())
}

function displayValidationValue(value: unknown): string {
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value)
}

function parseValidationSample(raw: string): ValidationSample {
  if (!raw) {
    return { context: [], fields: [], fallback: null }
  }

  try {
    const value = JSON.parse(raw) as unknown
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return { context: [], fields: [], fallback: raw }
    }
    const record = value as Record<string, unknown>
    const entries = Object.entries(record)
      .filter(([, entry]) => entry != null)
      .map(([key, entry]) => ({
        label: labelForValidationKey(key),
        value: displayValidationValue(entry),
      }))
    return {
      context: entries.filter(({ label }) => label === 'Filename' || label === 'Csv Row Number'),
      fields: entries.filter(({ label }) => label !== 'Filename' && label !== 'Csv Row Number'),
      fallback: null,
    }
  } catch {
    return { context: [], fields: [], fallback: raw }
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

// Counts keep the existing "N errors" wording; the plural forms are locale data.
const errorsText = computed(() => {
  const count = validation.value ? validation.value.errorCount : 0
  return t('feed.overview.errors', { count }, count)
})
const warningsText = computed(() => {
  const count = validation.value?.warningCount ?? 0
  return t('feed.overview.warnings', { count }, count)
})

function noticeCountText(notice: { severity: string; count: number }): string {
  const params = { count: formatNumber(notice.count, locale.value) }
  return notice.severity === 'ERROR'
    ? t('feed.overview.noticeError', params, notice.count)
    : t('feed.overview.noticeWarning', params, notice.count)
}

const n = (v: number | null) => (v == null ? '—' : formatNumber(v, locale.value))

function noticeColor(severity: string): 'error' | 'warning' {
  return severity === 'ERROR' ? 'error' : 'warning'
}
</script>

<template>
  <div class="flex flex-col gap-3">
    <div class="flex items-center gap-2 text-sm font-medium text-muted">
      <UIcon name="i-lucide-database" class="size-4" />
      {{ $t('feed.overview.title') }}
      <span v-if="feed" class="text-dimmed">· {{ feed.source }}</span>
    </div>

    <div v-if="loading && !feed" class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <USkeleton v-for="i in 4" :key="i" class="h-26 w-full" />
    </div>

    <p v-else-if="error || !feed" class="text-sm text-dimmed">{{ $t('feed.metadataUnavailable') }}</p>

    <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <UCard :ui="{ body: 'flex flex-col gap-2' }">
        <div class="flex items-center gap-2 text-sm text-muted">
          <UIcon name="i-lucide-git-commit-horizontal" class="size-4 shrink-0" />
          {{ $t('feed.overview.activeRevision') }}
        </div>
        <div class="flex items-center gap-2">
          <UBadge :color="statusMeta.color" variant="subtle">{{ statusMeta.label }}</UBadge>
          <span v-if="rev" class="text-sm text-highlighted">#{{ rev.id }}</span>
        </div>
        <div v-if="rev?.activatedAt" class="text-xs text-dimmed">
          {{ $t('feed.overview.activated', { time: timeAgo(rev.activatedAt) }) }}
        </div>
      </UCard>

      <UCard :ui="{ body: 'flex flex-col gap-2' }">
        <div class="flex items-center gap-2 text-sm text-muted">
          <UIcon name="i-lucide-calendar-range" class="size-4 shrink-0" />
          {{ $t('feed.overview.serviceWindow') }}
        </div>
        <div class="text-sm font-semibold text-highlighted">
          {{ gtfsDate(rev?.feedStartDate, locale) }} → {{ gtfsDate(rev?.feedEndDate, locale) }}
        </div>
        <div v-if="daysLeft != null" class="text-xs text-dimmed">
          {{
            daysLeft >= 0
              ? $t('feed.overview.daysLeft', { count: daysLeft }, daysLeft)
              : $t('feed.overview.expired', { count: -daysLeft }, -daysLeft)
          }}
        </div>
      </UCard>

      <UCard :ui="{ body: 'flex flex-col gap-2' }">
        <div class="flex items-center gap-2 text-sm text-muted">
          <UIcon name="i-lucide-route" class="size-4 shrink-0" />
          {{ $t('feed.overview.content') }}
        </div>
        <div class="text-2xl font-semibold text-highlighted">
          {{ $t('feed.overview.routes', { count: n(rowCount(counts, 'route')) }, rowCount(counts, 'route') ?? 0) }}
        </div>
        <div class="text-xs text-dimmed">
          {{ $t('feed.overview.stops', { count: n(rowCount(counts, 'stop')) }, rowCount(counts, 'stop') ?? 0) }}
          · {{ $t('feed.overview.trips', { count: n(rowCount(counts, 'trip')) }, rowCount(counts, 'trip') ?? 0) }}
          ·
          {{ $t('feed.overview.agencies', { count: n(rowCount(counts, 'agency')) }, rowCount(counts, 'agency') ?? 0) }}
        </div>
      </UCard>

      <UCard :ui="{ body: 'flex flex-col gap-2' }">
        <div class="flex items-center gap-2 text-sm text-muted">
          <UIcon name="i-lucide-shield-check" class="size-4 shrink-0" />
          {{ $t('feed.overview.validation') }}
        </div>
        <div class="flex flex-wrap items-center gap-1.5">
          <button
            type="button"
            class="rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            :aria-label="$t('feed.overview.viewProblems')"
            @click="validationOpen = true"
          >
            <UBadge :color="validation?.errorCount ? 'error' : 'success'" variant="subtle">
              {{ errorsText }}
            </UBadge>
          </button>
          <button
            v-if="validation?.warningCount"
            type="button"
            class="rounded-full focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            :aria-label="$t('feed.overview.viewProblems')"
            @click="validationOpen = true"
          >
            <UBadge color="warning" variant="subtle">
              {{ warningsText }}
            </UBadge>
          </button>
        </div>
        <div class="text-xs text-dimmed">
          <template v-if="info?.feedVersion">v{{ info.feedVersion }} · </template>
          {{ info?.feedPublisherName ?? $t('feed.overview.noFeedInfo') }}
        </div>
      </UCard>
    </div>

    <UModal
      v-model:open="validationOpen"
      :title="$t('feed.overview.validationProblems')"
      :description="
        validation
          ? $t('feed.overview.validationSummary', { errors: errorsText, warnings: warningsText })
          : $t('feed.overview.noReport')
      "
    >
      <template #body>
        <div v-if="validationNotices.length" class="max-h-[60vh] space-y-3 overflow-y-auto pr-1">
          <article
            v-for="notice in validationNotices"
            :key="`${notice.severity}:${notice.rule}`"
            class="rounded-md border border-default bg-elevated/30 p-3"
          >
            <div class="flex flex-wrap items-center justify-between gap-2">
              <div class="min-w-0 font-medium text-highlighted">{{ notice.rule }}</div>
              <UBadge :color="noticeColor(notice.severity)" variant="subtle">
                {{ noticeCountText(notice) }}
              </UBadge>
            </div>
            <div
              v-if="notice.sampleDetails.context.length || notice.sampleDetails.fields.length"
              class="mt-3 space-y-2 text-sm"
            >
              <div
                v-if="notice.sampleDetails.context.length"
                class="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-dimmed"
              >
                <span
                  v-for="field in notice.sampleDetails.context"
                  :key="field.label"
                  class="font-medium text-muted"
                >
                  {{ field.label === 'Csv Row Number' ? $t('feed.overview.row', { n: field.value }) : field.value }}
                </span>
              </div>
              <dl v-if="notice.sampleDetails.fields.length" class="space-y-2">
                <div v-for="field in notice.sampleDetails.fields" :key="field.label">
                  <dt class="text-xs font-medium uppercase tracking-wide text-dimmed">
                    {{ field.label }}
                  </dt>
                  <dd
                    class="mt-1 whitespace-pre-wrap break-words rounded-md bg-default/70 p-2 text-sm leading-6 text-muted"
                  >
                    {{ field.value }}
                  </dd>
                </div>
              </dl>
            </div>
            <p
              v-else-if="notice.sampleDetails.fallback"
              class="mt-2 whitespace-pre-wrap break-words rounded-md bg-default/70 p-2 text-sm leading-6 text-muted"
            >
              {{ notice.sampleDetails.fallback }}
            </p>
            <p v-else class="mt-2 text-sm text-dimmed">
              {{ $t('feed.overview.noSample') }}
            </p>
          </article>
        </div>
        <div v-else class="rounded-md border border-default bg-elevated/30 p-4 text-sm text-muted">
          {{
            validation
              ? $t('feed.overview.noProblems')
              : $t('feed.overview.runIngest')
          }}
        </div>
      </template>
    </UModal>
  </div>
</template>
