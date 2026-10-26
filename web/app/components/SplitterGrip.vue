<script setup lang="ts">
withDefaults(
  defineProps<{
    orientation?: 'horizontal' | 'vertical'
  }>(),
  {
    orientation: 'horizontal',
  },
)
</script>

<template>
  <span
    class="pointer-events-none absolute inset-0 z-10 flex items-center justify-center text-dimmed transition-colors group-hover:text-primary"
    aria-hidden="true"
  >
    <UIcon
      :name="orientation === 'horizontal' ? 'i-lucide-grip-vertical' : 'i-lucide-grip-horizontal'"
      class="size-3.5"
    />
  </span>
</template>
