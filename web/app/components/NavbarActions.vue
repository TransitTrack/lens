<script setup lang="ts">
import PollControl from './PollControl.vue'
import FeedSwitcher from './FeedSwitcher.vue'
import { useDashboard } from '../composables/useDashboard'

defineProps<{ updatedAt?: string | number | null }>()

const { isNotificationsSlideoverOpen } = useDashboard()
</script>

<template>
  <FeedSwitcher />

  <USeparator orientation="vertical" class="h-6" />

  <PollControl :updated-at="updatedAt" />

  <UTooltip text="Feed status" :shortcuts="['N']">
    <UButton
      color="neutral"
      variant="ghost"
      square
      aria-label="Feed status"
      @click="isNotificationsSlideoverOpen = true"
    >
      <UChip color="error" inset>
        <UIcon name="i-lucide-bell" class="size-5 shrink-0" />
      </UChip>
    </UButton>
  </UTooltip>

  <UColorModeButton />
</template>
