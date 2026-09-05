<script setup lang="ts">
import { useAvlFeedsQuery } from '../../generated/graphql'
import { useDashboardSelection } from '../composables/useDashboardSelection'

const { result } = useAvlFeedsQuery(() => ({ pollInterval: 60_000 }))
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
</script>

<template>
  <div class="flex items-center gap-4">
    <USelectMenu
      :model-value="selectedFeedCode"
      :items="options"
      value-key="value"
      placeholder="Select a feed"
      @update:model-value="selectFeed"
    />
    <span v-if="selectedFeed" class="text-sm text-gray-500">
      {{ selectedFeed.name }} — last poll: {{ selectedFeed.lastPollStatus ?? 'n/a' }}
    </span>
  </div>
</template>
