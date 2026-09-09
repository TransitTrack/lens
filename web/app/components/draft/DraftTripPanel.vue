<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  useDraftTripDetailQuery,
  useShiftTripMutation,
  useDeleteTripMutation,
  useUpdateStopTimeMutation,
  useSetStopDwellMutation,
  type DraftGridQuery,
} from '~~/generated/graphql'
import type { useDraftEditor } from '~/composables/useDraftEditor'
import { useDraftEdit } from '~/composables/useDraftEdit'
import { parseDeltaInput, secToClock } from '~/utils/gtfsTime'

type DraftGridTrip = DraftGridQuery['draftGrid']['trips'][number]

const props = defineProps<{
  trip: DraftGridTrip
  activeCell: { tripId: string; stopSequence: number } | null
  activeCellValue: { arrivalSec: number | null; departureSec: number | null } | null
  readOnly: boolean
  derivationStale: boolean
  feedCode: string
  draftId: string
  editor: ReturnType<typeof useDraftEditor>
}>()

const emit = defineEmits<{
  changed: []
  duplicate: [tripId: string]
  deleted: [tripId: string]
}>()

// --- derived detail ---------------------------------------------------
const { result: detailResult } = useDraftTripDetailQuery(
  () => ({ feedCode: props.feedCode, tripId: props.trip.tripId, revisionId: props.draftId }),
  () => ({ enabled: !props.derivationStale && !!props.trip }),
)
const detail = computed(() => detailResult.value?.trip ?? null)
const runTime = computed(() => {
  const d = detail.value
  if (!d || d.startTimeSec == null || d.endTimeSec == null) return null
  return secToClock(d.endTimeSec - d.startTimeSec)
})

const { run } = useDraftEdit(props.editor, { onChanged: () => emit('changed') })

// --- shift ----------------------------------------------------------
const { mutate: shiftTrip } = useShiftTripMutation()
const shiftRaw = ref('')

const shiftDelta = computed(() => parseDeltaInput(shiftRaw.value))
const shiftInvalid = computed(() => shiftRaw.value.trim() !== '' && shiftDelta.value == null)

async function applyShift(deltaSec: number) {
  await run(
    (v) =>
      shiftTrip({
        input: {
          draftId: v.draftId,
          editor: v.editor,
          expectedVersion: v.expectedVersion,
          tripId: props.trip.tripId,
          deltaSec,
        },
      }),
    'shiftTrip',
  )
}

function applyShiftInput() {
  const delta = shiftDelta.value
  if (delta == null) return
  void applyShift(delta)
  shiftRaw.value = ''
}

// --- delete -------------------------------------------------------
const { mutate: deleteTrip } = useDeleteTripMutation()
const deleteOpen = ref(false)

async function confirmDelete() {
  const result = await run(
    (v) =>
      deleteTrip({
        input: {
          draftId: v.draftId,
          editor: v.editor,
          expectedVersion: v.expectedVersion,
          tripId: props.trip.tripId,
        },
      }),
    'deleteTrip',
  )
  if (result) {
    deleteOpen.value = false
    emit('deleted', props.trip.tripId)
  }
}

// --- active cell -------------------------------------------------
const showCell = computed(
  () => props.activeCell != null && props.activeCell.tripId === props.trip.tripId,
)
const arr = ref<number | null>(null)
const dep = ref<number | null>(null)
const dwell = ref<number | null>(null)
watch(
  () => props.activeCellValue,
  (v) => {
    arr.value = v?.arrivalSec ?? null
    dep.value = v?.departureSec ?? null
    dwell.value =
      v?.arrivalSec != null && v?.departureSec != null ? v.departureSec - v.arrivalSec : null
  },
  { immediate: true },
)

const { mutate: updateStopTime } = useUpdateStopTimeMutation()
const { mutate: setStopDwell } = useSetStopDwellMutation()

async function commitStopTime() {
  const seq = props.activeCell?.stopSequence
  if (seq == null) return
  await run(
    (v) =>
      updateStopTime({
        input: {
          draftId: v.draftId,
          editor: v.editor,
          expectedVersion: v.expectedVersion,
          tripId: props.trip.tripId,
          stopSequence: seq,
          arrivalSec: arr.value,
          departureSec: dep.value,
        },
      }),
    'updateStopTime',
  )
}

async function commitDwell() {
  const seq = props.activeCell?.stopSequence
  if (seq == null || dwell.value == null) return
  await run(
    (v) =>
      setStopDwell({
        input: {
          draftId: v.draftId,
          editor: v.editor,
          expectedVersion: v.expectedVersion,
          tripId: props.trip.tripId,
          stopSequence: seq,
          dwellSec: dwell.value as number,
        },
      }),
    'setStopDwell',
  )
}

const dirLabel = computed(() =>
  props.trip.directionId == null ? '—' : `Direction ${props.trip.directionId}`,
)
</script>

<template>
  <div class="flex flex-col gap-4 p-3 text-sm">
    <div>
      <div class="font-medium break-all">{{ trip.tripId }}</div>
      <dl class="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs text-muted">
        <dt>Service</dt>
        <dd class="text-default">{{ trip.serviceId ?? '—' }}</dd>
        <dt>Block</dt>
        <dd class="text-default">{{ trip.blockId ?? '—' }}</dd>
        <dt>Headsign</dt>
        <dd class="text-default">{{ trip.headsign ?? '—' }}</dd>
        <dt>Direction</dt>
        <dd class="text-default">{{ dirLabel }}</dd>
      </dl>
    </div>

    <div class="border-t border-default pt-3">
      <div class="mb-1 text-xs font-semibold uppercase text-muted">Derived</div>
      <p v-if="derivationStale" class="text-xs text-muted">
        Rebuild to refresh pattern &amp; run-time.
      </p>
      <dl
        v-else-if="detail"
        class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs text-muted"
      >
        <dt>Pattern</dt>
        <dd class="text-default break-all">{{ detail.pattern?.patternKey ?? '—' }}</dd>
        <dt>Stops</dt>
        <dd class="text-default">{{ detail.pattern?.stopCount ?? '—' }}</dd>
        <dt>Length</dt>
        <dd class="text-default">
          {{ detail.pattern?.lengthM != null ? (detail.pattern.lengthM / 1000).toFixed(1) + ' km' : '—' }}
        </dd>
        <dt>Run time</dt>
        <dd class="text-default">{{ runTime ?? '—' }}</dd>
      </dl>
      <p v-else class="text-xs text-muted">No derived data.</p>
    </div>

    <div class="border-t border-default pt-3">
      <div class="mb-2 text-xs font-semibold uppercase text-muted">Actions</div>
      <div class="flex flex-wrap gap-2">
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          label="Shift −1m"
          :disabled="readOnly"
          @click="applyShift(-60)"
        />
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          label="Shift +1m"
          :disabled="readOnly"
          @click="applyShift(60)"
        />
      </div>
      <div class="mt-2 flex gap-2">
        <UInput
          v-model="shiftRaw"
          size="xs"
          placeholder="e.g. +3m or -90"
          :disabled="readOnly"
          :color="shiftInvalid ? 'error' : undefined"
          class="flex-1"
          @keydown.enter="applyShiftInput"
        />
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          label="Apply"
          :disabled="readOnly || shiftDelta == null"
          @click="applyShiftInput"
        />
      </div>
      <p v-if="shiftInvalid" class="mt-1 text-xs text-error">
        Enter a signed delta: +3m, -90, +1:30
      </p>
      <div class="mt-3 flex flex-wrap gap-2">
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          icon="i-lucide-copy"
          label="Duplicate"
          :disabled="readOnly"
          @click="emit('duplicate', trip.tripId)"
        />
        <UButton
          size="xs"
          color="error"
          variant="soft"
          icon="i-lucide-trash-2"
          label="Delete trip"
          :disabled="readOnly"
          @click="deleteOpen = true"
        />
      </div>
    </div>

    <div v-if="showCell" class="border-t border-default pt-3">
      <div class="mb-2 text-xs font-semibold uppercase text-muted">
        Stop #{{ activeCell?.stopSequence }}
      </div>
      <div class="grid grid-cols-3 gap-2">
        <UFormField label="Arrival" size="xs">
          <UInput v-model.number="arr" type="number" size="xs" :disabled="readOnly" @change="commitStopTime" />
        </UFormField>
        <UFormField label="Departure" size="xs">
          <UInput v-model.number="dep" type="number" size="xs" :disabled="readOnly" @change="commitStopTime" />
        </UFormField>
        <UFormField label="Dwell" size="xs">
          <UInput v-model.number="dwell" type="number" size="xs" :disabled="readOnly" @change="commitDwell" />
        </UFormField>
      </div>
    </div>

    <UModal v-model:open="deleteOpen" title="Delete trip?">
      <template #body>
        <p class="text-sm text-muted">
          This removes trip <span class="font-mono">{{ trip.tripId }}</span> from the draft.
          You can undo it from the history panel.
        </p>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton color="neutral" variant="ghost" label="Cancel" @click="deleteOpen = false" />
          <UButton color="error" label="Delete" @click="confirmDelete" />
        </div>
      </template>
    </UModal>
  </div>
</template>
