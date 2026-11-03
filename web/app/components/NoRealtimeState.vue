<script setup lang="ts">
import { useFeeds } from '~/composables/useFeeds'

defineProps<{ what?: string }>()

const { selectedFeed, feedPath } = useFeeds()
</script>

<template>
  <div class="flex flex-1 flex-col items-center justify-center gap-3 p-8 text-center">
    <UIcon name="i-lucide-radio-tower" class="size-10 text-dimmed" />
    <div class="text-sm font-medium text-highlighted">{{ $t('noRealtime.title') }}</div>
    <p class="max-w-sm text-sm text-muted">
      {{
        $t('noRealtime.body', {
          name: selectedFeed?.name ?? $t('noRealtime.thisFeed'),
          what: what ?? $t('noRealtime.liveVehicleData'),
        })
      }}
    </p>
    <UButton
      :to="feedPath('/explore/routes')"
      color="neutral"
      variant="soft"
      icon="i-lucide-compass"
      :label="$t('noRealtime.explore')"
    />
  </div>
</template>
