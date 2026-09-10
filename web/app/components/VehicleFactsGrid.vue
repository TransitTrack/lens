<script setup lang="ts">
export interface Fact {
  label: string
  value: string | null | undefined
}

defineProps<{ items: Fact[] }>()
</script>

<template>
  <dl class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
    <template v-for="f in items" :key="f.label">
      <dt class="text-dimmed">{{ f.label }}</dt>
      <dd class="min-w-0 truncate text-highlighted">{{ f.value || '—' }}</dd>
    </template>
  </dl>
</template>
