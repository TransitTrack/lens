<script setup lang="ts">
import { useFeeds } from '~/composables/useFeeds'
import { useDashboard } from '~/composables/useDashboard'

const { feeds, selectedFeed } = useFeeds()
const { isFeedDrawerOpen } = useDashboard()

function dotClass(f: { hasRealtime: boolean; realtimeHealthy: boolean }) {
  if (!f.hasRealtime) return 'bg-dimmed'
  return f.realtimeHealthy ? 'bg-success' : 'bg-warning'
}

function statusLabel(f: { hasRealtime: boolean; realtimeHealthy: boolean }) {
  if (!f.hasRealtime) return 'static only'
  return f.realtimeHealthy ? 'realtime · healthy' : 'realtime · degraded'
}
</script>

<template>
  <USlideover
    v-model:open="isFeedDrawerOpen"
    title="Select feed"
    :ui="{ content: 'ring ring-default shadow-xl' }"
  >
    <template #body>
      <NuxtLink
        v-for="f in feeds"
        :key="f.code"
        :to="`/${f.code}`"
        class="-mx-3 flex items-center gap-3 rounded-md px-3 py-2.5 first:-mt-3 last:-mb-3 hover:bg-elevated/50"
        :class="{ 'bg-elevated/60': f.code === selectedFeed?.code }"
        @click="isFeedDrawerOpen = false"
      >
        <span class="mt-1 size-2.5 shrink-0 rounded-full" :class="dotClass(f)" />
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="truncate text-sm font-medium text-highlighted">{{ f.name }}</span>
            <UIcon
              v-if="f.code === selectedFeed?.code"
              name="i-lucide-check"
              class="size-4 shrink-0 text-primary"
            />
          </div>
          <div class="text-xs text-dimmed">{{ f.code }} · {{ statusLabel(f) }}</div>
        </div>
      </NuxtLink>

      <p v-if="!feeds.length" class="p-3 text-sm text-muted">No feeds configured.</p>
    </template>
  </USlideover>
</template>
