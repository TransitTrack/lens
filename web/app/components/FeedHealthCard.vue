<script setup lang="ts">
import {formatTimeAgo} from '@vueuse/core'
import {useAvlFeedsQuery} from '../../generated/graphql'

const {result, loading} = useAvlFeedsQuery(() => ({pollInterval: 60_000}))

const feeds = computed(() => result.value?.avlFeeds ?? [])

function healthy(status: string | null | undefined): boolean {
  return /ok|success|healthy/i.test(status ?? '')
}
</script>

<template>
  <UCard :ui="{ body: 'flex flex-col gap-3' }">
    <div class="flex items-center gap-2 text-sm font-medium text-muted">
      <UIcon name="i-lucide-rss" class="size-4"/>
      Feed health
    </div>

    <div v-if="loading && !feeds.length" class="flex flex-col gap-2">
      <USkeleton v-for="i in 2" :key="i" class="h-9 w-full"/>
    </div>

    <div v-else class="flex flex-col divide-y divide-default">
      <div
        v-for="feed in feeds"
        :key="feed.code"
        class="flex items-center justify-between gap-3 py-2 first:pt-0 last:pb-0"
      >
        <div class="min-w-0">
          <div class="truncate text-sm font-medium text-highlighted">{{ feed.name }}</div>
          <div class="text-xs text-dimmed">
            {{ feed.lastPollReportCount ?? 0 }} reports ·
            {{ feed.lastPollAt ? formatTimeAgo(new Date(feed.lastPollAt)) : 'never' }}
          </div>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <UBadge v-if="!feed.enabled" color="neutral" variant="subtle">Disabled</UBadge>
          <UBadge
            :color="healthy(feed.lastPollStatus) ? 'success' : 'error'"
            variant="subtle"
          >
            {{ feed.lastPollStatus ?? 'unknown' }}
          </UBadge>
        </div>
      </div>
    </div>
  </UCard>
</template>
