<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {
  useDraftPatternsQuery,
  useAddTripMutation,
  useDuplicateTripMutation,
  type DraftGridQuery,
} from '~~/generated/graphql'
import type {useDraftEditor} from '~/composables/useDraftEditor'
import {useDraftEdit} from '~/composables/useDraftEdit'
import {parseTimeInput, parseDeltaInput, secToClock} from '~/utils/gtfsTime'

type DraftGridTrip = DraftGridQuery['draftGrid']['trips'][number]

const open = defineModel<boolean>('open', {default: false})

const props = defineProps<{
  editor: ReturnType<typeof useDraftEditor>
  feedCode: string
  routeId: string
  directionId: number | null
  serviceId: string | null
  derivationStale: boolean
  trips: DraftGridTrip[]
  duplicateSourceTripId?: string | null
  serviceItems?: { label: string; value: string }[]
}>()

const emit = defineEmits<{ changed: [] }>()

type Mode = 'new' | 'duplicate'
const mode = ref<Mode>('duplicate')

// duplicate mode
const sourceTripId = ref<string | null>(null)
const offsetRaw = ref('')
const newTripId = ref('')

// new mode
const patternKey = ref<string | null>(null)
const serviceSel = ref<string | null>(null)
const baseRaw = ref('')
const headsign = ref('')
const directionRaw = ref<number | string | null>(null)
const blockId = ref('')

watch(open, (isOpen) => {
  if (!isOpen) return
  mode.value = props.duplicateSourceTripId ? 'duplicate' : props.derivationStale ? 'duplicate' : 'new'
  sourceTripId.value = props.duplicateSourceTripId ?? props.trips[0]?.tripId ?? null
  offsetRaw.value = ''
  newTripId.value = ''
  patternKey.value = null
  serviceSel.value = props.serviceId
  baseRaw.value = ''
  headsign.value = ''
  directionRaw.value = props.directionId
  blockId.value = ''
})

const tripItems = computed(() =>
  props.trips.map((t) => ({
    label: `${t.tripId} (${secToClock(t.firstDepartureSec)})`,
    value: t.tripId,
  })),
)

const {result: patternsResult} = useDraftPatternsQuery(
  () => ({
    feedCode: props.feedCode,
    routeId: props.routeId,
    revisionId: props.editor.draft.value?.id ?? '',
  }),
  () => ({
    enabled:
      !!props.routeId &&
      !!props.editor.draft.value?.id &&
      !props.derivationStale &&
      open.value &&
      mode.value === 'new',
  }),
)
const patterns = computed(() => patternsResult.value?.tripPatterns ?? [])
const patternItems = computed(() =>
  patterns.value.map((p) => ({label: p.headsign ?? p.patternKey, value: p.patternKey})),
)
const selectedPattern = computed(
  () => patterns.value.find((p) => p.patternKey === patternKey.value) ?? null,
)
const patternHasUnknownTimes = computed(() =>
  (selectedPattern.value?.stopPaths ?? []).some(
    (sp) => sp.typicalTravelTimeSec == null || sp.typicalDwellTimeSec == null,
  ),
)

const offsetSec = computed(() => parseDeltaInput(offsetRaw.value))
const offsetInvalid = computed(() => offsetRaw.value.trim() !== '' && offsetSec.value == null)
const baseSec = computed(() => parseTimeInput(baseRaw.value, null))

const canSubmit = computed(() => {
  if (mode.value === 'duplicate') return !!sourceTripId.value && offsetSec.value != null
  return !props.derivationStale && !!selectedPattern.value && !!serviceSel.value && baseSec.value != null
})

const {mutate: addTrip} = useAddTripMutation()
const {mutate: duplicateTrip} = useDuplicateTripMutation()
const {run} = useDraftEdit(props.editor, {onChanged: () => emit('changed')})
const submitting = ref(false)

function buildStops(base: number) {
  const paths = [...(selectedPattern.value?.stopPaths ?? [])].sort(
    (a, b) => a.stopPathIndex - b.stopPathIndex,
  )
  const stops: { stopId: string; arrivalSec: number; departureSec: number }[] = []
  let prevDeparture = base
  paths.forEach((sp, i) => {
    const arrival = i === 0 ? base : prevDeparture + (sp.typicalTravelTimeSec ?? 0)
    const departure = arrival + (sp.typicalDwellTimeSec ?? 0)
    stops.push({stopId: sp.stopId, arrivalSec: arrival, departureSec: departure})
    prevDeparture = departure
  })
  return stops
}

async function submit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    if (mode.value === 'duplicate') {
      const result = await run(
        (v) =>
          duplicateTrip({
            input: {
              draftId: v.draftId,
              editor: v.editor,
              expectedVersion: v.expectedVersion,
              sourceTripId: sourceTripId.value as string,
              newTripId: newTripId.value || null,
              offsetSec: offsetSec.value as number,
            },
          }),
        'duplicateTrip',
      )
      if (result) open.value = false
    } else {
      const stops = buildStops(baseSec.value as number)
      const result = await run(
        (v) =>
          addTrip({
            input: {
              draftId: v.draftId,
              editor: v.editor,
              expectedVersion: v.expectedVersion,
              routeId: props.routeId,
              serviceId: serviceSel.value as string,
              tripId: null,
              headsign: headsign.value || null,
              directionId:
                directionRaw.value === '' || directionRaw.value == null
                  ? null
                  : Number(directionRaw.value),
              shapeId: null,
              blockId: blockId.value || null,
              stops,
            },
          }),
        'addTrip',
      )
      if (result) open.value = false
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <UModal v-model:open="open" title="Add trip">
    <template #body>
      <div class="flex flex-col gap-3">
        <div class="flex gap-2">
          <UButton
            :color="mode === 'new' ? 'primary' : 'neutral'"
            :variant="mode === 'new' ? 'soft' : 'ghost'"
            label="New"
            :disabled="derivationStale"
            @click="mode = 'new'"
          />
          <UButton
            :color="mode === 'duplicate' ? 'primary' : 'neutral'"
            :variant="mode === 'duplicate' ? 'soft' : 'ghost'"
            label="Duplicate"
            @click="mode = 'duplicate'"
          />
        </div>

        <template v-if="mode === 'duplicate'">
          <UFormField label="Source trip">
            <USelectMenu
              v-model="sourceTripId"
              :items="tripItems"
              value-key="value"
              placeholder="Pick a trip"
              class="w-full"
            />
          </UFormField>
          <UFormField
            label="Offset"
            :error="offsetInvalid ? 'Enter a signed delta: +30m, -90, +1:30' : undefined"
          >
            <UInput
              v-model="offsetRaw"
              placeholder="e.g. +30m"
              :color="offsetInvalid ? 'error' : undefined"
              class="w-full"
            />
          </UFormField>
          <UFormField label="New trip id" hint="optional">
            <UInput v-model="newTripId" placeholder="auto" class="w-full"/>
          </UFormField>
        </template>

        <template v-else>
          <p v-if="derivationStale" class="text-sm text-warning">
            Rebuild the draft first to add a trip from a pattern.
          </p>
          <template v-else>
            <UFormField label="Pattern">
              <USelectMenu
                v-model="patternKey"
                :items="patternItems"
                value-key="value"
                placeholder="Pick a pattern"
                class="w-full"
              />
            </UFormField>
            <UFormField label="Service">
              <USelectMenu
                v-model="serviceSel"
                :items="serviceItems ?? []"
                value-key="value"
                placeholder="Pick a service"
                class="w-full"
              />
            </UFormField>
            <UFormField label="Base departure">
              <UInput v-model="baseRaw" placeholder="e.g. 06:15" class="w-full"/>
            </UFormField>
            <div class="grid grid-cols-3 gap-2">
              <UFormField label="Headsign" hint="opt">
                <UInput v-model="headsign" class="w-full"/>
              </UFormField>
              <UFormField label="Direction" hint="opt">
                <UInput v-model.number="directionRaw" type="number" class="w-full"/>
              </UFormField>
              <UFormField label="Block" hint="opt">
                <UInput v-model="blockId" class="w-full"/>
              </UFormField>
            </div>
            <p v-if="selectedPattern && patternHasUnknownTimes" class="text-xs text-warning">
              Some segment times unknown — verify after adding.
            </p>
          </template>
        </template>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" label="Cancel" @click="open = false"/>
        <UButton
          :loading="submitting"
          :disabled="!canSubmit"
          :label="mode === 'duplicate' ? 'Duplicate trip' : 'Add trip'"
          @click="submit"
        />
      </div>
    </template>
  </UModal>
</template>
