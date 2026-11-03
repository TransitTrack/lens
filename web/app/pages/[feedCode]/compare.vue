<script setup lang="ts">
import { useRevisionsQuery, useDraftsQuery, useCompareRevisionsQuery } from '~~/generated/graphql'
import { useFeeds } from '~/composables/useFeeds'
import AppPage from '~/components/AppPage.vue'
import PageState from '~/components/PageState.vue'
import CompareTripChanges from '~/components/compare/CompareTripChanges.vue'
import CompareCalendarChanges from '~/components/compare/CompareCalendarChanges.vue'
import CompareHeadwaySummary from '~/components/compare/CompareHeadwaySummary.vue'
import { gtfsDate } from '~/utils/gtfs'

const { selectedFeedCode } = useFeeds()
const { t, te } = useI18n()
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

// i18n-keys: feed.dataset.statusValue.*
function statusLabel(status: string): string {
  const key = `feed.dataset.statusValue.${status}`
  return te(key) ? t(key) : status
}

interface RevisionOption {
  label: string
  value: string
}
const revisionOptions = computed<RevisionOption[]>(() => {
  const draftLabels = new Map(
    (draftsResult.value?.drafts ?? []).map((d) => [d.id, d.label ?? t('compare.untitledVersion', { version: d.version })]),
  )
  return (revisionsResult.value?.revisions ?? []).map((r) => {
    const label = draftLabels.get(r.id)
    return {
      label: label
        ? t('compare.revisionWithLabel', { label, id: r.id })
        : t('compare.revisionWithStatus', { status: statusLabel(r.status), date: gtfsDate(r.createdAt), id: r.id }),
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
  <AppPage :title="$t('nav.compareRevisions')">
    <template #toolbar>
      <div class="flex flex-wrap items-center gap-2 py-2">
        <USelectMenu
          v-model="fromId"
          :items="revisionOptions"
          value-key="value"
          :placeholder="$t('compare.fromRevision')"
          class="w-56"
        />
        <UIcon name="i-lucide-arrow-right" class="text-dimmed" />
        <USelectMenu
          v-model="toId"
          :items="revisionOptions"
          value-key="value"
          :placeholder="$t('compare.toRevision')"
          class="w-56"
        />
        <UInput v-model="routeFilter" :placeholder="$t('compare.routeIdOptional')" class="w-40" />
        <UInput
          :model-value="dirFilter == null ? '' : String(dirFilter)"
          :placeholder="$t('compare.directionOptional')"
          class="w-32"
          @update:model-value="(v: string) => (dirFilter = v === '' ? null : Number(v))"
        />
        <UInput v-model="serviceFilter" :placeholder="$t('compare.serviceIdOptional')" class="w-40" />
      </div>
    </template>

    <PageState
      v-if="!fromId || !toId"
      state="empty"
      :title="$t('compare.chooseTitle')"
      :description="$t('compare.chooseBody')"
      icon="i-lucide-git-compare"
    />

    <PageState v-else-if="loading" state="loading" :title="$t('compare.comparing')" />

    <PageState
      v-else-if="error"
      state="error"
      :title="$t('compare.unavailable')"
      :description="error.message"
    />

    <div v-else-if="comparison" class="flex flex-col gap-6 py-6">
      <div class="flex flex-wrap gap-2">
        <UBadge color="neutral" variant="soft">
          {{ $t('compare.tripsChanged', { count: comparison.tripChangeCount }, comparison.tripChangeCount) }}
        </UBadge>
        <UBadge color="neutral" variant="soft">
          {{ $t('compare.calendarsChanged', { count: comparison.calendarChanges.length }, comparison.calendarChanges.length) }}
        </UBadge>
        <UBadge
          v-if="comparison.fromDerivationStale || comparison.toDerivationStale"
          color="warning"
          variant="subtle"
        >
          {{ comparison.fromDerivationStale && comparison.toDerivationStale ? $t('compare.rebuildBoth') : $t('compare.rebuildStale') }}
        </UBadge>
      </div>

      <CompareTripChanges :changes="comparison.tripChanges" :total-count="comparison.tripChangeCount" />
      <CompareCalendarChanges :changes="comparison.calendarChanges" />
      <CompareHeadwaySummary v-if="!comparison.fromDerivationStale && !comparison.toDerivationStale" :summaries="comparison.headwaySummaries" />
    </div>
  </AppPage>
</template>
