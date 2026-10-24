<script setup lang="ts">
import { useDashboard } from '~/composables/useDashboard'

const props = defineProps<{
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
  /**
   * For full-bleed map/canvas pages: at desktop width the content slot fills
   * the entire page edge-to-edge instead of sitting in a bordered, padded
   * box. Pages like this typically skip AppPage's own title/actions and
   * render their own floating panel(s) over the content instead.
   */
  floating?: boolean
}>()

const slots = useSlots()
const hasHeader = computed(() => !!slots.actions || !!slots.leading)
const containerClass = computed(() => (props.floating ? '' : 'max-w-(--app-content-max-w)'))

// Every AppPage asserts the header's transparency on mount, so the next
// page's AppPage always overwrites this before the previous one unmounts —
// an unmount-time reset here would race that assertion (the old page's
// unmount can fire after the new page's mount) and intermittently leave the
// header in the wrong state when navigating back and forth.
const { transparentHeader } = useDashboard()
watch(
  () => props.floating,
  (floating) => {
    transparentHeader.value = !!floating
  },
  { immediate: true },
)
</script>

<template>
  <div
    class="relative"
    :class="
      fullBleed
        ? 'flex h-[calc(100vh-var(--ui-header-height))] flex-col'
        : 'flex min-h-[calc(100vh-var(--ui-header-height))] flex-col'
    "
  >
    <div
      v-if="title || hasHeader"
      class="shrink-0 bg-linear-to-r from-[var(--ui-bg-elevated)] to-[var(--ui-bg-accented)] shadow-md z-10"
    >
      <UContainer
        :class="[containerClass, 'flex min-h-16 items-center justify-between gap-4 py-2.5']"
      >
        <div class="flex min-w-0 items-center gap-3">
          <slot name="leading" />
          <div class="min-w-0">
            <h1 v-if="title" class="truncate text-lg font-semibold text-highlighted tracking-tight">
              {{ title }}
            </h1>
            <p v-if="description" class="truncate text-sm text-muted leading-snug">
              {{ description }}
            </p>
          </div>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <slot name="actions" />
        </div>
      </UContainer>
    </div>

    <div v-if="slots.toolbar" data-testid="app-page-toolbar" class="shrink-0 mt-2 border-default">
      <UContainer :class="containerClass">
        <slot name="toolbar" />
      </UContainer>
    </div>

    <div
      v-if="floating"
      data-testid="app-page-floating"
      class="flex min-h-0 flex-1 flex-col lg:absolute lg:inset-0"
    >
      <slot />
    </div>
    <UContainer
      v-else-if="fullBleed"
      data-testid="app-page-bleed"
      class="flex min-h-0 flex-1 flex-col py-4"
      :class="[containerClass, fullWidth && 'max-w-none! px-0! py-0!']"
    >
      <div class="flex min-h-0 flex-1 flex-col overflow-hidden border border-default">
        <slot />
      </div>
    </UContainer>
    <UContainer
      v-else
      data-testid="app-page-container"
      :class="[containerClass, 'flex flex-1 flex-col gap-2 py-0']"
    >
      <slot />
    </UContainer>
  </div>
</template>
