<script setup lang="ts">
import { h } from 'vue'
import type { TableColumn } from '@nuxt/ui'
import {
  type RouteAdherenceQuery,
  useExploreRoutesQuery,
  useRouteAdherenceQuery,
} from '~~/generated/graphql'
import AppPage from '~/components/shell/AppPage.vue'
import PollControl from '~/components/shell/PollControl.vue'
import NoRealtimeState from '~/components/shell/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { adherenceBadge } from '~/utils/adherence'
import { isValidAdherenceRange } from '~/utils/adherenceReport'
import { formatHm } from '~/utils/tripFilters'

interface ReportFilters {
  routeId: string | null
  startDate: string
  endDate: string
}

function today(): string {
  // Machine format: 'en-CA' yields ISO yyyy-mm-dd for the API and date inputs, so it must not follow the UI locale.
  const parts = new Intl.DateTimeFormat('en-CA').formatToParts(new Date())
  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((part) => part.type === type)?.value ?? ''
  return `${value('year')}-${value('month')}-${value('day')}`
}

const { t } = useI18n()
const { selectedFeedCode, selectedAvlFeedCode, feedPath } = useFeeds()

const filters = reactive<ReportFilters>({
  routeId: null,
  startDate: today(),
  endDate: today(),
})
const submitted = ref<ReportFilters | null>(null)

// The filters panel starts open and collapses once a report has run, so the
// results get the room back; re-opening is still one click away via the
// accordion header.
const FILTERS_ACCORDION_VALUE = 'filters'
const filtersAccordionItems = computed(() => [
  { label: t('reports.filters.title'), value: FILTERS_ACCORDION_VALUE },
])
const openFilterAccordion = ref<string | undefined>(FILTERS_ACCORDION_VALUE)

const {
  result: routesResult,
  loading: routesLoading,
  error: routesError,
} = useExploreRoutesQuery(() => ({
  variables: { feedCode: selectedFeedCode.value ?? '' },
  enabled: !!selectedFeedCode.value,
}))

const routeItems = computed(() =>
  [...(routesResult.value?.routes ?? [])]
    .sort((a, b) =>
      (a.routeShortName ?? a.routeId).localeCompare(b.routeShortName ?? b.routeId, undefined, {
        numeric: true,
      }),
    )
    .map((r) => ({
      label: `${r.routeShortName ?? r.routeId} — ${r.routeLongName ?? ''}`,
      value: r.routeId,
    })),
)

const canRun = computed(
  () =>
    !!selectedAvlFeedCode.value &&
    !!filters.routeId &&
    isValidAdherenceRange(filters.startDate, filters.endDate),
)

function runReport() {
  if (!canRun.value || !filters.routeId) return
  submitted.value = { ...filters }
  openFilterAccordion.value = undefined
}

const {
  result: adherenceResult,
  loading: adherenceLoading,
  error: adherenceError,
} = useRouteAdherenceQuery(() => ({
  variables: { feedCode: selectedAvlFeedCode.value ?? '', routeId: submitted.value?.routeId ?? '', startDate: submitted.value?.startDate ?? '', endDate: submitted.value?.endDate ?? '' },
  enabled: !!selectedAvlFeedCode.value && !!submitted.value, fetchPolicy: 'network-only',
}))

type ReportRow = RouteAdherenceQuery['routeAdherence'][number]

const rows = computed<ReportRow[]>(() => adherenceResult.value?.routeAdherence ?? [])

const UBadge = resolveComponent('UBadge')
const NuxtLink = resolveComponent('NuxtLink')

const columns = computed<TableColumn<ReportRow>[]>(() => [
  {
    accessorKey: 'tripId',
    header: t('explore.col.trip'),
    cell: ({ row }) =>
      h(
        NuxtLink,
        {
          to: {
            path: feedPath('/reports/adherence-trip'),
            query: {
              routeId: submitted.value?.routeId ?? undefined,
              tripId: row.original.tripId,
              startDate: submitted.value?.startDate,
              endDate: submitted.value?.endDate,
            },
          },
          class: 'text-primary hover:underline',
        },
        { default: () => row.original.tripId },
      ),
  },
  { accessorKey: 'serviceDate', header: t('reports.col.serviceDate') },
  { accessorKey: 'stopId', header: t('explore.col.stop') },
  {
    accessorKey: 'scheduledSec',
    header: t('reports.col.scheduledTime'),
    cell: ({ row }) => formatHm(row.original.scheduledSec),
  },
  {
    accessorKey: 'observedSec',
    header: t('reports.col.observedTime'),
    cell: ({ row }) => formatHm(row.original.observedSec),
  },
  {
    id: 'adherence',
    header: t('vehicles.field.adherence'),
    cell: ({ row }) => {
      // Here, `null` means "this stop was scheduled but never observed in the AVL data" (a
      // reporting gap), not "not on a scheduled trip" - `adherenceBadge`'s live-tracking
      // "Unscheduled" label would be misleading next to a real Scheduled-time value.
      if (row.original.adherenceSec == null) {
        return h(UBadge, { color: 'neutral', variant: 'subtle' }, { default: () => t('reports.adherence.notObserved') })
      }
      const badge = adherenceBadge(row.original.adherenceSec, t)
      return h(UBadge, { color: badge.color, variant: 'subtle' }, { default: () => badge.label })
    },
  },
])
</script>

<template>
  <AppPage
    :title="$t('reports.route.title')"
    :description="$t('reports.route.description')"
  >
    <template #actions>
      <PollControl />
    </template>

    <NoRealtimeState v-if="!selectedAvlFeedCode" :what="$t('reports.noRealtime.adherence')" />

    <div v-else class="flex min-h-0 flex-1 flex-col gap-4 py-4">
      <div class="app-frame bg-elevated/20 px-4">
        <UAccordion
          v-model="openFilterAccordion"
          type="single"
          collapsible
          :items="filtersAccordionItems"
        >
          <template #trailing>
            <span v-if="routesLoading" class="text-sm text-dimmed">{{ $t('reports.filters.loadingRoutes') }}</span>
            <UIcon
              name="i-lucide-chevron-down"
              class="shrink-0 size-5 ms-auto group-data-[state=open]:rotate-180 transition-transform duration-200 ease-out motion-reduce:transition-none"
            />
          </template>
          <template #body>
            <UAlert
              v-if="routesError"
              color="error"
              variant="soft"
              icon="i-lucide-alert-triangle"
              :title="$t('reports.error.routes')"
              :description="routesError.message"
            />

            <div v-else class="grid gap-3 md:grid-cols-[minmax(16rem,1fr)_auto_auto] md:items-end">
              <label class="grid gap-1 text-sm font-medium text-muted">
                {{ $t('explore.col.route') }}
                <USelectMenu
                  v-model="filters.routeId"
                  data-testid="report-route"
                  :items="routeItems"
                  value-key="value"
                  searchable
                  :placeholder="$t('reports.filters.selectRoute')"
                  icon="i-lucide-route"
                />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                {{ $t('reports.filters.startDate') }}
                <UInput v-model="filters.startDate" type="date" data-testid="report-start-date" />
              </label>
              <label class="grid gap-1 text-sm font-medium text-muted">
                {{ $t('reports.filters.endDate') }}
                <UInput v-model="filters.endDate" type="date" data-testid="report-end-date" />
              </label>
            </div>

            <div class="mt-4 flex items-center justify-between gap-3">
              <p
                v-if="
                  filters.startDate &&
                  filters.endDate &&
                  !isValidAdherenceRange(filters.startDate, filters.endDate)
                "
                class="text-sm text-error"
              >
                {{ $t('reports.filters.endBeforeStart') }}
              </p>
              <div v-else />
              <UButton
                data-testid="report-run"
                icon="i-lucide-play"
                :disabled="!canRun"
                @click="runReport"
              >
                {{ $t('reports.filters.run') }}
              </UButton>
            </div>
          </template>
        </UAccordion>
      </div>

      <section v-if="submitted" class="min-h-0 app-frame">
        <UAlert
          v-if="adherenceError"
          color="error"
          variant="soft"
          icon="i-lucide-alert-triangle"
          :title="$t('reports.error.adherence')"
          :description="adherenceError.message"
          class="m-4"
        />
        <div v-else-if="adherenceLoading && !rows.length" class="flex flex-col gap-2 p-4">
          <USkeleton v-for="i in 8" :key="i" class="h-10 w-full" />
        </div>
        <p v-else-if="!rows.length" class="p-4 text-sm text-muted">
          {{ $t('reports.route.empty') }}
        </p>
        <UTable v-else :data="rows" :columns="columns" />
      </section>
    </div>
  </AppPage>
</template>
