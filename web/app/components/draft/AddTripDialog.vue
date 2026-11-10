<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import type { DraftGridTrip } from '~/api'
import type { DraftEditCommand, DraftPattern } from '~/types/draftEdits'
import {parseTimeInput, parseDeltaInput, secToClock} from '~/utils/gtfsTime'

const open = defineModel<boolean>('open', {default: false})

type Mode = 'new' | 'duplicate'
/** The page reads the route's patterns only while the dialog is open in 'new' mode. */
const mode = defineModel<Mode>('mode', {default: 'duplicate'})

const props = defineProps<{
  routeId: string
  directionId: number | null
  serviceId: string | null
  derivationStale: boolean
  trips: DraftGridTrip[]
  duplicateSourceTripId?: string | null
  serviceItems?: { label: string; value: string }[]
  /** The route's patterns in the draft with their stop paths (read by the page). */
  patterns: DraftPattern[]
  /** True while the page runs the submitted edit; the page closes the dialog when it lands. */
  submitting: boolean
}>()

const emit = defineEmits<{ edit: [command: DraftEditCommand] }>()

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

const patterns = computed(() => props.patterns)
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

function submit() {
  if (!canSubmit.value || props.submitting) return
  if (mode.value === 'duplicate') {
    emit('edit', {
      op: 'duplicateTrip',
      body: {
        sourceTripId: sourceTripId.value as string,
        newTripId: newTripId.value || null,
        offsetSec: offsetSec.value as number,
      },
    })
  } else {
    emit('edit', {
      op: 'addTrip',
      body: {
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
        stops: buildStops(baseSec.value as number),
      },
    })
  }
}
</script>

<template>
  <UModal v-model:open="open" :title="$t('drafts.addTrip')">
    <template #body>
      <div class="flex flex-col gap-3">
        <div class="flex gap-2">
          <UButton
            :color="mode === 'new' ? 'primary' : 'neutral'"
            :variant="mode === 'new' ? 'soft' : 'ghost'"
            :label="$t('drafts.modeNew')"
            :disabled="derivationStale"
            @click="mode = 'new'"
          />
          <UButton
            :color="mode === 'duplicate' ? 'primary' : 'neutral'"
            :variant="mode === 'duplicate' ? 'soft' : 'ghost'"
            :label="$t('drafts.duplicate')"
            @click="mode = 'duplicate'"
          />
        </div>

        <template v-if="mode === 'duplicate'">
          <UFormField :label="$t('drafts.sourceTrip')">
            <USelectMenu
              v-model="sourceTripId"
              :items="tripItems"
              value-key="value"
              :placeholder="$t('drafts.pickTrip')"
              class="w-full"
            />
          </UFormField>
          <UFormField
            :label="$t('drafts.offset')"
            :error="offsetInvalid ? $t('drafts.deltaHintAdd') : undefined"
          >
            <UInput
              v-model="offsetRaw"
              :placeholder="$t('drafts.example', { value: '+30m' })"
              :color="offsetInvalid ? 'error' : undefined"
              class="w-full"
            />
          </UFormField>
          <UFormField :label="$t('drafts.newTripId')" :hint="$t('drafts.optional')">
            <UInput v-model="newTripId" :placeholder="$t('drafts.auto')" class="w-full"/>
          </UFormField>
        </template>

        <template v-else>
          <p v-if="derivationStale" class="text-sm text-warning">
            {{ $t('drafts.rebuildFirstAdd') }}
          </p>
          <template v-else>
            <UFormField :label="$t('explore.routes.pattern')">
              <USelectMenu
                v-model="patternKey"
                :items="patternItems"
                value-key="value"
                :placeholder="$t('drafts.pickPattern')"
                class="w-full"
              />
            </UFormField>
            <UFormField :label="$t('explore.col.service')">
              <USelectMenu
                v-model="serviceSel"
                :items="serviceItems ?? []"
                value-key="value"
                :placeholder="$t('drafts.pickService')"
                class="w-full"
              />
            </UFormField>
            <UFormField :label="$t('drafts.baseDeparture')">
              <UInput v-model="baseRaw" :placeholder="$t('drafts.example', { value: '06:15' })" class="w-full"/>
            </UFormField>
            <div class="grid grid-cols-3 gap-2">
              <UFormField :label="$t('explore.col.headsign')" :hint="$t('drafts.optionalShort')">
                <UInput v-model="headsign" class="w-full"/>
              </UFormField>
              <UFormField :label="$t('vehicles.detail.fact.direction')" :hint="$t('drafts.optionalShort')">
                <UInput v-model.number="directionRaw" type="number" class="w-full"/>
              </UFormField>
              <UFormField :label="$t('explore.col.block')" :hint="$t('drafts.optionalShort')">
                <UInput v-model="blockId" class="w-full"/>
              </UFormField>
            </div>
            <p v-if="selectedPattern && patternHasUnknownTimes" class="text-xs text-warning">
              {{ $t('drafts.unknownTimes') }}
            </p>
          </template>
        </template>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" :label="$t('common.cancel')" @click="open = false"/>
        <UButton
          :loading="submitting"
          :disabled="!canSubmit"
          :label="mode === 'duplicate' ? $t('drafts.duplicateTrip') : $t('drafts.addTrip')"
          @click="submit"
        />
      </div>
    </template>
  </UModal>
</template>
