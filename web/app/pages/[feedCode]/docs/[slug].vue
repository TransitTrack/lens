<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import DocsShell from '~/components/docs/DocsShell.vue'
import PageState from '~/components/PageState.vue'
import { useDocs } from '~/composables/useDocs'

const route = useRoute()
const slug = computed(() => String(route.params.slug))
const { ordered, docRoute } = useDocs()

const { data: page, status } = useAsyncData(
  () => `doc-${slug.value}`,
  () => queryCollection('docs').path(`/${slug.value}`).first(),
)

const position = computed(() => ordered.value.findIndex((d) => d.path === `/${slug.value}`))
const prev = computed(() => (position.value > 0 ? ordered.value[position.value - 1] : null))
const next = computed(() =>
  position.value >= 0 ? (ordered.value[position.value + 1] ?? null) : null,
)

// The dashboard scrolls inside its panel, not the window, so the router's
// hash scrolling can't reach headings — bring them into view ourselves. The
// doc renders a moment after its data arrives (cross-doc links like
// `avl#_6-retention`, deep links), so retry briefly until the heading exists;
// then keep it pinned while diagrams above it render and shift the layout,
// until the reader scrolls themselves.
const article = ref<HTMLElement | null>(null)
let pendingScroll: ReturnType<typeof setTimeout> | undefined
let pinnedHeading: HTMLElement | null = null
function unpin() {
  pinnedHeading = null
}
function scrollToHash(attemptsLeft = 20) {
  clearTimeout(pendingScroll)
  unpin()
  const id = decodeURIComponent(route.hash.slice(1))
  if (!id || !page.value) return
  const heading = document.getElementById(id)
  if (heading) {
    heading.scrollIntoView({ block: 'start' })
    pinnedHeading = heading
    pendingScroll = setTimeout(unpin, 3000)
  } else if (attemptsLeft > 0) {
    pendingScroll = setTimeout(() => scrollToHash(attemptsLeft - 1), 100)
  }
}
watch([() => route.hash, page], () => scrollToHash(), { flush: 'post', immediate: true })
useResizeObserver(article, () => pinnedHeading?.scrollIntoView({ block: 'start' }))
for (const event of ['wheel', 'touchmove', 'keydown', 'pointerdown'] as const) {
  useEventListener(event, unpin, { passive: true })
}
onBeforeUnmount(() => clearTimeout(pendingScroll))
</script>

<template>
  <AppPage title="Documentation" :description="page?.title" :full-bleed="false">
    <DocsShell>
      <PageState v-if="status === 'pending' && !page" state="loading" title="Loading document" />
      <PageState
        v-else-if="!page"
        state="empty"
        icon="i-lucide-file-question"
        title="No such document"
        description="It may have been renamed or removed from docs/."
      >
        <template #action>
          <UButton :to="docRoute()" color="neutral" variant="subtle" icon="i-lucide-book-open"
            >All docs</UButton
          >
        </template>
      </PageState>
      <template v-else>
        <article ref="article" class="max-w-4xl">
          <ContentRenderer :value="page" />
        </article>
        <nav
          v-if="prev || next"
          aria-label="Previous and next document"
          class="mt-10 grid max-w-4xl gap-3 border-t border-default pt-6 sm:grid-cols-2"
        >
          <ULink
            v-if="prev"
            :to="docRoute(prev.path)"
            class="rounded-lg border border-default p-4 transition-colors hover:border-primary/50"
          >
            <span class="text-xs text-dimmed">Previous</span>
            <span class="mt-1 flex items-center gap-1 font-medium text-highlighted">
              <UIcon name="i-lucide-arrow-left" class="size-4" />{{ prev.title }}
            </span>
          </ULink>
          <ULink
            v-if="next"
            :to="docRoute(next.path)"
            class="rounded-lg border border-default p-4 text-right transition-colors hover:border-primary/50 sm:col-start-2"
          >
            <span class="text-xs text-dimmed">Next</span>
            <span class="mt-1 flex items-center justify-end gap-1 font-medium text-highlighted">
              {{ next.title }}<UIcon name="i-lucide-arrow-right" class="size-4" />
            </span>
          </ULink>
        </nav>
      </template>

      <template v-if="page?.body?.toc?.links?.length" #aside>
        <UContentToc :links="page.body.toc.links" title="On this page" highlight />
      </template>
    </DocsShell>
  </AppPage>
</template>
