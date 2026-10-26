<script setup lang="ts">
const props = defineProps<{ count?: number }>()
const { isNotificationsSlideoverOpen } = useDashboard()

const label = computed(() =>
  props.count
    ? `${props.count} feed ${props.count === 1 ? 'issue' : 'issues'} — open feed status`
    : 'Open feed status',
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
