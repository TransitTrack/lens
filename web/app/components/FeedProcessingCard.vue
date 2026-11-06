<script setup lang="ts">
import { formatNumber } from '~/utils/intlLocale'
import {useFeeds} from '~/composables/useFeeds'
import {useFeedDetailQuery, useAvlFeedsQuery} from '~~/generated/graphql'
import {revisionStatusMeta, cronSummary, PIPELINE_STEPS} from '~/utils/feedStatus'

const { t, locale } = useI18n()
const timeAgo = useRelativeTime()
const {selectedFeedCode, selectedFeed} = useFeeds()

const {result, loading, error} = useFeedDetailQuery(() => ({
  variables: { code: selectedFeedCode.value ?? '' },
  enabled: !!selectedFeedCode.value, pollInterval: 30_000,
}))
const {result: avlResult} = useAvlFeedsQuery(() => ({pollInterval: 30_000}))

const feed = computed(() => result.value?.feed ?? null)

// DRAFT revisions belong to the schedule editor, not the ingest pipeline.
const revisions = computed(() =>
  [...(feed.value?.revisions ?? [])]
    .filter((r) => r.status !== 'DRAFT')
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
)
const latest = computed(() => revisions.value[0] ?? null)
const failedCount = computed(() => revisions.value.filter((r) => r.status === 'FAILED').length)

const latestMeta = computed(() => revisionStatusMeta(latest.value?.status, t))

const avl = computed(
  () =>
    (avlResult.value?.avlFeeds ?? []).find((f) => f.code === selectedFeed.value?.avlFeedCode) ??
    null,
)
const avlHealthy = computed(() => /ok|success|healthy/i.test(avl.value?.lastPollStatus ?? ''))
</script>

<template>
  <UCard :ui="{ body: 'flex flex-col gap-4' }">
    <div class="flex items-center gap-2 text-sm font-medium text-muted">
      <UIcon name="i-lucide-cpu" class="size-4"/>
      {{ $t('feed.processing.title') }}
    </div>

    <div v-if="loading && !feed" class="flex flex-col gap-3">
      <USkeleton class="h-8 w-full"/>
      <USkeleton class="h-16 w-full"/>
    </div>

    <p v-else-if="error || !feed" class="text-sm text-dimmed">{{ $t('feed.metadataUnavailable') }}</p>

    <template v-else>
      <!-- Pipeline -->
      <div class="flex flex-col gap-2">
        <div class="flex items-center justify-between text-xs text-dimmed">
          <span>{{ $t('feed.processing.pipeline') }}</span>
          <UBadge :color="latestMeta.color" variant="subtle">{{
              latestMeta.label
            }}
          </UBadge>
        </div>
        <div class="flex items-center gap-1">
          <template v-for="(step, i) in PIPELINE_STEPS" :key="step.key">
            <div
              class="h-1.5 flex-1 rounded-full"
              :class="
                latestMeta.step < 0
                  ? 'bg-error/40'
                  : i <= latestMeta.step
                    ? 'bg-primary'
                    : 'bg-elevated'
              "
            />
          </template>
        </div>
        <div class="flex justify-between text-[11px] text-dimmed">
          <span v-for="step in PIPELINE_STEPS" :key="step.key">{{ $t(step.labelKey) }}</span>
        </div>
        <p v-if="latest?.errorMessage" class="text-xs text-error">{{ latest.errorMessage }}</p>
      </div>

      <!-- Ingestion + schedule -->
      <dl class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
        <dt class="text-dimmed">{{ $t('feed.processing.lastIngest') }}</dt>
        <dd>
          {{ latest ? timeAgo(latest.createdAt) : '—' }}
          <span class="text-dimmed">
            · {{ $t('feed.processing.revisions', { count: revisions.length }, revisions.length) }}
            <template v-if="failedCount">
              ({{ $t('feed.processing.failed', { count: failedCount }, failedCount) }})</template
            >
          </span>
        </dd>

        <dt class="text-dimmed">{{ $t('feed.processing.schedule') }}</dt>
        <dd class="flex items-center gap-2">
          {{ cronSummary(feed.pollingCron, t) }}
          <UBadge v-if="!feed.enabled" color="neutral" variant="subtle">{{ $t('feed.processing.disabled') }}</UBadge>
        </dd>
      </dl>

      <!-- Realtime -->
      <div v-if="avl" class="flex flex-col gap-1 border-t border-default pt-3">
        <div class="flex items-center justify-between text-xs text-dimmed">
          <span>{{ $t('feed.processing.realtimeFeed', { format: avl.format }) }}</span>
          <UBadge :color="avlHealthy ? 'success' : 'error'" variant="subtle">
            {{ avl.lastPollStatus ?? $t('common.unknown') }}
          </UBadge>
        </div>
        <div class="text-sm">
          {{ $t('feed.processing.polled') }}
          <span class="text-highlighted">
            {{ avl.lastPollAt ? timeAgo(avl.lastPollAt) : $t('common.never') }}
          </span>
          <span class="text-dimmed">
            ·
            {{
              $t(
                'feed.reports',
                { count: formatNumber(avl.lastPollReportCount ?? 0, locale) },
                avl.lastPollReportCount ?? 0,
              )
            }}
            · {{ $t('feed.processing.every', { seconds: avl.pollIntervalSec }) }}
          </span>
        </div>
      </div>
      <div v-else class="border-t border-default pt-3 text-xs text-dimmed">
        {{ $t('feed.processing.noRealtime') }}
      </div>
    </template>
  </UCard>
</template>
