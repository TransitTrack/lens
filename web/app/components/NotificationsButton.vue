<script setup lang="ts">
const props = defineProps<{ count?: number }>()
const { t } = useI18n()
const { isNotificationsSlideoverOpen } = useDashboard()

const label = computed(() =>
  props.count
    ? t('notifications.openWithIssues', { count: props.count }, props.count)
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
