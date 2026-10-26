<script setup lang="ts">
withDefaults(
  defineProps<{
    state: 'loading' | 'empty' | 'error'
    title?: string
    description?: string
    icon?: string
  }>(),
  {
    title: undefined,
    description: undefined,
    icon: undefined,
  },
)

const defaults = {
  loading: { title: 'Loading data', icon: 'i-lucide-loader-circle' },
  empty: { title: 'Nothing to show yet', icon: 'i-lucide-inbox' },
  error: { title: 'This data is unavailable', icon: 'i-lucide-triangle-alert' },
}
</script>

<template>
  <section class="app-page-state" :data-state="state" aria-live="polite">
    <div class="app-page-state__content">
      <template v-if="state === 'loading'">
        <UIcon :name="icon ?? defaults.loading.icon" class="size-5 animate-spin text-primary" />
        <p class="text-sm font-medium text-highlighted">{{ title ?? defaults.loading.title }}</p>
        <USkeleton class="h-2 w-40" />
      </template>
      <template v-else>
        <div class="app-page-state__icon">
          <UIcon :name="icon ?? defaults[state].icon" class="size-5" />
        </div>
        <div>
          <p class="font-medium text-highlighted">{{ title ?? defaults[state].title }}</p>
          <p v-if="description" class="mt-1 text-sm leading-5 text-muted">{{ description }}</p>
        </div>
        <slot name="action" />
      </template>
    </div>
  </section>
</template>
