<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import type {DraftEdit, DraftGridTrip} from '~/api'
import type {DraftEditCommand, DraftTripDetail} from '~/types/draftEdits'
import DraftTripPanel from '~/components/draft/DraftTripPanel.vue'
import DraftHistoryPanel from '~/components/draft/DraftHistoryPanel.vue'
import DraftValidationPanel from '~/components/draft/DraftValidationPanel.vue'

const { t } = useI18n()

const props = defineProps<{
  selectedTrip: DraftGridTrip | null
  /** The selected trip's derived detail in the draft, or null. */
  tripDetail: DraftTripDetail | null
  activeCell: { tripId: string; stopSequence: number } | null
  activeCellValue: { arrivalSec: number | null; departureSec: number | null } | null
  activeStopName?: string | null
  readOnly: boolean
  derivationStale: boolean
  lastValidation: unknown
  undoneCount: number
  errorCount: number
  /** The draft's latest edits, for the history tab. */
  edits: DraftEdit[]
  canUndo: boolean
  canRedo: boolean
  /** collapsed/expanded state now lives in the parent splitter panel. */
  collapsed: boolean
}>()

const emit = defineEmits<{
  goto: [payload: { tripId: string; stopSequence: number }]
  edit: [command: DraftEditCommand]
  'undo-steps': [count: number]
  duplicate: [tripId: string]
  expand: []
  collapse: []
}>()

type Tab = 'trip' | 'history' | 'validation'
const tab = ref<Tab>('trip')

function reveal(t: Tab) {
  tab.value = t
  emit('expand')
}

watch(
  () => props.selectedTrip?.tripId,
  (id) => {
    if (id) {
      emit('expand')
      tab.value = 'trip'
    }
  },
)

const tabs = computed<{ id: Tab; label: string; icon: string }[]>(() => [
  {id: 'trip', label: t('explore.col.trip'), icon: 'i-lucide-info'},
  {id: 'history', label: t('drafts.tabHistory'), icon: 'i-lucide-history'},
  {id: 'validation', label: t('drafts.col.validation'), icon: 'i-lucide-triangle-alert'},
])
</script>

<template>
  <div class="flex h-full w-full flex-col bg-default">
    <!-- collapsed strip -->
    <div v-if="collapsed" class="flex w-full flex-col items-center gap-2 py-2">
      <UButton
        size="xs"
        color="neutral"
        variant="ghost"
        icon="i-lucide-info"
        :aria-label="$t('explore.col.trip')"
        @click="reveal('trip')"
      />
      <UChip :show="undoneCount > 0" :text="undoneCount" color="neutral">
        <UButton
          size="xs"
          color="neutral"
          variant="ghost"
          icon="i-lucide-history"
          :aria-label="$t('drafts.tabHistory')"
          @click="reveal('history')"
        />
      </UChip>
      <UChip :show="errorCount > 0" :text="errorCount" color="error">
        <UButton
          size="xs"
          color="neutral"
          variant="ghost"
          icon="i-lucide-triangle-alert"
          :aria-label="$t('drafts.col.validation')"
          @click="reveal('validation')"
        />
      </UChip>
    </div>

    <!-- expanded panel -->
    <div v-else class="flex min-w-0 flex-1 flex-col">
      <div class="flex items-center justify-between border-b border-default px-1 py-1">
        <div class="flex gap-0.5">
          <UButton
            v-for="item in tabs"
            :key="item.id"
            size="xs"
            :color="tab === item.id ? 'primary' : 'neutral'"
            :variant="tab === item.id ? 'soft' : 'ghost'"
            :icon="item.icon"
            :label="item.label"
            :ui="{ label: 'text-xs' }"
            @click="tab = item.id"
          />
        </div>
        <UButton
          size="xs"
          color="neutral"
          variant="ghost"
          icon="i-lucide-x"
          :aria-label="$t('drafts.collapse')"
          @click="emit('collapse')"
        />
      </div>

      <div class="min-h-0 flex-1 overflow-auto">
        <template v-if="tab === 'trip'">
          <DraftTripPanel
            v-if="selectedTrip"
            :trip="selectedTrip"
            :detail="tripDetail"
            :active-cell="activeCell"
            :active-cell-value="activeCellValue"
            :active-stop-name="activeStopName"
            :read-only="readOnly"
            :derivation-stale="derivationStale"
            @edit="(command) => emit('edit', command)"
            @duplicate="(id) => emit('duplicate', id)"
          />
          <p v-else class="p-3 text-xs text-muted">{{ $t('drafts.selectTrip') }}</p>
        </template>

        <DraftHistoryPanel
          v-else-if="tab === 'history'"
          :edits="edits"
          :can-undo="canUndo"
          :can-redo="canRedo"
          :read-only="readOnly"
          @edit="(command) => emit('edit', command)"
          @undo-steps="(count) => emit('undo-steps', count)"
        />

        <DraftValidationPanel
          v-else
          :last-validation="lastValidation"
          :derivation-stale="derivationStale"
          @goto="(p) => emit('goto', p)"
        />
      </div>
    </div>
  </div>
</template>
