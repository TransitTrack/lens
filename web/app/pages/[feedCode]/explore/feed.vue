<script setup lang="ts">
import { useMutation, useQuery, useQueryCache } from '@pinia/colada'
import { feedQuery, revisionsQuery, feedInfoQuery, routesQuery, agenciesQuery } from '~/composables/api/queries/gtfs'
import { ingestFeedMutation } from '~/composables/api/mutations/gtfs/ingestFeedMutation'
import { updateFeedMutation } from '~/composables/api/mutations/gtfs/updateFeedMutation'
import { activateRevisionMutation } from '~/composables/api/mutations/gtfs/activateRevisionMutation'
import { deleteRevisionMutation } from '~/composables/api/mutations/gtfs/deleteRevisionMutation'
import { activeRevision } from '~/utils/joins/feeds'
import { formatNumber } from '~/utils/intlLocale'
import AppPage from '~/components/shell/AppPage.vue'
import PollControl from '~/components/shell/PollControl.vue'
import ExploreToolbar from '~/components/explore/ExploreToolbar.vue'
import FeedFeatures from '~/components/explore/FeedFeatures.vue'
import FeedDatasetHistory from '~/components/explore/FeedDatasetHistory.vue'
import { useFeeds } from '~/composables/useFeeds'

import { gtfsDate } from '~/utils/gtfs'
import { deriveFeatures } from '~/utils/feedFeatures'

const { t, te, locale } = useI18n()
const { selectedFeedCode } = useFeeds()
const toast = useToast()

const enabled = computed(() => !!selectedFeedCode.value)
const { data: agenciesData } = useQuery(() => ({
  ...agenciesQuery({ feedCode: selectedFeedCode.value! }),
  enabled: enabled.value,
}))
const { data: routesData } = useQuery(() => ({
  ...routesQuery({ feedCode: selectedFeedCode.value! }),
  enabled: enabled.value,
}))
const routes = computed(() => routesData.value ?? [])

const agencies = computed(() => {
  const byAgency = new Map<string, number>()
  for (const r of routes.value) {
    if (r.agencyId) byAgency.set(r.agencyId, (byAgency.get(r.agencyId) ?? 0) + 1)
  }
  return (agenciesData.value ?? []).map((a) => ({
    ...a,
    routeCount: a.agencyId ? (byAgency.get(a.agencyId) ?? 0) : 0,
  }))
})

const { data: feedData, isLoading: feedLoading } = useQuery(() => ({
  ...feedQuery({ code: selectedFeedCode.value! }),
  enabled: enabled.value,
}))
const { data: revisions, isLoading: revisionsLoading } = useQuery(() => ({
  ...revisionsQuery({ feedCode: selectedFeedCode.value! }),
  enabled: enabled.value,
}))
const { data: infoData } = useQuery(() => ({
  ...feedInfoQuery({ feedCode: selectedFeedCode.value! }),
  enabled: enabled.value,
}))

const loading = computed(() => feedLoading.value || revisionsLoading.value)
const feed = computed(() => {
  if (!feedData.value) return null
  const list = revisions.value ?? []
  return { ...feedData.value, revisions: list, activeRevision: activeRevision(list) }
})
const info = computed(() => infoData.value ?? null)
const rev = computed(() => feed.value?.activeRevision ?? null)

const rowCounts = computed<[string, number][]>(() => {
  const rc = rev.value?.rowCounts
  if (!rc || typeof rc !== 'object') return []
  return Object.entries(rc as Record<string, number>).sort((a, b) => b[1] - a[1])
})

const features = computed(() =>
  deriveFeatures(
    {
      filesPresent: rev.value?.filesPresent,
      rowCounts: (rev.value?.rowCounts ?? null) as Record<string, number> | null,
      routes: routes.value,
    },
    t,
  ),
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
      ? {
          label: t('feed.overview.errors', { count: v.errorCount }, v.errorCount),
          color: 'error',
          icon: 'i-lucide-circle-x',
        }
      : { label: t('explore.feed.noErrors'), color: 'success', icon: 'i-lucide-circle-check' },
    v.warningCount
      ? {
          label: t('feed.overview.warnings', { count: v.warningCount }, v.warningCount),
          color: 'warning',
          icon: 'i-lucide-triangle-alert',
        }
      : { label: t('explore.feed.noWarnings'), color: 'success', icon: 'i-lucide-circle-check' },
  ]
})

// --- mutations -------------------------------------------------------------
const queryCache = useQueryCache()
const { mutateAsync: ingest, isLoading: ingesting } = useMutation(ingestFeedMutation(queryCache))
const { mutateAsync: activate } = useMutation(activateRevisionMutation(queryCache))
const { mutateAsync: remove } = useMutation(deleteRevisionMutation(queryCache))
const { mutateAsync: update, isLoading: updating } = useMutation(updateFeedMutation(queryCache))

// i18n-keys: feed.dataset.statusValue.*
function statusLabel(status: string): string {
  const key = `feed.dataset.statusValue.${status}`
  return te(key) ? t(key) : status
}

async function reingest() {
  try {
    const res = await ingest({ path: { feedCode: selectedFeedCode.value ?? '' } })
    toast.add({
      title: t('explore.feed.ingestTriggered'),
      description: t('explore.feed.ingestRevision', {
        id: res?.id,
        status: statusLabel(String(res?.status)),
      }),
      color: 'success',
      icon: 'i-lucide-download',
    })
  } catch (e) {
    toast.add({
      title: t('explore.feed.ingestFailed'),
      description: (e as Error).message,
      color: 'error',
    })
  }
}

async function activateRevision(id: string) {
  try {
    await activate({ path: { revisionId: id } })
    toast.add({
      title: t('explore.feed.activated', { id }),
      color: 'success',
      icon: 'i-lucide-check',
    })
  } catch (e) {
    toast.add({
      title: t('explore.feed.activateFailed'),
      description: (e as Error).message,
      color: 'error',
    })
  }
}

async function deleteRevision(id: string) {
  try {
    await remove({ path: { id } })
    toast.add({
      title: t('explore.feed.deleted', { id }),
      color: 'success',
      icon: 'i-lucide-trash-2',
    })
  } catch (e) {
    toast.add({
      title: t('explore.feed.deleteFailed'),
      description: (e as Error).message,
      color: 'error',
    })
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
      path: { code: selectedFeedCode.value ?? '' },
      body: {
        name: form.name,
        description: form.description || null,
        url: form.url,
        pollingCron: form.pollingCron || null,
        enabled: form.enabled,
      },
    })
    toast.add({ title: t('explore.feed.updated'), color: 'success', icon: 'i-lucide-check' })
    editOpen.value = false
  } catch (e) {
    toast.add({
      title: t('explore.feed.updateFailed'),
      description: (e as Error).message,
      color: 'error',
    })
  }
}
</script>

<template>
  <AppPage :title="t('nav.feed')" :description="t('explore.feed.description')">
    <template #actions>
      <UButton
        icon="i-lucide-download"
        color="neutral"
        variant="soft"
        :loading="ingesting"
        :label="t('explore.feed.reingest')"
        @click="reingest"
      />
      <PollControl />
    </template>
    <template #toolbar>
      <ExploreToolbar :show-agency="false" />
    </template>

    <div v-if="loading && !feed" class="flex flex-col gap-4">
      <USkeleton class="h-24 w-full" />
      <div class="grid gap-4 lg:grid-cols-3">
        <USkeleton class="h-72 w-full lg:col-span-2" />
        <USkeleton class="h-72 w-full" />
      </div>
    </div>

    <template v-else-if="feed">
      <!-- Header band -->
      <div class="app-frame flex flex-col gap-3 p-4">
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
              :label="t('explore.feed.edit')"
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
            {{
              rev.byteSize
                ? t('explore.feed.latestDatasetSize', {
                    date: gtfsDate(rev.activatedAt ?? rev.createdAt, locale),
                    size: (rev.byteSize / 1e6).toFixed(1),
                  })
                : t('explore.feed.latestDataset', {
                    date: gtfsDate(rev.activatedAt ?? rev.createdAt, locale),
                  })
            }}
          </span>
        </div>
      </div>

      <!-- Body: wide-left / sidebar-right -->
      <div class="grid gap-4 lg:grid-cols-3">
        <div class="app-frame flex flex-col lg:col-span-2">
          <div
            v-if="rev && rowCounts.length"
            class="flex flex-col gap-3 border-b border-default p-4"
          >
            <div class="text-sm font-medium text-muted">
              {{ t('explore.feed.activeRevision', { id: rev.id }) }}
            </div>
            <div class="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
              <div
                v-for="[name, count] in rowCounts"
                :key="name"
                class="rounded-md border border-default px-2.5 py-1.5"
              >
                <div class="text-sm font-semibold text-highlighted">
                  {{ formatNumber(count, locale) }}
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

        <div class="app-frame flex flex-col divide-y divide-default">
          <div class="flex flex-col gap-2 p-4">
            <div class="text-sm font-medium text-muted">
              {{ t('explore.feed.details') }}
            </div>
            <dl class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
              <dt class="text-dimmed">{{ t('explore.col.code') }}</dt>
              <dd>{{ feed.code }}</dd>
              <dt class="text-dimmed">{{ t('explore.feed.source') }}</dt>
              <dd>{{ feed.source }}</dd>
              <dt class="text-dimmed">{{ t('explore.feed.enabled') }}</dt>
              <dd>{{ feed.enabled ? t('explore.feed.yes') : t('explore.feed.no') }}</dd>
              <dt class="text-dimmed">{{ t('explore.feed.polling') }}</dt>
              <dd>{{ feed.pollingCron ?? '—' }}</dd>
              <dt class="text-dimmed">{{ t('explore.feed.url') }}</dt>
              <dd class="truncate">
                <a :href="feed.url" target="_blank" class="text-primary hover:underline">{{
                  feed.url
                }}</a>
              </dd>
            </dl>
          </div>

          <div class="flex flex-col gap-2 p-4">
            <div class="text-sm font-medium text-muted">feed_info.txt</div>
            <dl v-if="info" class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
              <dt class="text-dimmed">{{ t('explore.feed.publisher') }}</dt>
              <dd>{{ info.feedPublisherName ?? '—' }}</dd>
              <dt class="text-dimmed">{{ t('explore.feed.version') }}</dt>
              <dd>{{ info.feedVersion ?? '—' }}</dd>
              <dt class="text-dimmed">{{ t('explore.feed.language') }}</dt>
              <dd>{{ info.feedLang ?? '—' }}</dd>
              <dt class="text-dimmed">{{ t('explore.feed.valid') }}</dt>
              <dd>
                {{ gtfsDate(info.feedStartDate, locale) }} →
                {{ gtfsDate(info.feedEndDate, locale) }}
              </dd>
              <dt class="text-dimmed">{{ t('explore.feed.contact') }}</dt>
              <dd>{{ info.feedContactEmail ?? info.feedContactUrl ?? '—' }}</dd>
            </dl>
            <p v-else class="text-sm text-dimmed">{{ t('explore.feed.noFeedInfo') }}</p>
          </div>

          <div class="flex flex-col gap-2 p-4">
            <div class="text-sm font-medium text-muted">
              {{ t('explore.feed.agencies', { count: agencies.length }) }}
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
                  <span class="text-muted">{{
                    t('feed.overview.routes', { count: a.routeCount }, a.routeCount)
                  }}</span>
                  <a
                    v-if="a.agencyUrl"
                    :href="a.agencyUrl"
                    target="_blank"
                    class="text-primary hover:underline"
                    >{{ t('explore.feed.site') }}</a
                  >
                </div>
              </div>
            </div>
          </div>

          <FeedFeatures :features="features" />
        </div>
      </div>
    </template>

    <UModal v-model:open="editOpen" :title="t('explore.feed.editTitle')">
      <template #body>
        <div class="flex flex-col gap-3">
          <UFormField :label="t('explore.col.name')">
            <UInput v-model="form.name" class="w-full" />
          </UFormField>
          <UFormField :label="t('explore.feed.formDescription')">
            <UInput v-model="form.description" class="w-full" />
          </UFormField>
          <UFormField :label="t('explore.feed.url')">
            <UInput v-model="form.url" class="w-full" />
          </UFormField>
          <UFormField :label="t('explore.feed.pollingCron')">
            <UInput v-model="form.pollingCron" placeholder="0 */15 * * * *" class="w-full" />
          </UFormField>
          <USwitch v-model="form.enabled" :label="t('explore.feed.enabled')" />
        </div>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton
            color="neutral"
            variant="ghost"
            :label="t('common.cancel')"
            @click="editOpen = false"
          />
          <UButton :loading="updating" :label="t('common.save')" @click="saveFeed" />
        </div>
      </template>
    </UModal>
  </AppPage>
</template>
