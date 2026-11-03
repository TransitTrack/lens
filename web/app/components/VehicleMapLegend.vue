<script setup lang="ts">
const { t } = useI18n()
const items = computed(() => [
  { color: '#22c55e', label: t('vehicles.legend.matched') },
  { color: '#f59e0b', label: t('vehicles.legend.stale') },
  { color: '#9ca3af', label: t('vehicles.legend.unmatched') },
])
</script>

<template>
  <div
    class="pointer-events-none flex flex-col gap-1 rounded-md bg-default/85 px-2.5 py-2 text-xs shadow ring ring-default backdrop-blur"
  >
    <div v-for="item in items" :key="item.label" class="flex items-center gap-2">
      <span
        class="size-2.5 rounded-full ring-2 ring-white"
        :style="{ backgroundColor: item.color }"
      />
      <span class="text-muted">{{ item.label }}</span>
    </div>
    <div class="mt-0.5 flex items-center gap-2 border-t border-default pt-1">
      <UIcon name="i-lucide-navigation" class="size-3 text-dimmed" />
      <span class="text-muted">{{ $t('vehicles.legend.heading') }}</span>
    </div>
  </div>
</template>
