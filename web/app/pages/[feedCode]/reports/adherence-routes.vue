<script setup lang="ts">
import { useQuery } from '@pinia/colada'
import { allRoutesAdherenceQuery } from '~/composables/api/queries/avl'
import type { RouteAdherenceSummary } from '~/api'
import type { TableColumn } from '@nuxt/ui'
import AppPage from '~/components/shell/AppPage.vue'
import PollControl from '~/components/shell/PollControl.vue'
import NoRealtimeState from '~/components/shell/NoRealtimeState.vue'
import { useFeeds } from '~/composables/useFeeds'
import { formatAdherence } from '~/utils/adherence'
import { isValidAdherenceRange } from '~/utils/adherenceReport'

interface ReportFilters {
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
const { selectedAvlFeedCode } = useFeeds()

const filters = reactive<ReportFilters>({
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

const canRun = computed(
  () => !!selectedAvlFeedCode.value && isValidAdherenceRange(filters.startDate, filters.endDate),
)

function runReport() {
  if (!canRun.value) return
  submitted.value = { ...filters }
  openFilterAccordion.value = undefined
}

const {
  data: adherence,
  isLoading: adherenceLoading,
  error: adherenceError,
} = useQuery(() => ({
  ...allRoutesAdherenceQuery({ feedCode: selectedAvlFeedCode.value!, startDate: submitted.value?.startDate ?? '', endDate: submitted.value?.endDate ?? '' }),
  enabled: !!selectedAvlFeedCode.value && !!submitted.value,
}))

type ReportRow = RouteAdherenceSummary

const rows = computed<ReportRow[]>(() =>
  [...(adherence.value ?? [])].sort((a, b) =>
    a.routeId.localeCompare(b.routeId, undefined, { numeric: true }),
  ),
)

const columns = computed<TableColumn<ReportRow>[]>(() => [
  {
    id: 'route',
    header: t('explore.col.route'),
    cell: ({ row }) => row.original.routeShortName ?? row.original.routeId,
  },
  { accessorKey: 'sampleCount', header: t('reports.col.sampleCount') },
  {
    id: 'meanAdherence',
    header: t('reports.col.meanAdherence'),
    cell: ({ row }) => formatAdherence(Math.round(row.original.meanAdherenceSec), t),
  },
  {
    id: 'onTimePct',
    header: t('reports.col.onTimePct'),
    cell: ({ row }) => `${row.original.onTimePct.toFixed(1)}%`,
  },
])
</script>

<template>
  <AppPage
    :title="$t('reports.routes.title')"
    :description="$t('reports.routes.description')"
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
          <template #body>
            <div class="grid gap-3 md:grid-cols-[auto_auto] md:items-end">
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
          {{ $t('reports.routes.empty') }}
        </p>
        <UTable v-else :data="rows" :columns="columns" />
      </section>
    </div>
  </AppPage>
</template>
