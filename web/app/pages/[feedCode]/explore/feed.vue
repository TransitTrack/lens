<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import NavbarActions from '~/components/NavbarActions.vue'
import ExploreToolbar from '~/components/ExploreToolbar.vue'
import { useFeeds } from '~/composables/useFeeds'
import {
  useFeedDetailQuery,
  useAgenciesDetailQuery,
  useExploreRoutesQuery,
  useIngestFeedMutation,
  useActivateRevisionMutation,
  useDeleteRevisionMutation,
  useUpdateFeedMutation,
  type FeedDetailQuery,
  type RevisionStatus,
} from '~~/generated/graphql'
import { gtfsDate } from '~/utils/gtfs'

const { selectedFeedCode } = useFeeds()
const toast = useToast()

const { result: agenciesResult } = useAgenciesDetailQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const { result: routesResult } = useExploreRoutesQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const agencies = computed(() => {
  const byAgency = new Map<string, number>()
  for (const r of routesResult.value?.routes ?? []) {
    if (r.agencyId) byAgency.set(r.agencyId, (byAgency.get(r.agencyId) ?? 0) + 1)
  }
  return (agenciesResult.value?.agencies ?? []).map((a) => ({
    ...a,
    routeCount: a.agencyId ? byAgency.get(a.agencyId) ?? 0 : 0,
  }))
})

const { result, loading, refetch } = useFeedDetailQuery(
  () => ({ code: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)

const feed = computed(() => result.value?.feed ?? null)
const info = computed(() => result.value?.feedInfo ?? null)
const rev = computed(() => feed.value?.activeRevision ?? null)

const rowCounts = computed<[string, number][]>(() => {
  const rc = rev.value?.rowCounts
  if (!rc || typeof rc !== 'object') return []
  return Object.entries(rc as Record<string, number>).sort((a, b) => b[1] - a[1])
})

function statusColor(s: RevisionStatus): 'success' | 'error' | 'warning' | 'neutral' {
  if (s === 'ACTIVE' || s === 'READY') return 'success'
  if (s === 'FAILED') return 'error'
  if (s === 'SUPERSEDED' || s === 'UNCHANGED') return 'neutral'
  return 'warning'
}

// --- mutations -------------------------------------------------------------
const { mutate: ingest, loading: ingesting } = useIngestFeedMutation()
const { mutate: activate } = useActivateRevisionMutation()
const { mutate: remove } = useDeleteRevisionMutation()
const { mutate: update, loading: updating } = useUpdateFeedMutation()

async function reingest() {
  try {
    const res = await ingest({ feedCode: selectedFeedCode.value ?? '' })
    toast.add({ title: 'Ingest triggered', description: `Revision ${res?.data?.ingestFeed.id} · ${res?.data?.ingestFeed.status}`, color: 'success', icon: 'i-lucide-download' })
    await refetch()
  } catch (e) {
    toast.add({ title: 'Ingest failed', description: (e as Error).message, color: 'error' })
  }
}

async function activateRevision(id: string) {
  try {
    await activate({ revisionId: id })
    toast.add({ title: `Revision ${id} activated`, color: 'success', icon: 'i-lucide-check' })
    await refetch()
  } catch (e) {
    toast.add({ title: 'Activate failed', description: (e as Error).message, color: 'error' })
  }
}

async function deleteRevision(id: string) {
  try {
    await remove({ revisionId: id })
    toast.add({ title: `Revision ${id} deleted`, color: 'success', icon: 'i-lucide-trash-2' })
    await refetch()
  } catch (e) {
    toast.add({ title: 'Delete failed', description: (e as Error).message, color: 'error' })
  }
}

// --- edit modal ----------------------------------------------------------
const editOpen = ref(false)
const form = reactive({ name: '', description: '', url: '', pollingCron: '', enabled: true })

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
    toast.add({ title: 'Feed updated', color: 'success', icon: 'i-lucide-check' })
    editOpen.value = false
    await refetch()
  } catch (e) {
    toast.add({ title: 'Update failed', description: (e as Error).message, color: 'error' })
  }
}

type Rev = NonNullable<FeedDetailQuery['feed']>['revisions'][number]
const revColumns: TableColumn<Rev>[] = [
  { accessorKey: 'id', header: 'Rev' },
  { accessorKey: 'status', header: 'Status' },
  { accessorKey: 'createdAt', header: 'Created' },
  { id: 'validation', header: 'Validation' },
  { id: 'actions', header: '' },
]
</script>

<template>
  <UDashboardPanel id="explore-feed">
    <template #header>
      <UDashboardNavbar title="Feed">
        <template #leading>
          <UDashboardSidebarCollapse />
        </template>
        <template #right>
          <UButton
            icon="i-lucide-download"
            size="sm"
            color="neutral"
            variant="soft"
            :loading="ingesting"
            label="Re-ingest"
            @click="reingest"
          />
          <NavbarActions />
        </template>
      </UDashboardNavbar>
      <ExploreToolbar :show-agency="false" />
    </template>

    <template #body>
      <div v-if="loading && !feed" class="flex flex-col gap-4">
        <USkeleton class="h-32 w-full" />
        <USkeleton class="h-48 w-full" />
      </div>

      <template v-else-if="feed">
        <div class="grid gap-4 lg:grid-cols-2">
          <UCard :ui="{ body: 'flex flex-col gap-2' }">
            <div class="flex items-center justify-between">
              <div class="text-sm font-medium text-muted">Feed</div>
              <UButton size="xs" color="neutral" variant="ghost" icon="i-lucide-pencil" label="Edit" @click="editOpen = true" />
            </div>
            <div class="text-lg font-semibold text-highlighted">{{ feed.name }}</div>
            <dl class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
              <dt class="text-dimmed">Code</dt><dd>{{ feed.code }}</dd>
              <dt class="text-dimmed">Source</dt><dd>{{ feed.source }}</dd>
              <dt class="text-dimmed">Enabled</dt><dd>{{ feed.enabled ? 'Yes' : 'No' }}</dd>
              <dt class="text-dimmed">Polling</dt><dd>{{ feed.pollingCron ?? '—' }}</dd>
              <dt class="text-dimmed">URL</dt>
              <dd class="truncate"><a :href="feed.url" target="_blank" class="text-primary hover:underline">{{ feed.url }}</a></dd>
            </dl>
          </UCard>

          <UCard :ui="{ body: 'flex flex-col gap-2' }">
            <div class="text-sm font-medium text-muted">feed_info.txt</div>
            <dl v-if="info" class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
              <dt class="text-dimmed">Publisher</dt><dd>{{ info.feedPublisherName ?? '—' }}</dd>
              <dt class="text-dimmed">Version</dt><dd>{{ info.feedVersion ?? '—' }}</dd>
              <dt class="text-dimmed">Language</dt><dd>{{ info.feedLang ?? '—' }}</dd>
              <dt class="text-dimmed">Valid</dt>
              <dd>{{ gtfsDate(info.feedStartDate) }} → {{ gtfsDate(info.feedEndDate) }}</dd>
              <dt class="text-dimmed">Contact</dt>
              <dd>{{ info.feedContactEmail ?? info.feedContactUrl ?? '—' }}</dd>
            </dl>
            <p v-else class="text-sm text-dimmed">No feed_info.txt in this feed.</p>
          </UCard>
        </div>

        <div class="rounded-lg border border-default p-4">
          <div class="mb-2 text-sm font-medium text-muted">
            Agencies ({{ agencies.length }})
          </div>
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
                >site</a>
              </div>
            </div>
          </div>
        </div>

        <div v-if="rev" class="flex flex-col gap-3 rounded-lg border border-default p-4">
          <div class="flex flex-wrap items-center gap-2">
            <span class="text-sm font-medium text-muted">Active revision #{{ rev.id }}</span>
            <UBadge :color="statusColor(rev.status)" variant="subtle" size="sm">{{ rev.status }}</UBadge>
            <span class="text-xs text-dimmed">
              activated {{ rev.activatedAt ? gtfsDate(rev.activatedAt) : '—' }}
              <template v-if="rev.byteSize">· {{ (rev.byteSize / 1e6).toFixed(1) }} MB</template>
            </span>
            <UBadge
              v-if="rev.validationSummary"
              :color="rev.validationSummary.errorCount ? 'error' : rev.validationSummary.warningCount ? 'warning' : 'success'"
              variant="subtle"
              size="sm"
            >
              {{ rev.validationSummary.errorCount }} err · {{ rev.validationSummary.warningCount }} warn
            </UBadge>
          </div>

          <div class="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
            <div
              v-for="[name, count] in rowCounts"
              :key="name"
              class="rounded-md border border-default px-2.5 py-1.5"
            >
              <div class="text-sm font-semibold text-highlighted">{{ count.toLocaleString() }}</div>
              <div class="truncate text-[11px] text-dimmed">{{ name }}</div>
            </div>
          </div>

          <div class="flex flex-wrap gap-1">
            <UBadge v-for="f in rev.filesPresent" :key="f" color="neutral" variant="subtle" size="sm">
              {{ f }}
            </UBadge>
          </div>
        </div>

        <div class="text-sm font-medium text-muted">Revision history</div>
        <UTable :data="feed.revisions" :columns="revColumns">
          <template #status-cell="{ row }">
            <UBadge :color="statusColor(row.original.status)" variant="subtle" size="sm">
              {{ row.original.status }}
            </UBadge>
          </template>
          <template #createdAt-cell="{ row }">{{ gtfsDate(row.original.createdAt) }}</template>
          <template #validation-cell="{ row }">
            <span v-if="row.original.validationSummary" class="text-xs text-muted">
              {{ row.original.validationSummary.errorCount }}e /
              {{ row.original.validationSummary.warningCount }}w
            </span>
            <span v-else-if="row.original.errorMessage" class="text-xs text-error">
              {{ row.original.errorMessage }}
            </span>
            <span v-else class="text-xs text-dimmed">—</span>
          </template>
          <template #actions-cell="{ row }">
            <div class="flex justify-end gap-1">
              <UButton
                v-if="row.original.status !== 'ACTIVE' && row.original.status !== 'FAILED'"
                size="xs"
                color="neutral"
                variant="ghost"
                icon="i-lucide-circle-check"
                label="Activate"
                @click="activateRevision(row.original.id)"
              />
              <UButton
                v-if="row.original.status !== 'ACTIVE'"
                size="xs"
                color="error"
                variant="ghost"
                icon="i-lucide-trash-2"
                @click="deleteRevision(row.original.id)"
              />
            </div>
          </template>
        </UTable>
      </template>

      <UModal v-model:open="editOpen" title="Edit feed">
        <template #body>
          <div class="flex flex-col gap-3">
            <UFormField label="Name">
              <UInput v-model="form.name" class="w-full" />
            </UFormField>
            <UFormField label="Description">
              <UInput v-model="form.description" class="w-full" />
            </UFormField>
            <UFormField label="URL">
              <UInput v-model="form.url" class="w-full" />
            </UFormField>
            <UFormField label="Polling cron">
              <UInput v-model="form.pollingCron" placeholder="0 */15 * * * *" class="w-full" />
            </UFormField>
            <USwitch v-model="form.enabled" label="Enabled" />
          </div>
        </template>
        <template #footer>
          <div class="flex justify-end gap-2">
            <UButton color="neutral" variant="ghost" label="Cancel" @click="editOpen = false" />
            <UButton :loading="updating" label="Save" @click="saveFeed" />
          </div>
        </template>
      </UModal>
    </template>
  </UDashboardPanel>
</template>
