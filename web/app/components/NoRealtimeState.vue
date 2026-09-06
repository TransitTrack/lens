<script setup lang="ts">
import { useFeeds } from '~/composables/useFeeds'

defineProps<{ what?: string }>()

const { selectedFeed, feedPath } = useFeeds()
</script>

<template>
  <div class="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center">
    <UIcon name="i-lucide-radio-tower" class="size-10 text-dimmed" />
    <div class="text-sm font-medium text-highlighted">No realtime source for this feed</div>
    <p class="max-w-sm text-sm text-muted">
      {{ selectedFeed?.name ?? 'This feed' }} has no linked AVL feed, so
      {{ what ?? 'live vehicle data' }} isn't available. Explore the static GTFS data instead.
    </p>
    <UButton
      :to="feedPath('/explore/routes')"
      color="neutral"
      variant="soft"
      icon="i-lucide-compass"
      label="Explore feed"
    />
  </div>
</template>
