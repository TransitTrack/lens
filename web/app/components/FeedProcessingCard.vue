<script setup lang="ts">
import {formatTimeAgo} from '@vueuse/core'
import {useFeeds} from '~/composables/useFeeds'
import {useFeedDetailQuery, useAvlFeedsQuery} from '~~/generated/graphql'
import {revisionStatusMeta, cronSummary, PIPELINE_STEPS} from '~/utils/feedStatus'

const {selectedFeedCode, selectedFeed} = useFeeds()

const {result, loading, error} = useFeedDetailQuery(
  () => ({code: selectedFeedCode.value ?? ''}),
  () => ({enabled: !!selectedFeedCode.value, pollInterval: 30_000}),
)
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

const latestMeta = computed(() => revisionStatusMeta(latest.value?.status))

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
      Processing
    </div>

    <div v-if="loading && !feed" class="flex flex-col gap-3">
      <USkeleton class="h-8 w-full"/>
      <USkeleton class="h-16 w-full"/>
    </div>

    <p v-else-if="error || !feed" class="text-sm text-dimmed">Feed metadata unavailable.</p>

    <template v-else>
      <!-- Pipeline -->
      <div class="flex flex-col gap-2">
        <div class="flex items-center justify-between text-xs text-dimmed">
          <span>Latest ingest pipeline</span>
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
          <span v-for="step in PIPELINE_STEPS" :key="step.key">{{ step.label }}</span>
        </div>
        <p v-if="latest?.errorMessage" class="text-xs text-error">{{ latest.errorMessage }}</p>
      </div>

      <!-- Ingestion + schedule -->
      <dl class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
        <dt class="text-dimmed">Last ingest</dt>
        <dd>
          {{ latest ? formatTimeAgo(new Date(latest.createdAt)) : '—' }}
          <span class="text-dimmed">
            · {{ revisions.length }} revision{{ revisions.length === 1 ? '' : 's' }}
            <template v-if="failedCount"> ({{ failedCount }} failed)</template>
          </span>
        </dd>

        <dt class="text-dimmed">Schedule</dt>
        <dd class="flex items-center gap-2">
          {{ cronSummary(feed.pollingCron) }}
          <UBadge v-if="!feed.enabled" color="neutral" variant="subtle">disabled</UBadge>
        </dd>
      </dl>

      <!-- Realtime -->
      <div v-if="avl" class="flex flex-col gap-1 border-t border-default pt-3">
        <div class="flex items-center justify-between text-xs text-dimmed">
          <span>Realtime feed · {{ avl.format }}</span>
          <UBadge :color="avlHealthy ? 'success' : 'error'" variant="subtle">
            {{ avl.lastPollStatus ?? 'unknown' }}
          </UBadge>
        </div>
        <div class="text-sm">
          polled
          <span class="text-highlighted">
            {{ avl.lastPollAt ? formatTimeAgo(new Date(avl.lastPollAt)) : 'never' }}
          </span>
          <span class="text-dimmed">
            · {{ (avl.lastPollReportCount ?? 0).toLocaleString() }} reports · every
            {{ avl.pollIntervalSec }}s
          </span>
        </div>
      </div>
      <div v-else class="border-t border-default pt-3 text-xs text-dimmed">
        No realtime feed linked.
      </div>
    </template>
  </UCard>
</template>
