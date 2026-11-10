<script setup lang="ts">
import { computed } from 'vue'
import type { DraftEdit } from '~/api'
import type { DraftEditCommand } from '~/types/draftEdits'
import { formatClock } from '~/utils/clock'

const props = defineProps<{
  /** The draft's latest edits (read by the page). */
  edits: DraftEdit[]
  canUndo: boolean
  canRedo: boolean
  readOnly: boolean
}>()

const emit = defineEmits<{
  edit: [command: DraftEditCommand]
  /** Undo the newest `count` active edits — the page runs them as one burst. */
  'undo-steps': [count: number]
}>()

const { locale } = useI18n()

// newest-first
const rows = computed(() => {
  const list = [...props.edits]
  list.sort((a, b) => b.seq - a.seq)
  return list
})

function onUndo() {
  emit('edit', { op: 'undoDraftEdit' })
}

function onRedo() {
  emit('edit', { op: 'redoDraftEdit' })
}

function undoToHere(targetSeq: number) {
  // count the active edits newer than this row up front — the list can't
  // refresh inside the page's await loop, so it doesn't re-read it per step.
  const n = props.edits.filter((e) => !e.undone && e.seq > targetSeq).length
  if (n > 0) emit('undo-steps', n)
}

function fmt(iso: string): string {
  return formatClock(iso, { seconds: true, locale: locale.value })
}
</script>

<template>
  <div class="flex flex-col gap-3 p-3 text-sm">
    <div class="flex gap-2">
      <UButton
        size="xs"
        color="neutral"
        variant="soft"
        icon="i-lucide-undo-2"
        :label="$t('drafts.undo')"
        :disabled="!canUndo || readOnly"
        @click="onUndo"
      />
      <UButton
        size="xs"
        color="neutral"
        variant="soft"
        icon="i-lucide-redo-2"
        :label="$t('drafts.redo')"
        :disabled="!canRedo || readOnly"
        @click="onRedo"
      />
    </div>

    <p v-if="rows.length === 0" class="text-xs text-muted">{{ $t('drafts.noEdits') }}</p>

    <ul class="flex flex-col gap-1">
      <li
        v-for="(row, i) in rows"
        :key="row.seq"
        class="flex flex-col gap-0.5 rounded px-2 py-1"
        :class="row.undone ? 'text-dimmed line-through' : 'text-default'"
      >
        <div class="flex items-baseline justify-between gap-2">
          <span class="text-xs font-medium">{{ row.op }}</span>
          <span class="text-[0.65rem] text-dimmed">{{ fmt(row.appliedAt) }}</span>
        </div>
        <span class="text-xs text-muted">{{ row.summary }}</span>
        <button
          v-if="!row.undone && i !== 0"
          type="button"
          class="self-start text-[0.65rem] text-primary hover:underline disabled:opacity-50"
          :disabled="readOnly"
          @click="undoToHere(row.seq)"
        >
          {{ $t('drafts.undoToHere') }}
        </button>
      </li>
    </ul>
  </div>
</template>
