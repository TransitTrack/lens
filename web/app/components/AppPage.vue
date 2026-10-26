<script setup lang="ts">
import HeaderNotificationsButton from '~/components/HeaderNotificationsButton.vue'

withDefaults(
  defineProps<{
    title?: string
    description?: string
    /**
     * true (default) → "fill" page: the page is a definite height (viewport
     * minus the app header) and the body is a framed flex column that scrolls
     * internally, so map / split-pane children can flex to fill it without
     * scrolling the document. false → normal-flow page: body scrolls with the
     * document instead.
     */
    fullBleed?: boolean
    /**
     * true (default) → the page uses the full application canvas instead of
     * the reading-width content container.
     */
    fullWidth?: boolean
  }>(),
  { title: undefined, description: undefined, fullBleed: true, fullWidth: false },
)

const slots = useSlots()
const hasHeader = computed(() => !!slots.actions || !!slots.leading)
const containerClass = 'max-w-(--app-content-max-w)'
</script>

<template>
  <div
    class="app-page relative"
    :class="fullBleed ? 'flex h-dvh flex-col' : 'flex min-h-dvh flex-col'"
  >
    <div v-if="title || hasHeader" class="app-page__header shrink-0">
      <UContainer
        :class="[
          fullWidth ? 'max-w-none!' : containerClass,
          'flex min-h-16 items-center justify-between gap-4 px-(--app-page-gutter) py-3',
        ]"
      >
        <div class="flex min-w-0 items-center gap-3">
          <slot name="leading" />
          <div class="min-w-0">
            <h1 v-if="title" class="truncate text-xl font-semibold tracking-tight text-highlighted">
              {{ title }}
            </h1>
            <p v-if="description" class="truncate text-sm text-muted leading-snug">
              {{ description }}
            </p>
          </div>
        </div>
        <div class="flex shrink-0 items-center gap-2">
          <slot name="actions" />
          <HeaderNotificationsButton />
        </div>
      </UContainer>
    </div>

    <div v-if="slots.toolbar" data-testid="app-page-toolbar" class="app-page__toolbar shrink-0">
      <UContainer :class="[fullWidth ? 'max-w-none!' : containerClass, 'px-(--app-page-gutter)']">
        <slot name="toolbar" />
      </UContainer>
    </div>

    <UContainer
      data-testid="app-page-container"
      :class="[
        'app-page__content flex flex-1 flex-col gap-4',
        fullWidth ? 'app-page__content--canvas max-w-none! h-full' : 'app-page__content--rail',
      ]"
    >
      <slot />
    </UContainer>
  </div>
</template>
