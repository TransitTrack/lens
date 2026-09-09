<script setup lang="ts">
import { ref, watch } from 'vue'
import type { DraftGridQuery } from '~~/generated/graphql'
import type { useDraftEditor } from '~/composables/useDraftEditor'
import DraftTripPanel from '~/components/draft/DraftTripPanel.vue'
import DraftHistoryPanel from '~/components/draft/DraftHistoryPanel.vue'
import DraftValidationPanel from '~/components/draft/DraftValidationPanel.vue'

type DraftGridTrip = DraftGridQuery['draftGrid']['trips'][number]

const props = defineProps<{
  draftId: string
  selectedTrip: DraftGridTrip | null
  activeCell: { tripId: string; stopSequence: number } | null
  activeCellValue: { arrivalSec: number | null; departureSec: number | null } | null
  activeStopName?: string | null
  readOnly: boolean
  derivationStale: boolean
  feedCode: string
  lastValidation: unknown
  undoneCount: number
  errorCount: number
  editor: ReturnType<typeof useDraftEditor>
}>()

const emit = defineEmits<{
  goto: [payload: { tripId: string; stopSequence: number }]
  changed: []
  duplicate: [tripId: string]
  deleted: [tripId: string]
}>()

type Tab = 'trip' | 'history' | 'validation'
const open = ref(false)
const tab = ref<Tab>('trip')

function reveal(t: Tab) {
  tab.value = t
  open.value = true
}

watch(
  () => props.selectedTrip?.tripId,
  (id) => {
    if (id) {
      open.value = true
      tab.value = 'trip'
    }
  },
)

const tabs: { id: Tab; label: string; icon: string }[] = [
  { id: 'trip', label: 'Trip', icon: 'i-lucide-info' },
  { id: 'history', label: 'History', icon: 'i-lucide-history' },
  { id: 'validation', label: 'Validation', icon: 'i-lucide-triangle-alert' },
]
</script>

<template>
  <div
    class="absolute right-0 top-0 bottom-0 z-10 flex border-l border-default bg-default"
    :style="{ width: open ? '240px' : '34px' }"
  >
    <!-- collapsed strip -->
    <div v-if="!open" class="flex w-full flex-col items-center gap-2 py-2">
      <UButton
        size="xs"
        color="neutral"
        variant="ghost"
        icon="i-lucide-info"
        :aria-label="'Trip'"
        @click="reveal('trip')"
      />
      <UChip :show="undoneCount > 0" :text="undoneCount" size="sm" color="neutral">
        <UButton
          size="xs"
          color="neutral"
          variant="ghost"
          icon="i-lucide-history"
          :aria-label="'History'"
          @click="reveal('history')"
        />
      </UChip>
      <UChip :show="errorCount > 0" :text="errorCount" size="sm" color="error">
        <UButton
          size="xs"
          color="neutral"
          variant="ghost"
          icon="i-lucide-triangle-alert"
          :aria-label="'Validation'"
          @click="reveal('validation')"
        />
      </UChip>
    </div>

    <!-- expanded panel -->
    <div v-else class="flex min-w-0 flex-1 flex-col">
      <div class="flex items-center justify-between border-b border-default px-1 py-1">
        <div class="flex gap-0.5">
          <UButton
            v-for="t in tabs"
            :key="t.id"
            size="xs"
            :color="tab === t.id ? 'primary' : 'neutral'"
            :variant="tab === t.id ? 'soft' : 'ghost'"
            :icon="t.icon"
            :label="t.label"
            :ui="{ label: 'text-xs' }"
            @click="tab = t.id"
          />
        </div>
        <UButton
          size="xs"
          color="neutral"
          variant="ghost"
          icon="i-lucide-x"
          aria-label="Collapse"
          @click="open = false"
        />
      </div>

      <div class="min-h-0 flex-1 overflow-auto">
        <template v-if="tab === 'trip'">
          <DraftTripPanel
            v-if="selectedTrip"
            :trip="selectedTrip"
            :active-cell="activeCell"
            :active-cell-value="activeCellValue"
            :active-stop-name="activeStopName"
            :read-only="readOnly"
            :derivation-stale="derivationStale"
            :feed-code="feedCode"
            :draft-id="draftId"
            :editor="editor"
            @changed="emit('changed')"
            @duplicate="(id) => emit('duplicate', id)"
            @deleted="(id) => emit('deleted', id)"
          />
          <p v-else class="p-3 text-xs text-muted">Select a trip to inspect it.</p>
        </template>

        <DraftHistoryPanel
          v-else-if="tab === 'history'"
          :draft-id="draftId"
          :editor="editor"
          @changed="emit('changed')"
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
