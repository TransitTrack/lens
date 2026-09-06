<script setup lang="ts">
import { formatTimeAgo } from '@vueuse/core'
import { useAvlFeedsQuery } from '../../generated/graphql'
import { useDashboard } from '../composables/useDashboard'

const { isNotificationsSlideoverOpen } = useDashboard()

const { result } = useAvlFeedsQuery(() => ({ pollInterval: 60_000 }))

interface FeedNotification {
  code: string
  name: string
  status: string
  date: string | null
  healthy: boolean
}

const notifications = computed<FeedNotification[]>(() =>
  (result.value?.avlFeeds ?? []).map((f) => {
    const status = f.lastPollStatus ?? 'never polled'
    return {
      code: f.code,
      name: f.name,
      status,
      date: f.lastPollAt ?? null,
      healthy: /ok|success|healthy/i.test(status),
    }
  }),
)
</script>

<template>
  <USlideover v-model:open="isNotificationsSlideoverOpen" title="Feed status">
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
              v-text="formatTimeAgo(new Date(n.date))"
            />
          </p>
          <p class="text-dimmed">Last poll: {{ n.status }}</p>
        </div>
      </NuxtLink>

      <p v-if="!notifications.length" class="p-3 text-sm text-muted">No feeds configured.</p>
    </template>
  </USlideover>
</template>
