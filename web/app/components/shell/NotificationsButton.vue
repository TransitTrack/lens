<script setup lang="ts">
import { useFeeds } from '~/composables/useFeeds'

const { t } = useI18n()
const { isNotificationsSlideoverOpen } = useDashboard()
const { feeds } = useFeeds()

/** Feeds that publish realtime data but are currently unhealthy. */
const count = computed(
  () => feeds.value.filter((feed) => feed.hasRealtime && !feed.realtimeHealthy).length,
)

const label = computed(() =>
  count.value
    ? t('notifications.openWithIssues', { count: count.value }, count.value)
    : t('notifications.openFeedStatus'),
)
</script>

<template>
  <UTooltip :text="label">
    <UChip :show="!!count" :text="count" color="error" size="lg">
      <UButton
        icon="i-lucide-bell"
        color="neutral"
        :variant="isNotificationsSlideoverOpen ? 'soft' : 'ghost'"
        square
        :aria-label="label"
        @click="isNotificationsSlideoverOpen = !isNotificationsSlideoverOpen"
      />
    </UChip>
  </UTooltip>
</template>
