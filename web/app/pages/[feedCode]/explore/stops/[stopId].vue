<script setup lang="ts">
import { useStopDetailQuery, useStopScheduleQuery } from '~/composables/useRestDetails'
import SplitterGrip from '~/components/shell/SplitterGrip.vue'
import { formatNumber } from '~/utils/intlLocale'
import type { SplitterItem } from '@nuxt/ui'
import AppPage from '~/components/shell/AppPage.vue'
import ValidationFindingsPanel from '~/components/explore/ValidationFindingsPanel.vue'
import StopsMap from '~/components/explore/StopsMap.vue'
import BarChart from '~/components/charts/BarChart.vue'
import { useFeeds } from '~/composables/useFeeds'
import { useFeedExtent } from '~/composables/useFeedExtent'

import { hexColor, routeTextColor } from '~/utils/gtfs'
import {
  routesOfCalls,
  serviceKinds,
  tripTotalsOf,
  departureHistogram,
  scheduledDepartureGroups,
  type ServiceKind,
} from '~/utils/stopSchedule'
import { useSplitLayout } from '~/composables/useSplitLayout'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()
const { selectedFeedCode, feedPath } = useFeeds()
const { extent } = useFeedExtent(selectedFeedCode)
const stopId = computed(() => String(route.params.stopId))

const { result: stopResult, loading: stopLoading } = useStopDetailQuery(() => ({
  variables: { feedCode: selectedFeedCode.value ?? '', stopId: stopId.value },
  enabled: !!selectedFeedCode.value,
}))
const stop = computed(() => stopResult.value?.stop ?? null)

// What the schedule does at this stop — the patterns calling here, their departures, and the days each
// service runs — computed by the backend in one request.
const { result: scheduleResult, loading: scheduleLoading } = useStopScheduleQuery(() => ({
  variables: { feedCode: selectedFeedCode.value ?? '', stopId: stopId.value },
  enabled: !!selectedFeedCode.value,
}))
const schedule = computed(() => scheduleResult.value?.stopSchedule ?? { calls: [], services: [] })
// Loading only while there is nothing to show yet (a refetch keeps the previous schedule on screen).
const tripsLoading = computed(() => scheduleLoading.value && !scheduleResult.value)

const routes = computed(() => routesOfCalls(schedule.value.calls))
const tripTotals = computed(() => tripTotalsOf(schedule.value.calls))

const kind = ref<ServiceKind>('weekday')
const departuresOpen = ref(false)

const kinds = computed(() => serviceKinds(schedule.value.services))
const histogram = computed(() => departureHistogram(schedule.value, kinds.value, kind.value, t))
const departureGroups = computed(() => scheduledDepartureGroups(schedule.value, kinds.value, kind.value))

// i18n-keys: explore.serviceKind.*, explore.serviceKindLower.*
const kindItems = computed(() =>
  (['weekday', 'saturday', 'sunday'] as const).map((value) => ({
    label: t(`explore.serviceKind.${value}`),
    value,
  })),
)
const kindLower = computed(() => t(`explore.serviceKindLower.${kind.value}`))

const mapFeatures = computed(
  () =>
    ({
      type: 'FeatureCollection',
      features:
        stop.value?.stopLat != null && stop.value?.stopLon != null
          ? [
              {
                type: 'Feature',
                geometry: { type: 'Point', coordinates: [stop.value.stopLon, stop.value.stopLat] },
                properties: {
                  stopId: stop.value.stopId,
                  name: stop.value.stopName ?? stop.value.stopId,
                },
              },
            ]
          : [],
    }) as GeoJSON.FeatureCollection,
)

const { isDesktop, splitterOrientation } = useSplitLayout()
const splitterItems = computed<SplitterItem[]>(() =>
  isDesktop.value
    ? [
        {
          id: 'details',
          slot: 'details',
          sizeUnit: 'px',
          defaultSize: 416,
          minSize: 320,
          maxSize: 560,
          class: 'flex min-h-0 flex-col overflow-hidden',
        },
        { id: 'map', slot: 'map', class: 'flex min-h-0 flex-col overflow-hidden' },
      ]
    : [
        {
          id: 'details',
          slot: 'details',
          defaultSize: 52,
          minSize: 30,
          maxSize: 70,
          class: 'flex min-h-0 flex-col overflow-hidden',
        },
        { id: 'map', slot: 'map', class: 'flex min-h-0 flex-col overflow-hidden' },
      ],
)
</script>

<template>
  <AppPage :title="stop?.stopName ?? stopId">
    <template #leading>
      <UButton
        icon="i-lucide-arrow-left"
        color="neutral"
        variant="ghost"
        :aria-label="t('common.goBack')"
        @click="router.back()"
      />
    </template>
    <template #actions>
      <ValidationFindingsPanel
        :feed-code="selectedFeedCode"
        entity-type="Stop"
        :entity-id="stopId"
      />
      <UBadge v-if="stop?.stopCode || stop?.platformCode" color="neutral" variant="subtle" icon="i-lucide-map-pin">
        {{ stop.platformCode ? t('explore.stop.platform', { code: stop.platformCode }) : stop.stopCode }}
      </UBadge>
    </template>

    <div v-if="stopLoading && !stop" class="flex min-h-0 flex-1">
      <USkeleton class="min-w-0 flex-1 rounded-none" />
      <div class="w-104 shrink-0 border-l border-default p-4">
        <USkeleton v-for="i in 8" :key="i" class="mb-2 h-10 w-full" />
      </div>
    </div>

    <USplitter
      v-else-if="stop"
      id="stop-detail-splitter"
      :orientation="splitterOrientation"
      :items="splitterItems"
      class="h-full min-h-0 w-full"
    >
      <template #details>
        <aside
          class="flex h-full min-h-0 w-full flex-1 flex-col gap-4 overflow-y-auto overscroll-contain p-4"
        >
          <section class="app-frame shrink-0 flex flex-col gap-3 p-3">
            <div class="flex items-start justify-between gap-3">
              <div>
                <div class="text-xs font-medium uppercase tracking-wide text-dimmed">
                  {{ t('explore.stop.details') }}
                </div>
                <div class="mt-1 text-sm text-muted">{{ stop.stopId }}</div>
              </div>
              <UBadge
                v-if="stop.wheelchairBoarding === 1"
                color="success"
                variant="subtle"
                icon="i-lucide-accessibility"
              >
                {{ t('explore.stop.accessible') }}
              </UBadge>
            </div>
            <dl class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1.5 text-sm">
              <dt class="text-dimmed">{{ t('explore.col.code') }}</dt>
              <dd>{{ stop.stopCode ?? '—' }}</dd>
              <dt class="text-dimmed">{{ t('explore.stop.location') }}</dt>
              <dd>{{ stop.stopLat?.toFixed(5) }}, {{ stop.stopLon?.toFixed(5) }}</dd>
              <template v-if="stop.stopDesc">
                <dt class="text-dimmed">{{ t('explore.stop.about') }}</dt>
                <dd>{{ stop.stopDesc }}</dd>
              </template>
              <template v-if="stop.zoneId">
                <dt class="text-dimmed">{{ t('explore.stop.zone') }}</dt>
                <dd>{{ stop.zoneId }}</dd>
              </template>
              <template v-if="stop.parentStation">
                <dt class="text-dimmed">{{ t('explore.stop.station') }}</dt>
                <dd>{{ stop.parentStation }}</dd>
              </template>
            </dl>
          </section>

          <section class="app-frame shrink-0">
            <div class="flex items-center justify-between border-b border-default px-3 py-2.5">
              <span class="text-sm font-medium text-highlighted">{{
                t('explore.stop.serviceAtStop')
              }}</span>
              <span class="text-xs text-dimmed">{{
                t(
                  'feed.overview.trips',
                  { count: formatNumber(tripTotals.total, locale) },
                  tripTotals.total,
                )
              }}</span>
            </div>
            <div class="p-3">
              <div class="grid grid-cols-2 border border-default">
                <div class="border-b border-r border-default px-3 py-2">
                  <div class="text-lg font-semibold text-highlighted">{{ routes.length }}</div>
                  <div class="text-xs text-dimmed">{{ t('explore.stop.routesLabel') }}</div>
                </div>
                <div class="border-b border-default px-3 py-2">
                  <div class="text-lg font-semibold text-highlighted">{{ schedule.calls.length }}</div>
                  <div class="text-xs text-dimmed">{{ t('explore.stop.patternsLabel') }}</div>
                </div>
                <div class="col-span-2 px-3 py-2">
                  <div class="text-sm font-semibold text-highlighted">
                    {{ secToHm(histogram.firstSec) }}–{{ secToHm(histogram.lastSec) }}
                  </div>
                  <div class="text-xs text-dimmed">
                    {{ t('explore.stop.typicalSpan', { kind: kindLower }) }}
                  </div>
                </div>
              </div>
              <div v-if="routes.length" class="mt-3 flex flex-wrap gap-1">
                <UBadge
                  v-for="r in routes"
                  :key="r.id"
                  class="cursor-pointer"
                  :style="{ backgroundColor: hexColor(r.color, undefined) }"
                  @click="navigateTo(feedPath(`/explore/routes/${r.id}`))"
                >
                  {{ r.name }}
                </UBadge>
              </div>
            </div>
          </section>

          <section class="app-frame shrink-0 flex flex-col gap-2 p-3">
            <div class="flex items-center justify-between">
              <span class="text-sm font-medium text-highlighted">{{
                t('explore.stop.departuresByHour')
              }}</span>
              <UFieldGroup>
                <UButton
                  v-for="k in kindItems"
                  :key="k.value"
                  :color="kind === k.value ? 'primary' : 'neutral'"
                  :variant="kind === k.value ? 'solid' : 'outline'"
                  :label="k.label"
                  @click="kind = k.value"
                />
              </UFieldGroup>
            </div>
            <USkeleton v-if="tripsLoading" class="h-32.5 w-full" />
            <template v-else>
              <BarChart :bars="histogram.bars" :height="130" :format="(n) => `${n}`" />
              <p class="text-xs text-dimmed">
                {{
                  t(
                    'explore.stop.departuresSummary',
                    { count: formatNumber(histogram.total, locale), kind: kindLower },
                    histogram.total,
                  )
                }}
              </p>
              <UButton
                :label="t('explore.stop.viewDepartures')"
                icon="i-lucide-list-clock"
                color="neutral"
                variant="outline"
                block
                @click="departuresOpen = true"
              />
            </template>
          </section>
        </aside>
      </template>
      <template #map>
        <div class="relative h-full min-w-0">
          <div
            class="pointer-events-none absolute left-4 top-4 z-1 border border-default bg-default/90 px-3 py-2 text-xs text-muted shadow-sm backdrop-blur"
          >
            {{ stop.stopName ?? stop.stopId }}
          </div>
          <StopsMap :stops="mapFeatures" :focus-id="stop.stopId" :extent="extent" />
        </div>
      </template>

      <template #resize-handle>
        <SplitterGrip :orientation="splitterOrientation" />
      </template>
    </USplitter>

    <UModal
      v-if="stop"
      v-model:open="departuresOpen"
      :title="t('explore.stop.departuresTitle', { name: stop.stopName ?? stop.stopId })"
      :description="t('explore.stop.departuresDescription', { kind: kindLower })"
      :ui="{ content: 'sm:max-w-5xl' }"
      scrollable
    >
      <template #body>
        <div v-if="tripsLoading" class="space-y-3">
          <USkeleton v-for="i in 6" :key="i" class="h-12 w-full" />
        </div>
        <div v-else-if="departureGroups.length" class="space-y-4">
          <section v-for="group in departureGroups" :key="group.hour">
            <h3 class="mb-1 text-xs font-medium uppercase tracking-wide text-dimmed">
              {{ String(group.hour).padStart(2, '0') }}:00–{{
                String(group.hour).padStart(2, '0')
              }}:59
            </h3>
            <table class="w-full text-sm">
              <tbody>
                <tr
                  v-for="departure in group.departures"
                  :key="`${departure.routeLabel}:${departure.headsign}:${departure.departureSec}`"
                  class="border-b border-default last:border-b-0"
                >
                  <td class="w-16 py-2 pr-3">
                    <UBadge
                      color="neutral"
                      variant="subtle"
                      :style="{
                        backgroundColor: hexColor(departure.routeColor, undefined),
                        color: routeTextColor(
                          hexColor(departure.routeColor),
                          departure.routeTextColor,
                        ),
                      }"
                      >{{ departure.routeLabel }}</UBadge
                    >
                  </td>
                  <td class="py-2 text-highlighted">{{ departure.headsign ?? '—' }}</td>
                  <td class="py-2 text-right font-medium tabular-nums text-highlighted">
                    {{ secToHm(departure.departureSec) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </section>
        </div>
        <p v-else class="py-8 text-center text-sm text-dimmed">
          {{ t('explore.stop.noDepartures') }}
        </p>
      </template>
    </UModal>
  </AppPage>
</template>
