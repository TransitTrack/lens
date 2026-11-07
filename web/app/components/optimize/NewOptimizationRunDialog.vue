<script setup lang="ts">
import { useStartOptimizationRunMutation } from '~/composables/useRestOptimization'
import { useExploreCalendarQuery, useRoutesQuery } from '~/composables/useRestGtfs'

import {useFeeds} from '~/composables/useFeeds'

const open = defineModel<boolean>('open', {default: false})

const {selectedFeedCode, feedPath} = useFeeds()
const toast = useToast()
const {t} = useI18n()

const observedFrom = ref('')
const observedTo = ref('')
const minimumSamples = ref(20)
const routeId = ref<string | null>(null)
const serviceId = ref<string | null>(null)
const directionId = ref<string | null>(null)
const windowFrom = ref('')
const windowTo = ref('')

const {result: routesResult} = useRoutesQuery(() => ({
  variables: { feedCode: selectedFeedCode.value ?? '' },
  enabled: !!selectedFeedCode.value && open.value,
}))
const {result: calendarResult} = useExploreCalendarQuery(() => ({
  variables: { feedCode: selectedFeedCode.value ?? '' },
  enabled: !!selectedFeedCode.value && open.value,
}))

const routeOptions = computed(() =>
  (routesResult.value?.routes ?? []).map((r) => ({
    label: `${r.routeShortName ?? r.routeId} — ${r.routeLongName ?? ''}`.trim(),
    value: r.routeId,
  })),
)
const serviceOptions = computed(() => {
  const ids = new Set((calendarResult.value?.calendars ?? []).map((c) => c.serviceId))
  return [...ids].sort().map((id) => ({label: id, value: id}))
})
const directionOptions = computed(() => [
  {label: t('optimize.anyDirection'), value: null},
  {label: t('optimize.outbound'), value: '0'},
  {label: t('optimize.inbound'), value: '1'},
])

/** "HH:MM" -> GTFS seconds-of-day, or null for a blank field. Throws on malformed input. */
function parseHm(value: string): number | null {
  const trimmed = value.trim()
  if (!trimmed) return null
  const m = /^(\d{1,2}):(\d{2})$/.exec(trimmed)
  if (!m) throw new Error(t('optimize.notHm', {value: trimmed}))
  return Number(m[1]) * 3600 + Number(m[2]) * 60
}

watch(open, (isOpen) => {
  if (!isOpen) return
  observedFrom.value = ''
  observedTo.value = ''
  minimumSamples.value = 20
  routeId.value = null
  serviceId.value = null
  directionId.value = null
  windowFrom.value = ''
  windowTo.value = ''
})

const {mutate: start, loading: starting} = useStartOptimizationRunMutation()

const canSubmit = computed(() => {
  if (!observedFrom.value || !observedTo.value || minimumSamples.value <= 0) return false
  return new Date(observedFrom.value).getTime() < new Date(observedTo.value).getTime()
})

async function submit() {
  if (!canSubmit.value) return
  let windowFromSec: number | null
  let windowToSec: number | null
  try {
    windowFromSec = parseHm(windowFrom.value)
    windowToSec = parseHm(windowTo.value)
  } catch (e) {
    toast.add({title: t('optimize.invalidWindow'), description: (e as Error).message, color: 'error'})
    return
  }
  if (windowFromSec != null && windowToSec != null && windowFromSec >= windowToSec) {
    toast.add({title: t('optimize.invalidWindow'), description: t('optimize.windowOrder'), color: 'error'})
    return
  }
  try {
    const res = await start({ variables: {
      input: {
        feedCode: selectedFeedCode.value ?? '',
        serviceId: serviceId.value,
        routeId: routeId.value,
        directionId: directionId.value == null ? null : Number(directionId.value),
        windowFromSec,
        windowToSec,
        observedFrom: new Date(observedFrom.value).toISOString(),
        observedTo: new Date(observedTo.value).toISOString(),
        minimumSamples: minimumSamples.value,
      },
    } })
    const id = res?.data?.startOptimizationRun.id
    open.value = false
    if (id) await navigateTo(feedPath('/optimize/' + id))
  } catch (e) {
    toast.add({title: t('optimize.startFailed'), description: (e as Error).message, color: 'error'})
  }
}
</script>

<template>
  <UModal v-model:open="open" :title="$t('optimize.dialogTitle')" :description="$t('optimize.dialogDescription')">
    <template #body>
      <div class="flex flex-col gap-6">
        <section>
          <div class="mb-3 flex items-center gap-2">
            <div class="grid size-6 place-items-center rounded-md bg-primary/10 text-primary"><UIcon name="i-lucide-calendar-range" class="size-3.5" /></div>
            <div>
              <p class="text-sm font-semibold text-highlighted">{{ $t('optimize.observationPeriod') }}</p>
              <p class="text-xs text-muted">{{ $t('optimize.observationHint') }}</p>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
          <UFormField :label="$t('optimize.observedFrom')" required>
            <UInput v-model="observedFrom" type="date" class="w-full" />
          </UFormField>
          <UFormField :label="$t('optimize.observedTo')" required>
            <UInput v-model="observedTo" type="date" class="w-full" />
          </UFormField>
        </div>
        </section>
        <section>
          <div class="mb-3 flex items-center gap-2">
            <div class="grid size-6 place-items-center rounded-md bg-primary/10 text-primary"><UIcon name="i-lucide-chart-bar-big" class="size-3.5" /></div>
            <div>
              <p class="text-sm font-semibold text-highlighted">{{ $t('optimize.confidence') }}</p>
              <p class="text-xs text-muted">{{ $t('optimize.confidenceHint') }}</p>
            </div>
          </div>
          <UFormField :label="$t('optimize.minSamples')" required :hint="$t('optimize.minSamplesHint')">
          <UInput v-model.number="minimumSamples" type="number" min="1" class="w-full" />
        </UFormField>
        </section>
        <section class="border-t border-default pt-5">
          <div class="mb-3 flex items-center gap-2">
            <div class="grid size-6 place-items-center rounded-md bg-elevated text-muted"><UIcon name="i-lucide-sliders-horizontal" class="size-3.5" /></div>
            <div>
              <p class="text-sm font-semibold text-highlighted">{{ $t('optimize.narrow') }}</p>
              <p class="text-xs text-muted">{{ $t('optimize.narrowHint') }}</p>
            </div>
          </div>
          <div class="grid gap-3 sm:grid-cols-2">
            <UFormField :label="$t('explore.col.route')">
          <USelectMenu
            v-model="routeId"
            :items="routeOptions"
            value-key="value"
            label-key="label"
            :placeholder="$t('optimize.anyRoute')"
            class="w-full"
          />
        </UFormField>
            <UFormField :label="$t('explore.col.service')">
          <USelectMenu
            v-model="serviceId"
            :items="serviceOptions"
            value-key="value"
            label-key="label"
            :placeholder="$t('optimize.anyService')"
            class="w-full"
          />
        </UFormField>
            <UFormField :label="$t('vehicles.detail.fact.direction')">
          <USelectMenu
            v-model="directionId"
            :items="directionOptions"
            value-key="value"
            label-key="label"
            class="w-full"
          />
        </UFormField>
          </div>
        <div class="grid grid-cols-2 gap-3">
          <UFormField :label="$t('optimize.windowFrom')" :hint="$t('optimize.timeFormat')">
            <UInput v-model="windowFrom" placeholder="06:00" class="w-full" />
          </UFormField>
          <UFormField :label="$t('optimize.windowTo')" :hint="$t('optimize.timeFormat')">
            <UInput v-model="windowTo" placeholder="09:00" class="w-full" />
          </UFormField>
        </div>
        </section>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" :label="$t('common.cancel')" @click="open = false" />
        <UButton :loading="starting" :disabled="!canSubmit" icon="i-lucide-sparkles" :label="$t('optimize.start')" @click="submit" />
      </div>
    </template>
  </UModal>
</template>
