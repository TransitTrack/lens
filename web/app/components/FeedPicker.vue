<script setup lang="ts">
import { useAvlFeedsQuery } from '../../generated/graphql'
import { useDashboardSelection } from '../composables/useDashboardSelection'

const { result, error } = useAvlFeedsQuery(() => ({ pollInterval: 60_000 }))
const feeds = computed(() => result.value?.avlFeeds ?? [])

const { selectedFeedCode, selectFeed } = useDashboardSelection()

const options = computed(() => feeds.value.map((f) => ({ label: f.name, value: f.code })))

watch(
  feeds,
  (list) => {
    if (!selectedFeedCode.value && list.length > 0) {
      selectFeed(list[0]!.code)
    }
  },
  { immediate: true },
)

const selectedFeed = computed(
  () => feeds.value.find((f) => f.code === selectedFeedCode.value) ?? null,
)

function formatLastPollAt(iso: string | null | undefined): string {
  if (!iso) return 'n/a'
  return new Intl.DateTimeFormat(undefined, {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(iso))
}
</script>

<template>
  <div class="flex flex-col gap-2">
    <USelectMenu
      :model-value="selectedFeedCode"
      :items="options"
      value-key="value"
      placeholder="Select a feed"
      class="w-full"
      @update:model-value="selectFeed"
    />
    <p v-if="selectedFeed" class="text-xs text-muted">
      Last poll: {{ selectedFeed.lastPollStatus ?? 'n/a' }} ({{
        formatLastPollAt(selectedFeed.lastPollAt)
      }})
    </p>
    <p v-if="error" class="text-xs text-error">Feed list unavailable: {{ error.message }}</p>
  </div>
</template>
