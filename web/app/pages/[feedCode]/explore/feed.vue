<script setup lang="ts">
import AppPage from '~/components/AppPage.vue'
import NavbarActions from '~/components/NavbarActions.vue'
import ExploreToolbar from '~/components/ExploreToolbar.vue'
import FeedCoveredArea from '~/components/FeedCoveredArea.vue'
import FeedFeatures from '~/components/FeedFeatures.vue'
import FeedDatasetHistory from '~/components/FeedDatasetHistory.vue'
import {useFeeds} from '~/composables/useFeeds'
import {useFeedExtent} from '~/composables/useFeedExtent'
import {
  useFeedDetailQuery,
  useAgenciesDetailQuery,
  useExploreRoutesQuery,
  useIngestFeedMutation,
  useActivateRevisionMutation,
  useDeleteRevisionMutation,
  useUpdateFeedMutation,
} from '~~/generated/graphql'
import {gtfsDate} from '~/utils/gtfs'
import {deriveFeatures} from '~/utils/feedFeatures'

const {selectedFeedCode, feedPath} = useFeeds()
const toast = useToast()

const {result: agenciesResult} = useAgenciesDetailQuery(
  () => ({feedCode: selectedFeedCode.value ?? ''}),
  () => ({enabled: !!selectedFeedCode.value}),
)
const {result: routesResult} = useExploreRoutesQuery(
  () => ({feedCode: selectedFeedCode.value ?? ''}),
  () => ({enabled: !!selectedFeedCode.value}),
)
const routes = computed(() => routesResult.value?.routes ?? [])
const {extent} = useFeedExtent(selectedFeedCode)

const agencies = computed(() => {
  const byAgency = new Map<string, number>()
  for (const r of routes.value) {
    if (r.agencyId) byAgency.set(r.agencyId, (byAgency.get(r.agencyId) ?? 0) + 1)
  }
  return (agenciesResult.value?.agencies ?? []).map((a) => ({
    ...a,
    routeCount: a.agencyId ? (byAgency.get(a.agencyId) ?? 0) : 0,
  }))
})

const {result, loading, refetch} = useFeedDetailQuery(
  () => ({code: selectedFeedCode.value ?? ''}),
  () => ({enabled: !!selectedFeedCode.value}),
)

const feed = computed(() => result.value?.feed ?? null)
const info = computed(() => result.value?.feedInfo ?? null)
const rev = computed(() => feed.value?.activeRevision ?? null)

const rowCounts = computed<[string, number][]>(() => {
  const rc = rev.value?.rowCounts
  if (!rc || typeof rc !== 'object') return []
  return Object.entries(rc as Record<string, number>).sort((a, b) => b[1] - a[1])
})

const features = computed(() =>
  deriveFeatures({
    filesPresent: rev.value?.filesPresent,
    rowCounts: (rev.value?.rowCounts ?? null) as Record<string, number> | null,
    routes: routes.value,
  }),
)

interface StatusChip {
  label: string
  color: 'success' | 'warning' | 'error'
  icon: string
}

const statusChips = computed<StatusChip[]>(() => {
  const v = rev.value?.validationSummary
  if (!v) return []
  return [
    v.errorCount
      ? {label: `${v.errorCount} errors`, color: 'error', icon: 'i-lucide-circle-x'}
      : {label: 'No errors', color: 'success', icon: 'i-lucide-circle-check'},
    v.warningCount
      ? {label: `${v.warningCount} warnings`, color: 'warning', icon: 'i-lucide-triangle-alert'}
      : {label: 'No warnings', color: 'success', icon: 'i-lucide-circle-check'},
  ]
})

// --- mutations -------------------------------------------------------------
const {mutate: ingest, loading: ingesting} = useIngestFeedMutation()
const {mutate: activate} = useActivateRevisionMutation()
const {mutate: remove} = useDeleteRevisionMutation()
const {mutate: update, loading: updating} = useUpdateFeedMutation()

async function reingest() {
  try {
    const res = await ingest({feedCode: selectedFeedCode.value ?? ''})
    toast.add({
      title: 'Ingest triggered',
      description: `Revision ${res?.data?.ingestFeed.id} · ${res?.data?.ingestFeed.status}`,
      color: 'success',
      icon: 'i-lucide-download',
    })
    await refetch()
  } catch (e) {
    toast.add({title: 'Ingest failed', description: (e as Error).message, color: 'error'})
  }
}

async function activateRevision(id: string) {
  try {
    await activate({revisionId: id})
    toast.add({title: `Revision ${id} activated`, color: 'success', icon: 'i-lucide-check'})
    await refetch()
  } catch (e) {
    toast.add({title: 'Activate failed', description: (e as Error).message, color: 'error'})
  }
}

async function deleteRevision(id: string) {
  try {
    await remove({revisionId: id})
    toast.add({title: `Revision ${id} deleted`, color: 'success', icon: 'i-lucide-trash-2'})
    await refetch()
  } catch (e) {
    toast.add({title: 'Delete failed', description: (e as Error).message, color: 'error'})
  }
}

// --- edit modal ----------------------------------------------------------
const editOpen = ref(false)
const form = reactive({name: '', description: '', url: '', pollingCron: '', enabled: true})

watch(editOpen, (open) => {
  if (open && feed.value) {
    form.name = feed.value.name
    form.description = feed.value.description ?? ''
    form.url = feed.value.url
    form.pollingCron = feed.value.pollingCron ?? ''
    form.enabled = feed.value.enabled
  }
})

async function saveFeed() {
  try {
    await update({
      code: selectedFeedCode.value ?? '',
      input: {
        name: form.name,
        description: form.description || null,
        url: form.url,
        pollingCron: form.pollingCron || null,
        enabled: form.enabled,
      },
    })
    toast.add({title: 'Feed updated', color: 'success', icon: 'i-lucide-check'})
    editOpen.value = false
    await refetch()
  } catch (e) {
    toast.add({title: 'Update failed', description: (e as Error).message, color: 'error'})
  }
}
</script>

<template>
  <AppPage title="Feed">
    <template #actions>
      <UButton
        icon="i-lucide-download"
        color="neutral"
        variant="soft"
        :loading="ingesting"
        label="Re-ingest"
        @click="reingest"
      />
      <NavbarActions/>
    </template>
    <template #toolbar>
      <ExploreToolbar :show-agency="false"/>
    </template>

    <div v-if="loading && !feed" class="flex flex-col gap-4">
      <USkeleton class="h-24 w-full"/>
      <div class="grid gap-4 lg:grid-cols-3">
        <USkeleton class="h-72 w-full lg:col-span-2"/>
        <USkeleton class="h-72 w-full"/>
      </div>
    </div>

    <template v-else-if="feed">
      <!-- Header band -->
      <div class="flex flex-col gap-3 border-b border-default pb-4">
        <div class="flex flex-wrap items-start justify-between gap-3">
          <div class="flex flex-col gap-1">
            <div class="flex items-center gap-2">
              <span class="text-xl font-semibold text-highlighted">{{ feed.name }}</span>
              <UBadge color="neutral" variant="subtle">{{ feed.source }}</UBadge>
            </div>
            <p v-if="feed.description" class="text-sm text-muted">{{ feed.description }}</p>
          </div>
          <div class="flex items-center gap-2">
            <UButton
              color="neutral"
              variant="soft"
              icon="i-lucide-pencil"
              label="Edit"
              @click="editOpen = true"
            />
          </div>
        </div>

        <div class="flex flex-wrap items-center gap-2">
          <UBadge
            v-for="chip in statusChips"
            :key="chip.label"
            :color="chip.color"
            variant="subtle"
            :icon="chip.icon"
          >
            {{ chip.label }}
          </UBadge>
          <span v-if="rev" class="text-xs text-dimmed">
            Latest dataset {{ gtfsDate(rev.activatedAt ?? rev.createdAt) }}
            <template v-if="rev.byteSize"> · {{ (rev.byteSize / 1e6).toFixed(1) }} MB</template>
          </span>
        </div>
      </div>

      <!-- Body: wide-left / sidebar-right -->
      <div class="grid gap-4 lg:grid-cols-3">
        <div class="flex flex-col gap-4 lg:col-span-2">
          <FeedCoveredArea
            :feed-code="feed.code"
            :extent="extent"
            :routes="routes"
            :full-map-to="feedPath('/explore/routes')"
          />

          <div
            v-if="rev && rowCounts.length"
            class="flex flex-col gap-3 rounded-lg border border-default p-4"
          >
            <div class="text-sm font-medium text-muted">Active revision #{{ rev.id }}</div>
            <div class="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
              <div
                v-for="[name, count] in rowCounts"
                :key="name"
                class="rounded-md border border-default px-2.5 py-1.5"
              >
                <div class="text-sm font-semibold text-highlighted">
                  {{ count.toLocaleString() }}
                </div>
                <div class="truncate text-[11px] text-dimmed">{{ name }}</div>
              </div>
            </div>
          </div>

          <FeedDatasetHistory
            :revisions="feed.revisions"
            @activate="activateRevision"
            @delete="deleteRevision"
          />
        </div>

        <div class="flex flex-col gap-4">
          <UCard :ui="{ body: 'flex flex-col gap-2' }">
            <div class="text-sm font-medium text-muted">Feed details</div>
            <dl class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
              <dt class="text-dimmed">Code</dt>
              <dd>{{ feed.code }}</dd>
              <dt class="text-dimmed">Source</dt>
              <dd>{{ feed.source }}</dd>
              <dt class="text-dimmed">Enabled</dt>
              <dd>{{ feed.enabled ? 'Yes' : 'No' }}</dd>
              <dt class="text-dimmed">Polling</dt>
              <dd>{{ feed.pollingCron ?? '—' }}</dd>
              <dt class="text-dimmed">URL</dt>
              <dd class="truncate">
                <a :href="feed.url" target="_blank" class="text-primary hover:underline">{{
                    feed.url
                  }}</a>
              </dd>
            </dl>
          </UCard>

          <UCard :ui="{ body: 'flex flex-col gap-2' }">
            <div class="text-sm font-medium text-muted">feed_info.txt</div>
            <dl v-if="info" class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
              <dt class="text-dimmed">Publisher</dt>
              <dd>{{ info.feedPublisherName ?? '—' }}</dd>
              <dt class="text-dimmed">Version</dt>
              <dd>{{ info.feedVersion ?? '—' }}</dd>
              <dt class="text-dimmed">Language</dt>
              <dd>{{ info.feedLang ?? '—' }}</dd>
              <dt class="text-dimmed">Valid</dt>
              <dd>{{ gtfsDate(info.feedStartDate) }} → {{ gtfsDate(info.feedEndDate) }}</dd>
              <dt class="text-dimmed">Contact</dt>
              <dd>{{ info.feedContactEmail ?? info.feedContactUrl ?? '—' }}</dd>
            </dl>
            <p v-else class="text-sm text-dimmed">No feed_info.txt in this feed.</p>
          </UCard>

          <UCard :ui="{ body: 'flex flex-col gap-2' }">
            <div class="text-sm font-medium text-muted">Agencies ({{ agencies.length }})</div>
            <div class="flex flex-col divide-y divide-default">
              <div
                v-for="a in agencies"
                :key="a.agencyId ?? a.agencyName ?? ''"
                class="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 py-2 first:pt-0 last:pb-0"
              >
                <div class="min-w-0">
                  <div class="truncate text-sm font-medium text-highlighted">
                    {{ a.agencyName ?? a.agencyId }}
                  </div>
                  <div class="text-xs text-dimmed">
                    {{ a.agencyTimezone ?? '—' }}
                    <template v-if="a.agencyPhone"> · {{ a.agencyPhone }}</template>
                  </div>
                </div>
                <div class="flex items-center gap-3 text-xs">
                  <span class="text-muted">{{ a.routeCount }} routes</span>
                  <a
                    v-if="a.agencyUrl"
                    :href="a.agencyUrl"
                    target="_blank"
                    class="text-primary hover:underline"
                  >site</a
                  >
                </div>
              </div>
            </div>
          </UCard>

          <FeedFeatures :features="features"/>
        </div>
      </div>
    </template>

    <UModal v-model:open="editOpen" title="Edit feed">
      <template #body>
        <div class="flex flex-col gap-3">
          <UFormField label="Name">
            <UInput v-model="form.name" class="w-full"/>
          </UFormField>
          <UFormField label="Description">
            <UInput v-model="form.description" class="w-full"/>
          </UFormField>
          <UFormField label="URL">
            <UInput v-model="form.url" class="w-full"/>
          </UFormField>
          <UFormField label="Polling cron">
            <UInput v-model="form.pollingCron" placeholder="0 */15 * * * *" class="w-full"/>
          </UFormField>
          <USwitch v-model="form.enabled" label="Enabled"/>
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton color="neutral" variant="ghost" label="Cancel" @click="editOpen = false"/>
          <UButton :loading="updating" label="Save" @click="saveFeed"/>
        </div>
      </template>
    </UModal>
  </AppPage>
</template>
