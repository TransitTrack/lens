<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
  /**
   * false → normal-flow page: content centered in a UContainer, body scrolls
   * with the document. true → "fill" page: content still centered in a
   * UContainer but the page is a definite height (viewport minus the app
   * header) and the body is a framed flex column, so map / split-pane children
   * can flex to fill it without scrolling the document.
   */
  fullBleed?: boolean
  /**
   * Lets a full-bleed workspace use the full application canvas instead of
   * the reading-width content container. Useful for dispatch maps and grids.
   */
  fullWidth?: boolean
}>()

const slots = useSlots()
const hasHeader = computed(() => !!slots.actions || !!slots.leading)
</script>

<template>
  <div
    :class="
      fullBleed
        ? 'flex h-[calc(100vh-var(--ui-header-height))] flex-col'
        : 'flex min-h-[calc(100vh-var(--ui-header-height))] flex-col'
    "
  >
    <div v-if="title || hasHeader" class="shrink-0 shadow drop-shadow-md bg-elevated z-10">
      <UContainer class="flex h-18 items-center justify-between gap-3">
        <div class="flex min-w-0 items-center gap-2">
          <slot name="leading" />
          <div class="min-w-0">
            <h1
              v-if="title"
              class="truncate text-lg font-semibold text-highlighted tracking-tighter"
            >
              {{ title }}
            </h1>
            <p v-if="description" class="truncate text-sm text-muted">{{ description }}</p>
          </div>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <slot name="actions" />
        </div>
      </UContainer>
    </div>

    <div v-if="slots.toolbar" data-testid="app-page-toolbar" class="shrink-0 mt-2 border-default">
      <UContainer>
        <slot name="toolbar" />
      </UContainer>
    </div>

    <UContainer
      v-if="fullBleed"
      data-testid="app-page-bleed"
      class="flex min-h-0 flex-1 flex-col py-4"
      :class="fullWidth && 'max-w-none! px-0! py-0!'"
    >
      <div class="flex min-h-0 flex-1 flex-col overflow-hidden border border-default">
        <slot />
      </div>
    </UContainer>
    <UContainer v-else data-testid="app-page-container" class="flex flex-1 flex-col gap-2 py-0">
      <slot />
    </UContainer>
  </div>
</template>
