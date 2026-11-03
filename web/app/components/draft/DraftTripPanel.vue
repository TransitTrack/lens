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
import { parseDeltaInput, parseTimeInput, secToClock } from '~/utils/gtfsTime'

type DraftGridTrip = DraftGridQuery['draftGrid']['trips'][number]

const props = defineProps<{
  trip: DraftGridTrip
  activeCell: { tripId: string; stopSequence: number } | null
  activeCellValue: { arrivalSec: number | null; departureSec: number | null } | null
  activeStopName?: string | null
  readOnly: boolean
  derivationStale: boolean
  feedCode: string
  draftId: string
  editor: ReturnType<typeof useDraftEditor>
}>()

const { t } = useI18n()

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
const arrText = ref('')
const depText = ref('')
watch(
  () => props.activeCellValue,
  (v) => {
    arr.value = v?.arrivalSec ?? null
    dep.value = v?.departureSec ?? null
    arrText.value = secToClock(v?.arrivalSec ?? null)
    depText.value = secToClock(v?.departureSec ?? null)
    dwell.value =
      v?.arrivalSec != null && v?.departureSec != null ? v.departureSec - v.arrivalSec : null
  },
  { immediate: true },
)

function commitArrText() {
  const parsed = parseTimeInput(arrText.value, arr.value ?? dep.value ?? null)
  if (parsed == null) {
    arrText.value = secToClock(arr.value)
    return
  }
  arr.value = parsed
  void commitStopTime()
}

function commitDepText() {
  const parsed = parseTimeInput(depText.value, dep.value ?? arr.value ?? null)
  if (parsed == null) {
    depText.value = secToClock(dep.value)
    return
  }
  dep.value = parsed
  void commitStopTime()
}

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
  props.trip.directionId == null ? '—' : t('explore.trips.direction', { dir: props.trip.directionId }),
)
</script>

<template>
  <div class="flex flex-col gap-4 p-3 text-sm">
    <div>
      <div class="font-medium break-all">{{ trip.tripId }}</div>
      <dl class="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs text-muted">
        <dt>{{ $t('explore.col.service') }}</dt>
        <dd class="text-default">{{ trip.serviceId ?? '—' }}</dd>
        <dt>{{ $t('explore.col.block') }}</dt>
        <dd class="text-default">{{ trip.blockId ?? '—' }}</dd>
        <dt>{{ $t('explore.col.headsign') }}</dt>
        <dd class="text-default">{{ trip.headsign ?? '—' }}</dd>
        <dt>{{ $t('vehicles.detail.fact.direction') }}</dt>
        <dd class="text-default">{{ dirLabel }}</dd>
      </dl>
    </div>

    <div class="border-t border-default pt-3">
      <div class="mb-1 text-xs font-semibold uppercase text-muted">{{ $t('drafts.derived') }}</div>
      <p v-if="derivationStale" class="text-xs text-muted">
        {{ $t('drafts.rebuildRefresh') }}
      </p>
      <dl
        v-else-if="detail"
        class="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs text-muted"
      >
        <dt>{{ $t('explore.routes.pattern') }}</dt>
        <dd class="text-default break-all">{{ detail.pattern?.patternKey ?? '—' }}</dd>
        <dt>{{ $t('nav.stops') }}</dt>
        <dd class="text-default">{{ detail.pattern?.stopCount ?? '—' }}</dd>
        <dt>{{ $t('explore.trip.length') }}</dt>
        <dd class="text-default">
          {{ detail.pattern?.lengthM != null ? $t('drafts.lengthKm', { km: (detail.pattern.lengthM / 1000).toFixed(1) }) : '—' }}
        </dd>
        <dt>{{ $t('drafts.runTime') }}</dt>
        <dd class="text-default">{{ runTime ?? '—' }}</dd>
      </dl>
      <p v-else class="text-xs text-muted">{{ $t('drafts.noDerived') }}</p>
    </div>

    <div class="border-t border-default pt-3">
      <div class="mb-2 text-xs font-semibold uppercase text-muted">{{ $t('drafts.actions') }}</div>
      <div class="flex flex-wrap gap-2">
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          :label="$t('drafts.shiftMinus')"
          :disabled="readOnly"
          @click="applyShift(-60)"
        />
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          :label="$t('drafts.shiftPlus')"
          :disabled="readOnly"
          @click="applyShift(60)"
        />
      </div>
      <div class="mt-2 flex gap-2">
        <UInput
          v-model="shiftRaw"
          size="xs"
          :placeholder="$t('drafts.deltaExample')"
          :disabled="readOnly"
          :color="shiftInvalid ? 'error' : undefined"
          class="flex-1"
          @keydown.enter="applyShiftInput"
        />
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          :label="$t('drafts.apply')"
          :disabled="readOnly || shiftDelta == null"
          @click="applyShiftInput"
        />
      </div>
      <p v-if="shiftInvalid" class="mt-1 text-xs text-error">
        {{ $t('drafts.deltaHint') }}
      </p>
      <div class="mt-3 flex flex-wrap gap-2">
        <UButton
          size="xs"
          color="neutral"
          variant="soft"
          icon="i-lucide-copy"
          :label="$t('drafts.duplicate')"
          :disabled="readOnly"
          @click="emit('duplicate', trip.tripId)"
        />
        <UButton
          size="xs"
          color="error"
          variant="soft"
          icon="i-lucide-trash-2"
          :label="$t('drafts.deleteTrip')"
          :disabled="readOnly"
          @click="deleteOpen = true"
        />
      </div>
    </div>

    <div v-if="showCell" class="border-t border-default pt-3">
      <div class="mb-2 text-xs font-semibold uppercase text-muted">
        {{ activeStopName ?? $t('drafts.stopNumber', { n: activeCell?.stopSequence }) }}
      </div>
      <div class="grid grid-cols-3 gap-2">
        <UFormField :label="$t('drafts.arrival')" size="xs">
          <UInput v-model="arrText" type="text" size="xs" :placeholder="$t('drafts.example', { value: '7:33' })" :disabled="readOnly" @change="commitArrText" />
        </UFormField>
        <UFormField :label="$t('drafts.departure')" size="xs">
          <UInput v-model="depText" type="text" size="xs" :placeholder="$t('drafts.example', { value: '7:33' })" :disabled="readOnly" @change="commitDepText" />
        </UFormField>
        <UFormField :label="$t('drafts.dwell')" size="xs">
          <UInput v-model.number="dwell" type="number" size="xs" :disabled="readOnly" @change="commitDwell" />
        </UFormField>
      </div>
    </div>

    <UModal v-model:open="deleteOpen" :title="$t('drafts.deleteTitle')">
      <template #body>
        <i18n-t keypath="drafts.deleteBody" tag="p" class="text-sm text-muted">
          <template #id>
            <span class="font-mono">{{ trip.tripId }}</span>
          </template>
        </i18n-t>
      </template>
      <template #footer>
        <div class="flex justify-end gap-2">
          <UButton color="neutral" variant="ghost" :label="$t('common.cancel')" @click="deleteOpen = false" />
          <UButton color="error" :label="$t('drafts.delete')" @click="confirmDelete" />
        </div>
      </template>
    </UModal>
  </div>
</template>
