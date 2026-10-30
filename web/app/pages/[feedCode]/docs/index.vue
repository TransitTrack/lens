<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import DocsShell from '~/components/docs/DocsShell.vue'
import PageState from '~/components/PageState.vue'
import { useDocs } from '~/composables/useDocs'

const { groups, status, docRoute } = useDocs()
</script>

<template>
  <AppPage
    title="Documentation"
    description="How TransitTrack works and how this instance is set up"
    :full-bleed="false"
  >
    <DocsShell>
      <PageState
        v-if="status === 'pending' && !groups.length"
        state="loading"
        title="Loading docs"
      />
      <PageState v-else-if="!groups.length" state="error" title="No docs in this build" />
      <div v-else class="space-y-8">
        <section v-for="group in groups" :key="group.title" class="space-y-3">
          <h2 class="text-sm font-semibold uppercase tracking-wide text-dimmed">
            {{ group.title }}
          </h2>
          <div class="grid gap-3 md:grid-cols-2">
            <ULink
              v-for="doc in group.docs"
              :key="doc.path"
              :to="docRoute(doc.path)"
              class="block rounded-lg border border-default p-4 transition-colors hover:border-primary/50 hover:bg-elevated/50"
            >
              <p class="font-medium text-highlighted">{{ doc.title }}</p>
              <p v-if="doc.description" class="mt-1 line-clamp-3 text-sm text-muted">
                {{ doc.description }}
              </p>
            </ULink>
          </div>
        </section>

        <section class="space-y-3">
          <h2 class="text-sm font-semibold uppercase tracking-wide text-dimmed">This instance</h2>
          <ULink
            :to="docRoute('/live-config')"
            class="flex items-start gap-3 rounded-lg border border-default p-4 transition-colors hover:border-primary/50 hover:bg-elevated/50"
          >
            <UIcon name="i-lucide-sliders-horizontal" class="mt-0.5 size-5 shrink-0 text-primary" />
            <span>
              <span class="block font-medium text-highlighted">Live configuration</span>
              <span class="mt-1 block text-sm text-muted">
                Every transittrack.* setting the running backend resolved, and where each value came
                from.
              </span>
            </span>
          </ULink>
        </section>
      </div>
    </DocsShell>
  </AppPage>
</template>
