<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  useDraftPatternsQuery,
  useBulkShiftTripsMutation,
  type DraftGridQuery,
} from '~~/generated/graphql'
import type { useDraftEditor } from '~/composables/useDraftEditor'
import { useDraftEdit } from '~/composables/useDraftEdit'
import { parseTimeInput, parseDeltaInput, secToClock } from '~/utils/gtfsTime'

type DraftGridTrip = DraftGridQuery['draftGrid']['trips'][number]

const open = defineModel<boolean>('open', { default: false })

const props = defineProps<{
  editor: ReturnType<typeof useDraftEditor>
  feedCode: string
  routeId: string
  serviceId: string | null
  derivationStale: boolean
  trips: DraftGridTrip[]
  serviceItems?: { label: string; value: string }[]
}>()

const emit = defineEmits<{ changed: [] }>()

const { t } = useI18n()

const patternKey = ref<string | null>(null)
const serviceSel = ref<string | null>(null)
const fromRaw = ref('')
const toRaw = ref('')
const deltaRaw = ref('')

watch(open, (isOpen) => {
  if (isOpen) {
    patternKey.value = null
    serviceSel.value = props.serviceId
    fromRaw.value = ''
    toRaw.value = ''
    deltaRaw.value = ''
  }
})

const { result: patternsResult } = useDraftPatternsQuery(
  () => ({
    feedCode: props.feedCode,
    routeId: props.routeId,
    revisionId: props.editor.draft.value?.id ?? '',
  }),
  () => ({
    enabled: !!props.routeId && !!props.editor.draft.value?.id && !props.derivationStale && open.value,
  }),
)
const patternItems = computed(() =>
  (patternsResult.value?.tripPatterns ?? []).map((p) => ({
    label: p.headsign ?? p.patternKey,
    value: p.patternKey,
  })),
)

const windowFromSec = computed(() => parseTimeInput(fromRaw.value, null))
const windowToSec = computed(() => parseTimeInput(toRaw.value, null))
const deltaSec = computed(() => parseDeltaInput(deltaRaw.value))
const deltaInvalid = computed(() => deltaRaw.value.trim() !== '' && deltaSec.value == null)
// An empty window field is a valid open bound; a non-empty unparseable one is not.
const fromInvalid = computed(() => fromRaw.value.trim() !== '' && windowFromSec.value == null)
const toInvalid = computed(() => toRaw.value.trim() !== '' && windowToSec.value == null)
const windowInvalid = computed(() => fromInvalid.value || toInvalid.value)

// The grid (`props.trips`) is already server-filtered by the page's active
// direction + service. The backend bulk-shift has no directionId filter, so it
// shifts every direction of the route — the client count can only ever be a
// rough "what's on screen" hint, and it's meaningless when the dialog's service
// selection diverges from the page's active service filter.
const serviceFilterDiverges = computed(
  () => (serviceSel.value || null) !== (props.serviceId || null),
)
const previewCount = computed(() => {
  const from = windowFromSec.value
  const to = windowToSec.value
  return props.trips.filter((trip) => {
    if (from != null || to != null) {
      const d = trip.firstDepartureSec
      if (d == null) return false
      if (from != null && d < from) return false
      if (to != null && d > to) return false
    }
    return true
  }).length
})

const deltaLabel = computed(() => {
  const d = deltaSec.value
  if (d == null) return ''
  return (d < 0 ? '-' : '+') + secToClock(Math.abs(d))
})

const shiftNote = computed(() => {
  const delta = deltaLabel.value
  if (serviceFilterDiverges.value) {
    return delta ? t('drafts.countUnavailableShift', { delta }) : t('drafts.countUnavailable')
  }
  const count = previewCount.value
  const preview = delta
    ? t('drafts.previewShift', { count, delta }, count)
    : t('drafts.previewCount', { count }, count)
  return `${preview} ${patternKey.value ? t('drafts.applyAllPattern') : t('drafts.applyAll')}`
})

const { mutate: bulkShift } = useBulkShiftTripsMutation()
const { run } = useDraftEdit(props.editor, { onChanged: () => emit('changed') })
const submitting = ref(false)

async function submit() {
  if (deltaSec.value == null || windowInvalid.value) return
  submitting.value = true
  try {
    const result = await run(
      (v) =>
        bulkShift({
          input: {
            draftId: v.draftId,
            editor: v.editor,
            expectedVersion: v.expectedVersion,
            routeId: props.routeId,
            patternKey: patternKey.value || null,
            serviceId: serviceSel.value || null,
            windowFromSec: windowFromSec.value,
            windowToSec: windowToSec.value,
            deltaSec: deltaSec.value as number,
          },
        }),
      'bulkShiftTrips',
    )
    if (result) open.value = false
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <UModal v-model:open="open" :title="$t('drafts.bulkTitle')">
    <template #body>
      <div class="flex flex-col gap-3">
        <UFormField :label="$t('explore.col.route')">
          <UInput :model-value="routeId" readonly class="w-full" />
        </UFormField>

        <UFormField :label="$t('explore.routes.pattern')" :hint="$t('drafts.optional')">
          <USelectMenu
            v-model="patternKey"
            :items="patternItems"
            value-key="value"
            :disabled="derivationStale"
            :placeholder="$t('drafts.allPatterns')"
            class="w-full"
          />
          <template v-if="derivationStale" #help>
            <span class="text-warning">{{ $t('drafts.rebuildFirstFilter') }}</span>
          </template>
        </UFormField>

        <UFormField :label="$t('explore.col.service')" :hint="$t('drafts.optional')">
          <USelectMenu
            v-model="serviceSel"
            :items="serviceItems ?? []"
            value-key="value"
            :placeholder="$t('explore.blocks.allServices')"
            class="w-full"
          />
        </UFormField>

        <div class="grid grid-cols-2 gap-2">
          <UFormField
            :label="$t('drafts.from')"
            :hint="$t('drafts.optional')"
            :error="fromInvalid ? $t('drafts.useHm', { value: '06:00' }) : undefined"
          >
            <UInput
              v-model="fromRaw"
              :placeholder="$t('drafts.example', { value: '06:00' })"
              :color="fromInvalid ? 'error' : undefined"
              class="w-full"
            />
          </UFormField>
          <UFormField
            :label="$t('drafts.to')"
            :hint="$t('drafts.optional')"
            :error="toInvalid ? $t('drafts.useHm', { value: '09:30' }) : undefined"
          >
            <UInput
              v-model="toRaw"
              :placeholder="$t('drafts.example', { value: '09:30' })"
              :color="toInvalid ? 'error' : undefined"
              class="w-full"
            />
          </UFormField>
        </div>

        <UFormField :label="$t('drafts.delta')" :error="deltaInvalid ? $t('drafts.deltaHint') : undefined">
          <UInput
            v-model="deltaRaw"
            :placeholder="$t('drafts.deltaExample')"
            :color="deltaInvalid ? 'error' : undefined"
            class="w-full"
          />
        </UFormField>

        <p class="text-xs text-muted">{{ shiftNote }}</p>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" :label="$t('common.cancel')" @click="open = false" />
        <UButton
          :loading="submitting"
          :disabled="deltaSec == null || windowInvalid"
          :label="$t('drafts.shiftTrips')"
          @click="submit"
        />
      </div>
    </template>
  </UModal>
</template>
