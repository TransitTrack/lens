<script setup lang="ts">
import type { DropdownMenuItem } from '@nuxt/ui'
import { useFeeds } from '~/composables/useFeeds'

const { feeds, selectedFeed } = useFeeds()

function dotClass(f: { hasRealtime: boolean, realtimeHealthy: boolean }) {
  if (!f.hasRealtime) return 'bg-dimmed'
  return f.realtimeHealthy ? 'bg-success' : 'bg-warning'
}

const items = computed<DropdownMenuItem[][]>(() => [
  feeds.value.map((f) => ({
    label: f.name,
    slot: 'feed' as const,
    // switching feed always lands on that feed's overview
    to: `/${f.code}`,
    checked: f.code === selectedFeed.value?.code,
    type: 'checkbox' as const,
    feed: f,
  })),
])
</script>

<template>
  <UDropdownMenu
    :items="items"
    :content="{ align: 'start', collisionPadding: 12 }"
    :ui="{ content: 'w-64' }"
  >
    <UButton
      color="neutral"
      variant="ghost"
      size="sm"
      trailing-icon="i-lucide-chevrons-up-down"
      :ui="{ trailingIcon: 'text-dimmed' }"
    >
      <span class="flex items-center gap-2">
        <span
          v-if="selectedFeed"
          class="size-2 rounded-full"
          :class="dotClass(selectedFeed)"
        />
        <span class="max-w-[10rem] truncate">
          {{ selectedFeed?.name ?? 'Select feed' }}
        </span>
      </span>
    </UButton>

    <template #feed-leading="{ item }">
      <span class="size-2 shrink-0 rounded-full" :class="dotClass((item as any).feed)" />
    </template>
    <template #feed-trailing="{ item }">
      <span class="text-xs text-dimmed">
        {{ (item as any).feed.hasRealtime ? 'realtime' : 'static' }}
      </span>
    </template>
  </UDropdownMenu>
</template>
