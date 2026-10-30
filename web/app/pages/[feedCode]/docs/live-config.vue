<script setup lang="ts">
import type { ContentTocLink } from '@nuxt/ui'
import AppPage from '~/components/AppPage.vue'
import DocsShell from '~/components/docs/DocsShell.vue'
import PageState from '~/components/PageState.vue'
import { useDocs } from '~/composables/useDocs'
import {
  docSectionForPrefix,
  filterConfigGroups,
  parseConfigProps,
  type ConfigPropsReport,
  type ConfigSourceKind,
  type DocHeading,
} from '~/utils/configProps'

const { docRoute } = useDocs()

const loadedAt = ref<Date | null>(null)
const { data, status, error, refresh } = useAsyncData('live-configprops', async () => {
  const report = await $fetch<ConfigPropsReport>('/actuator/configprops')
  loadedAt.value = new Date()
  return report
})

// configuration.md's headings name the prefix they document; used to link each group to its reference section.
const { data: configDoc } = useAsyncData('doc-configuration-toc', () =>
  queryCollection('docs').path('/configuration').first(),
)
const headings = computed<DocHeading[]>(() => {
  const flat: DocHeading[] = []
  const walk = (links: ContentTocLink[] = []) =>
    links.forEach((l) => {
      flat.push({ id: l.id, text: l.text })
      walk(l.children)
    })
  walk(configDoc.value?.body?.toc?.links)
  return flat
})

const query = ref('')
const groups = computed(() => (data.value ? parseConfigProps(data.value) : []))
const shown = computed(() => filterConfigGroups(groups.value, query.value))
const total = computed(() => groups.value.reduce((n, g) => n + g.entries.length, 0))
const overridden = computed(() =>
  groups.value.reduce((n, g) => n + g.entries.filter((e) => e.source.kind !== 'default').length, 0),
)

function groupId(prefix: string) {
  return `cfg-${prefix.replace(/\./g, '-')}`
}
function referenceLink(prefix: string) {
  const id = docSectionForPrefix(prefix, headings.value)
  return id ? `${docRoute('/configuration')}#${id}` : null
}
const toc = computed<ContentTocLink[]>(() =>
  shown.value.map((g) => ({
    id: groupId(g.prefix),
    text: g.prefix.replace(/^transittrack\./, ''),
    depth: 2,
  })),
)

const SOURCE_COLOR: Record<ConfigSourceKind, 'neutral' | 'primary' | 'warning' | 'info'> = {
  default: 'neutral',
  file: 'primary',
  env: 'warning',
  other: 'info',
}

const loadedAtLabel = computed(() => loadedAt.value?.toLocaleTimeString() ?? null)
</script>

<template>
  <AppPage
    title="Live configuration"
    description="transittrack.* settings as resolved by the running backend"
    :full-bleed="false"
  >
    <template #actions>
      <span v-if="loadedAtLabel" class="hidden text-xs text-dimmed sm:inline"
        >Loaded {{ loadedAtLabel }}</span
      >
      <UButton
        icon="i-lucide-refresh-cw"
        color="neutral"
        variant="subtle"
        :loading="status === 'pending'"
        @click="refresh()"
      >
        Refresh
      </UButton>
    </template>

    <DocsShell>
      <div class="space-y-5">
        <UAlert
          color="neutral"
          variant="subtle"
          icon="i-lucide-info"
          description="Values come from the backend instance serving this dashboard's API. In a role-split deployment (role-feed-processor, role-predictor, …) other instances may run with different settings. Secrets and feed request headers are hidden; keys and passwords inside URLs are replaced with ******."
        />

        <PageState
          v-if="status === 'pending' && !data"
          state="loading"
          title="Loading configuration"
        />
        <PageState
          v-else-if="error"
          state="error"
          title="Configuration unavailable"
          description="Couldn't read /actuator/configprops. The backend must expose it (management.endpoints.web.exposure.include) and the proxy must forward it."
        >
          <template #action>
            <UButton color="neutral" variant="subtle" icon="i-lucide-refresh-cw" @click="refresh()"
              >Try again</UButton
            >
          </template>
        </PageState>
        <template v-else-if="data">
          <div class="flex flex-wrap items-center gap-3">
            <UInput
              v-model="query"
              icon="i-lucide-search"
              placeholder="Filter by property, value or source…"
              class="w-full sm:w-96"
              aria-label="Filter properties"
            />
            <p class="text-sm text-muted">
              {{ total }} properties · {{ overridden }} set explicitly ·
              {{ total - overridden }} code defaults
            </p>
          </div>

          <PageState
            v-if="!shown.length"
            state="empty"
            icon="i-lucide-search-x"
            title="No matching properties"
            :description="
              query
                ? `Nothing matches “${query}”.`
                : 'The backend reported no transittrack.* settings.'
            "
          />

          <section
            v-for="group in shown"
            :id="groupId(group.prefix)"
            :key="group.prefix"
            class="scroll-mt-4 overflow-hidden rounded-lg border border-default"
          >
            <header
              class="flex flex-wrap items-center justify-between gap-2 border-b border-default bg-elevated/40 px-4 py-2.5"
            >
              <h2 class="font-mono text-sm font-semibold text-highlighted">{{ group.prefix }}</h2>
              <ULink
                v-if="referenceLink(group.prefix)"
                :to="referenceLink(group.prefix)!"
                class="flex items-center gap-1 text-xs text-muted hover:text-primary"
              >
                <UIcon name="i-lucide-book-open" class="size-3.5" />
                Reference
              </ULink>
            </header>
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead class="sr-only">
                  <tr>
                    <th>Property</th>
                    <th>Value</th>
                    <th>Source</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-default">
                  <tr v-for="entry in group.entries" :key="entry.key" class="align-top">
                    <td class="w-2/5 px-4 py-2 font-mono text-xs break-all text-default">
                      {{ entry.key }}
                    </td>
                    <td class="px-4 py-2 font-mono text-xs break-all">
                      <span v-if="entry.masked" class="inline-flex items-center gap-1 text-dimmed">
                        <UIcon name="i-lucide-lock" class="size-3.5" />hidden
                      </span>
                      <span v-else-if="entry.value === null" class="text-dimmed italic"
                        >not set</span
                      >
                      <span v-else class="text-highlighted">{{ entry.value }}</span>
                    </td>
                    <td class="w-48 px-4 py-2 text-right">
                      <UTooltip
                        :text="
                          entry.source.origin ??
                          'No property source set this — the code default applies'
                        "
                      >
                        <UBadge
                          :color="SOURCE_COLOR[entry.source.kind]"
                          variant="subtle"
                          size="sm"
                          class="max-w-44 truncate font-mono"
                        >
                          {{ entry.source.label }}
                        </UBadge>
                      </UTooltip>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </template>
      </div>

      <template v-if="toc.length" #aside>
        <UContentToc :links="toc" title="Groups" highlight />
      </template>
    </DocsShell>
  </AppPage>
</template>
