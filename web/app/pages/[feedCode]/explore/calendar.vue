<script setup lang="ts">
import type { TableColumn } from '@nuxt/ui'
import AppPage from '~/components/AppPage.vue'
import ExploreToolbar from '~/components/ExploreToolbar.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useExploreCalendarQuery, type ExploreCalendarQuery } from '~~/generated/graphql'
import { activeDays, serviceDaysLabel, gtfsDate } from '~/utils/gtfs'

const { selectedFeedCode } = useFeeds()

const { result, loading } = useExploreCalendarQuery(
  () => ({ feedCode: selectedFeedCode.value ?? '' }),
  () => ({ enabled: !!selectedFeedCode.value }),
)

const DAY_LABELS = ['M', 'T', 'W', 'T', 'F', 'S', 'S']

const calendars = computed(() =>
  [...(result.value?.calendars ?? [])].sort((a, b) => a.serviceId.localeCompare(b.serviceId)),
)

type Exception = ExploreCalendarQuery['calendarDates'][number]
const exceptions = computed(() =>
  [...(result.value?.calendarDates ?? [])].sort((a, b) => b.date.localeCompare(a.date)),
)

const exColumns: TableColumn<Exception>[] = [
  { accessorKey: 'date', header: 'Date' },
  { accessorKey: 'serviceId', header: 'Service' },
  { accessorKey: 'exceptionType', header: 'Change' },
]
</script>

<template>
  <AppPage title="Calendar">
    <template #toolbar>
      <ExploreToolbar :show-agency="false" />
    </template>
    <div class="border border-default p-4">
      <div class="text-sm font-medium text-muted">Services</div>
      <div v-if="loading && !calendars.length" class="flex flex-col gap-2">
        <USkeleton v-for="i in 4" :key="i" class="h-12 w-full" />
      </div>
      <div v-else class="flex flex-col divide-y divide-default">
        <div
          v-for="cal in calendars"
          :key="cal.serviceId"
          class="flex flex-wrap items-center gap-x-4 gap-y-2 py-3"
        >
          <span class="w-40 shrink-0 truncate text-sm font-medium text-highlighted">
            {{ cal.serviceId }}
          </span>
          <div class="flex gap-1">
            <span
              v-for="(on, i) in activeDays(cal)"
              :key="i"
              class="flex size-6 items-center justify-center rounded text-xs font-medium"
              :class="on ? 'bg-primary text-inverted' : 'bg-elevated text-dimmed'"
            >
              {{ DAY_LABELS[i] }}
            </span>
          </div>
          <span class="text-xs text-muted">{{ serviceDaysLabel(cal) }}</span>
          <span class="text-xs text-dimmed">
            {{ gtfsDate(cal.startDate) }} → {{ gtfsDate(cal.endDate) }}
          </span>
        </div>
        <p v-if="!calendars.length" class="py-3 text-sm text-dimmed">No calendar.txt entries.</p>
      </div>

      <div class="mt-2 text-sm font-medium text-muted">Exceptions ({{ exceptions.length }})</div>
      <UTable v-if="exceptions.length" :data="exceptions" :columns="exColumns">
        <template #date-cell="{ row }">{{ gtfsDate(row.original.date) }}</template>
        <template #exceptionType-cell="{ row }">
          <UBadge :color="row.original.exceptionType === 1 ? 'success' : 'error'" variant="subtle">
            {{ row.original.exceptionType === 1 ? 'Added' : 'Removed' }}
          </UBadge>
        </template>
      </UTable>
      <p v-else class="text-sm text-dimmed">No calendar_dates.txt entries.</p>
    </div>
  </AppPage>
</template>
