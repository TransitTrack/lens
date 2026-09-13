<script setup lang="ts">
import {
  useRoutesQuery,
  useExploreCalendarQuery,
  useStartOptimizationRunMutation,
} from '~~/generated/graphql'
import {useFeeds} from '~/composables/useFeeds'

const open = defineModel<boolean>('open', {default: false})

const {selectedFeedCode, feedPath} = useFeeds()
const toast = useToast()

const observedFrom = ref('')
const observedTo = ref('')
const minimumSamples = ref(20)
const routeId = ref<string | null>(null)
const serviceId = ref<string | null>(null)
const directionId = ref<string | null>(null)
const windowFrom = ref('')
const windowTo = ref('')

const {result: routesResult} = useRoutesQuery(
  () => ({feedCode: selectedFeedCode.value ?? ''}),
  () => ({enabled: !!selectedFeedCode.value && open.value}),
)
const {result: calendarResult} = useExploreCalendarQuery(
  () => ({feedCode: selectedFeedCode.value ?? ''}),
  () => ({enabled: !!selectedFeedCode.value && open.value}),
)

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
const directionOptions = [
  {label: 'Any direction', value: null},
  {label: '0 — Outbound', value: '0'},
  {label: '1 — Inbound', value: '1'},
]

/** "HH:MM" -> GTFS seconds-of-day, or null for a blank field. Throws on malformed input. */
function parseHm(value: string): number | null {
  const trimmed = value.trim()
  if (!trimmed) return null
  const m = /^(\d{1,2}):(\d{2})$/.exec(trimmed)
  if (!m) throw new Error(`"${trimmed}" is not HH:MM`)
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
    toast.add({title: 'Invalid time window', description: (e as Error).message, color: 'error'})
    return
  }
  if (windowFromSec != null && windowToSec != null && windowFromSec >= windowToSec) {
    toast.add({title: 'Invalid time window', description: 'window from must be before window to', color: 'error'})
    return
  }
  try {
    const res = await start({
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
    })
    const id = res?.data?.startOptimizationRun.id
    open.value = false
    if (id) await navigateTo(feedPath('/optimize/' + id))
  } catch (e) {
    toast.add({title: 'Could not start run', description: (e as Error).message, color: 'error'})
  }
}
</script>

<template>
  <UModal v-model:open="open" title="New optimization run">
    <template #body>
      <div class="flex flex-col gap-3">
        <div class="grid grid-cols-2 gap-3">
          <UFormField label="Observed from" required>
            <UInput v-model="observedFrom" type="date" class="w-full" />
          </UFormField>
          <UFormField label="Observed to" required>
            <UInput v-model="observedTo" type="date" class="w-full" />
          </UFormField>
        </div>
        <UFormField label="Minimum samples" required hint="minimum independent crossings per segment">
          <UInput v-model.number="minimumSamples" type="number" min="1" class="w-full" />
        </UFormField>
        <UFormField label="Route" hint="optional">
          <USelectMenu
            v-model="routeId"
            :items="routeOptions"
            value-key="value"
            label-key="label"
            placeholder="Any route"
            class="w-full"
          />
        </UFormField>
        <UFormField label="Service" hint="optional">
          <USelectMenu
            v-model="serviceId"
            :items="serviceOptions"
            value-key="value"
            label-key="label"
            placeholder="Any service"
            class="w-full"
          />
        </UFormField>
        <UFormField label="Direction" hint="optional">
          <USelectMenu
            v-model="directionId"
            :items="directionOptions"
            value-key="value"
            label-key="label"
            class="w-full"
          />
        </UFormField>
        <div class="grid grid-cols-2 gap-3">
          <UFormField label="Departure window from" hint="HH:MM, optional">
            <UInput v-model="windowFrom" placeholder="06:00" class="w-full" />
          </UFormField>
          <UFormField label="Departure window to" hint="HH:MM, optional">
            <UInput v-model="windowTo" placeholder="09:00" class="w-full" />
          </UFormField>
        </div>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" label="Cancel" @click="open = false" />
        <UButton :loading="starting" :disabled="!canSubmit" label="Start run" @click="submit" />
      </div>
    </template>
  </UModal>
</template>
