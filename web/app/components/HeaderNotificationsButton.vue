<script setup lang="ts">
import NotificationsButton from '~/components/NotificationsButton.vue'
import { useFeeds } from '~/composables/useFeeds'

const { feeds } = useFeeds()
const unhealthyFeedCount = computed(
  () => feeds.value.filter((feed) => feed.hasRealtime && !feed.realtimeHealthy).length,
)
</script>

<template>
  <div class="hidden shrink-0 lg:block">
    <NotificationsButton :count="unhealthyFeedCount || undefined" />
  </div>
</template>
