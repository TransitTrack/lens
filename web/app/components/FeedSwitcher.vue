<script setup lang="ts">
import { useFeeds } from '~/composables/useFeeds'
import { useDashboard } from '~/composables/useDashboard'

/** Trigger + dropdown for switching the selected GTFS feed, pinned to the sidebar header. */
defineProps<{
  /** Icon-only trigger for a collapsed sidebar; full name + status row otherwise. */
  collapsed?: boolean
}>()

const { feeds, selectedFeed } = useFeeds()
const { isFeedSwitcherOpen } = useDashboard()

function dotColor(f: {
  hasRealtime: boolean
  realtimeHealthy: boolean
}): 'success' | 'warning' | 'neutral' {
  if (!f.hasRealtime) return 'neutral'
  return f.realtimeHealthy ? 'success' : 'warning'
}

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
  <UPopover
    v-model:open="isFeedSwitcherOpen"
    :content="{ side: 'bottom', align: 'start', collisionPadding: 12 }"
    :ui="{ content: 'w-72' }"
  >
    <UButton
      v-if="collapsed"
      color="neutral"
      variant="ghost"
      size="xs"
      square
      aria-label="Switch feed"
      class="data-[state=open]:bg-elevated"
    >
      <UChip v-if="selectedFeed" :color="dotColor(selectedFeed)" inset>
        <UIcon name="i-lucide-rss" class="size-4 shrink-0" />
      </UChip>
      <UIcon v-else name="i-lucide-rss" class="size-4 shrink-0" />
    </UButton>

    <UButton
      v-else
      color="neutral"
      variant="ghost"
      trailing-icon="i-lucide-chevrons-up-down"
      block
      class="justify-start data-[state=open]:bg-elevated"
      :ui="{ trailingIcon: 'ms-auto text-dimmed' }"
    >
      <UChip v-if="selectedFeed" :color="dotColor(selectedFeed)" inset>
        <UIcon name="i-lucide-rss" class="size-4 shrink-0" />
      </UChip>
      <UIcon v-else name="i-lucide-rss" class="size-4 shrink-0" />
      <span class="min-w-0 flex-1 text-left">
        <span class="block truncate text-sm font-medium text-highlighted">
          {{ selectedFeed?.name ?? 'Select a feed' }}
        </span>
        <span v-if="selectedFeed" class="block truncate text-[10px] text-dimmed">
          {{ selectedFeed.code }} · {{ statusLabel(selectedFeed) }}
        </span>
      </span>
    </UButton>

    <template #content="{ close }">
      <div class="max-h-96 overflow-y-auto p-1">
        <NuxtLink
          v-for="f in feeds"
          :key="f.code"
          :to="`/${f.code}`"
          class="flex items-center gap-3 rounded-md px-2.5 py-2 hover:bg-elevated/50"
          :class="{ 'bg-elevated/60': f.code === selectedFeed?.code }"
          @click="close()"
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
      </div>
    </template>
  </UPopover>
</template>
