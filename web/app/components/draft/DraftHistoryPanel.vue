<script setup lang="ts">
import { computed, watch } from 'vue'
import {
  useDraftEditsQuery,
  useUndoDraftEditMutation,
  useRedoDraftEditMutation,
} from '~~/generated/graphql'
import type { useDraftEditor } from '~/composables/useDraftEditor'
import { useDraftEdit } from '~/composables/useDraftEdit'

const props = defineProps<{
  draftId: string
  editor: ReturnType<typeof useDraftEditor>
}>()

const emit = defineEmits<{ changed: [] }>()

const { result, refetch } = useDraftEditsQuery(
  () => ({ id: props.draftId, limit: 200 }),
  () => ({ enabled: !!props.draftId }),
)

// newest-first
const rows = computed(() => {
  const list = [...(result.value?.draftEdits ?? [])]
  list.sort((a, b) => b.seq - a.seq)
  return list
})

// refetch whenever the draft version changes (an edit landed)
watch(
  () => props.editor.draft.value?.version,
  () => {
    void refetch()
  },
)

const { mutate: undoDraftEdit } = useUndoDraftEditMutation()
const { mutate: redoDraftEdit } = useRedoDraftEditMutation()

const { run } = useDraftEdit(props.editor, {
  onChanged: async () => {
    await refetch()
    emit('changed')
  },
})

async function undoOnce(): Promise<boolean> {
  const result = await run(
    (v) =>
      undoDraftEdit({ id: v.draftId, editor: v.editor, expectedVersion: v.expectedVersion }),
    'undoDraftEdit',
  )
  return result != null
}

async function onUndo() {
  await undoOnce()
}

async function onRedo() {
  await run(
    (v) =>
      redoDraftEdit({ id: v.draftId, editor: v.editor, expectedVersion: v.expectedVersion }),
    'redoDraftEdit',
  )
}

async function undoToHere(targetSeq: number) {
  // count the active edits newer than this row up front — the list can't
  // refresh inside the tight await loop, so don't re-read it per iteration.
  const n = (result.value?.draftEdits ?? []).filter(
    (e) => !e.undone && e.seq > targetSeq,
  ).length
  for (let i = 0; i < n; i++) {
    if (!(await undoOnce())) break
  }
}

function fmt(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleTimeString()
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
        label="Undo"
        :disabled="!editor.canUndo.value || editor.readOnly.value"
        @click="onUndo"
      />
      <UButton
        size="xs"
        color="neutral"
        variant="soft"
        icon="i-lucide-redo-2"
        label="Redo"
        :disabled="!editor.canRedo.value || editor.readOnly.value"
        @click="onRedo"
      />
    </div>

    <p v-if="rows.length === 0" class="text-xs text-muted">No edits yet.</p>

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
          :disabled="editor.readOnly.value"
          @click="undoToHere(row.seq)"
        >
          undo to here
        </button>
      </li>
    </ul>
  </div>
</template>
