<script setup lang="ts">
import { useRevisionsQuery, useDraftsQuery, useCompareRevisionsQuery } from '~~/generated/graphql'
import { useFeeds } from '~/composables/useFeeds'
import AppPage from '~/components/AppPage.vue'
import { gtfsDate } from '~/utils/gtfs'

const { selectedFeedCode } = useFeeds()
const route = useRoute()
const router = useRouter()

function queryString(v: string | null): string | undefined {
  return v || undefined
}

const fromId = computed({
  get: () => (route.query.from as string) ?? '',
  set: (v: string) => router.replace({ query: { ...route.query, from: queryString(v) } }),
})
const toId = computed({
  get: () => (route.query.to as string) ?? '',
  set: (v: string) => router.replace({ query: { ...route.query, to: queryString(v) } }),
})
const routeFilter = computed({
  get: () => (route.query.route as string) ?? null,
  set: (v: string | null) => router.replace({ query: { ...route.query, route: queryString(v) } }),
})
const dirFilter = computed({
  get: () => (route.query.dir != null ? Number(route.query.dir) : null),
  set: (v: number | null) =>
    router.replace({ query: { ...route.query, dir: v == null ? undefined : String(v) } }),
})
const serviceFilter = computed({
  get: () => (route.query.service as string) ?? null,
  set: (v: string | null) => router.replace({ query: { ...route.query, service: queryString(v) } }),
})

const { result: revisionsResult } = useRevisionsQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)
const { result: draftsResult } = useDraftsQuery(
  () => ({ feedCode: selectedFeedCode.value }),
  () => ({ enabled: !!selectedFeedCode.value }),
)

interface RevisionOption {
  label: string
  value: string
}
const revisionOptions = computed<RevisionOption[]>(() => {
  const draftLabels = new Map(
    (draftsResult.value?.drafts ?? []).map((d) => [d.id, d.label ?? `(untitled) v${d.version}`]),
  )
  return (revisionsResult.value?.revisions ?? []).map((r) => {
    const label = draftLabels.get(r.id)
    return {
      label: label ? `${label} (#${r.id})` : `${r.status} · ${gtfsDate(r.createdAt)} (#${r.id})`,
      value: r.id,
    }
  })
})

const { result, loading, error } = useCompareRevisionsQuery(
  () => ({
    fromRevisionId: fromId.value,
    toRevisionId: toId.value,
    routeId: routeFilter.value,
    directionId: dirFilter.value,
    serviceId: serviceFilter.value,
    offset: 0,
    limit: 200,
  }),
  () => ({ enabled: !!fromId.value && !!toId.value }),
)

const comparison = computed(() => result.value?.compareRevisions ?? null)
</script>

<template>
  <AppPage title="Compare revisions">
    <template #toolbar>
      <div class="flex flex-wrap items-center gap-2 py-2">
        <USelectMenu
          v-model="fromId"
          :items="revisionOptions"
          value-key="value"
          placeholder="From revision"
          class="w-56"
        />
        <UIcon name="i-lucide-arrow-right" class="text-dimmed" />
        <USelectMenu
          v-model="toId"
          :items="revisionOptions"
          value-key="value"
          placeholder="To revision"
          class="w-56"
        />
        <UInput v-model="routeFilter" placeholder="Route id (optional)" class="w-40" />
        <UInput
          :model-value="dirFilter == null ? '' : String(dirFilter)"
          placeholder="Direction (optional)"
          class="w-32"
          @update:model-value="(v: string) => (dirFilter = v === '' ? null : Number(v))"
        />
        <UInput v-model="serviceFilter" placeholder="Service id (optional)" class="w-40" />
      </div>
    </template>

    <div v-if="!fromId || !toId" class="flex flex-col items-center gap-3 py-16 text-center">
      <UIcon name="i-lucide-git-compare" class="size-8 text-dimmed" />
      <p class="text-sm text-muted">Pick two revisions to compare.</p>
    </div>

    <div v-else-if="loading" class="flex flex-col gap-4">
      <USkeleton class="h-10 w-full" />
      <USkeleton class="h-40 w-full" />
    </div>

    <UAlert
      v-else-if="error"
      color="error"
      variant="soft"
      icon="i-lucide-alert-triangle"
      title="Comparison unavailable"
      :description="error.message"
    />

    <div v-else-if="comparison" class="flex flex-col gap-6 py-6">
      <div class="flex flex-wrap gap-2">
        <UBadge color="neutral" variant="soft">
          {{ comparison.tripChangeCount }} trip{{ comparison.tripChangeCount === 1 ? '' : 's' }} changed
        </UBadge>
        <UBadge color="neutral" variant="soft">
          {{ comparison.calendarChanges.length }} calendar{{ comparison.calendarChanges.length === 1 ? '' : 's' }} changed
        </UBadge>
        <UBadge
          v-if="comparison.fromDerivationStale || comparison.toDerivationStale"
          color="warning"
          variant="subtle"
        >
          rebuild {{ comparison.fromDerivationStale && comparison.toDerivationStale ? 'both revisions' : 'the stale revision' }}
          to see run-time / headway impact
        </UBadge>
      </div>

      <!-- Tasks 10-11 replace these with CompareTripChanges / CompareCalendarChanges / CompareHeadwaySummary -->
      <div id="trip-changes-slot" />
      <div id="calendar-changes-slot" />
      <div id="headway-summary-slot" />
    </div>
  </AppPage>
</template>
