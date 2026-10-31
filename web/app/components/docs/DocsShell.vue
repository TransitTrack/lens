<script setup lang="ts">
import type { NavigationMenuItem } from '@nuxt/ui'
import { useDocs } from '~/composables/useDocs'

/**
 * Three-column frame for the Docs pages: doc navigation (left), the page
 * (default slot) and an optional aside such as the table of contents (`aside`
 * slot). The side columns drop out below `lg`; the Docs index and ⌘K search
 * cover navigation there.
 */
const { groups, docRoute } = useDocs()
const route = useRoute()

const items = computed<NavigationMenuItem[][]>(() => [
  ...groups.value.map((g) => [
    { label: g.title, type: 'label' as const },
    ...g.docs.map((d) => ({
      label: d.title,
      to: docRoute(d.path),
      active: route.path === docRoute(d.path),
    })),
  ]),
  [
    { label: 'This instance', type: 'label' as const },
    {
      label: 'Live configuration',
      icon: 'i-lucide-sliders-horizontal',
      to: docRoute('/live-config'),
      active: route.path === docRoute('/live-config'),
    },
  ],
])
</script>

<template>
  <div
    class="grid gap-8 py-6 lg:grid-cols-[13rem_minmax(0,1fr)] xl:grid-cols-[13rem_minmax(0,1fr)_14rem]"
  >
    <nav aria-label="Documentation" class="hidden lg:block">
      <div class="sticky top-4">
        <ULink
          :to="docRoute()"
          class="mb-2 flex items-center gap-2 px-2.5 text-sm font-semibold text-highlighted"
        >
          <UIcon name="i-lucide-book-open" class="size-4 text-primary" />
          Documentation
        </ULink>
        <UNavigationMenu
          :items="items"
          orientation="vertical"
          :ui="{
            label: 'mt-3 px-2.5 text-xs uppercase tracking-wide text-dimmed',
            link: 'text-sm',
          }"
        />
      </div>
    </nav>

    <div class="min-w-0">
      <slot />
    </div>

    <aside v-if="$slots.aside" class="hidden xl:block">
      <div class="sticky top-4">
        <slot name="aside" />
      </div>
    </aside>
  </div>
</template>
