<script setup lang="ts">
import { useFeeds } from '~/composables/useFeeds'
import { useDashboard } from '~/composables/useDashboard'

const { t } = useI18n()
const timeAgo = useRelativeTime()
const { isNotificationsSlideoverOpen } = useDashboard()

const { avlFeeds } = useFeeds()

interface FeedNotification {
  code: string
  name: string
  status: string
  date: string | null
  healthy: boolean
}

const notifications = computed<FeedNotification[]>(() =>
  avlFeeds.value.map((f) => {
    const status = f.lastPollStatus ?? t('notifications.neverPolled')
    return {
      code: f.code,
      name: f.name,
      status,
      date: f.lastPollAt ?? null,
      healthy: /ok|success|healthy/i.test(f.lastPollStatus ?? ''),
    }
  }),
)
</script>

<template>
  <USlideover v-model:open="isNotificationsSlideoverOpen" :title="$t('notifications.title')">
    <template #body>
      <NuxtLink
        v-for="n in notifications"
        :key="n.code"
        to="/"
        class="relative -mx-3 flex items-center gap-3 rounded-md px-3 py-2.5 first:-mt-3 last:-mb-3 hover:bg-elevated/50"
      >
        <UChip :color="n.healthy ? 'success' : 'error'" :show="!n.healthy" inset>
          <UAvatar
            :icon="n.healthy ? 'i-lucide-rss' : 'i-lucide-triangle-alert'"
            size="md"
          />
        </UChip>

        <div class="flex-1 text-sm">
          <p class="flex items-center justify-between">
            <span class="font-medium text-highlighted">{{ n.name }}</span>
            <time
              v-if="n.date"
              :datetime="n.date"
              class="text-xs text-muted"
              v-text="timeAgo(n.date)"
            />
          </p>
          <p class="text-dimmed">{{ $t('notifications.lastPoll', { status: n.status }) }}</p>
        </div>
      </NuxtLink>

      <p v-if="!notifications.length" class="p-3 text-sm text-muted">{{ $t('common.noFeedsConfigured') }}</p>
    </template>
  </USlideover>
</template>
