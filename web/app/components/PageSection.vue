<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
}>()

const slots = useSlots()
</script>

<template>
  <section class="app-section">
    <header v-if="title || description || slots.actions" class="app-section__header">
      <div class="min-w-0">
        <h2 v-if="title" class="text-sm font-semibold text-highlighted">{{ title }}</h2>
        <p v-if="description" class="mt-1 text-sm leading-5 text-muted">{{ description }}</p>
      </div>
      <div v-if="slots.actions" class="flex shrink-0 items-center gap-2">
        <slot name="actions" />
      </div>
    </header>
    <div class="app-section__body">
      <slot />
    </div>
    <footer v-if="slots.footer" class="app-section__footer">
      <slot name="footer" />
    </footer>
  </section>
</template>
