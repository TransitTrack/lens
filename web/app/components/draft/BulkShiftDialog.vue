<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  useDraftPatternsQuery,
  useBulkShiftTripsMutation,
  type DraftGridQuery,
} from '~~/generated/graphql'
import type { useDraftEditor } from '~/composables/useDraftEditor'
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

const previewCount = computed(() => {
  const from = windowFromSec.value
  const to = windowToSec.value
  return props.trips.filter((t) => {
    if (serviceSel.value && t.serviceId !== serviceSel.value) return false
    if (from != null || to != null) {
      const d = t.firstDepartureSec
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

const { mutate: bulkShift } = useBulkShiftTripsMutation()
const submitting = ref(false)

async function submit() {
  if (deltaSec.value == null) return
  submitting.value = true
  try {
    const data = await props.editor.mutate(async (v) => {
      const res = await bulkShift({
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
      })
      return res?.data
    })
    if (data?.bulkShiftTrips) {
      props.editor.applyResult(data.bulkShiftTrips)
      await props.editor.refetchEdits()
      emit('changed')
      open.value = false
    }
  } catch {
    // toast already fired inside editor.mutate
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <UModal v-model:open="open" title="Bulk shift trips">
    <template #body>
      <div class="flex flex-col gap-3">
        <UFormField label="Route">
          <UInput :model-value="routeId" readonly class="w-full" />
        </UFormField>

        <UFormField label="Pattern" hint="optional">
          <USelectMenu
            v-model="patternKey"
            :items="patternItems"
            value-key="value"
            :disabled="derivationStale"
            placeholder="All patterns"
            class="w-full"
          />
          <template v-if="derivationStale" #help>
            <span class="text-warning">Rebuild the draft first to filter by pattern.</span>
          </template>
        </UFormField>

        <UFormField label="Service" hint="optional">
          <USelectMenu
            v-model="serviceSel"
            :items="serviceItems ?? []"
            value-key="value"
            placeholder="All services"
            class="w-full"
          />
        </UFormField>

        <div class="grid grid-cols-2 gap-2">
          <UFormField label="From" hint="optional">
            <UInput v-model="fromRaw" placeholder="e.g. 06:00" class="w-full" />
          </UFormField>
          <UFormField label="To" hint="optional">
            <UInput v-model="toRaw" placeholder="e.g. 09:30" class="w-full" />
          </UFormField>
        </div>

        <UFormField label="Delta" :error="deltaInvalid ? 'Enter a signed delta: +3m, -90, +1:30' : undefined">
          <UInput
            v-model="deltaRaw"
            placeholder="e.g. +3m or -90"
            :color="deltaInvalid ? 'error' : undefined"
            class="w-full"
          />
        </UFormField>

        <p class="text-xs text-muted">
          Will shift ~{{ previewCount }} trip{{ previewCount === 1 ? '' : 's' }}
          <template v-if="deltaLabel"> by {{ deltaLabel }}</template>
          <template v-if="patternKey"> (before pattern filter)</template>.
        </p>
      </div>
    </template>
    <template #footer>
      <div class="flex justify-end gap-2">
        <UButton color="neutral" variant="ghost" label="Cancel" @click="open = false" />
        <UButton
          :loading="submitting"
          :disabled="deltaSec == null"
          label="Shift trips"
          @click="submit"
        />
      </div>
    </template>
  </UModal>
</template>
