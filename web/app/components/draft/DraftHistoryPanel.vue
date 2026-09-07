<script setup lang="ts">
import { computed, watch } from 'vue'
import {
  useDraftEditsQuery,
  useUndoDraftEditMutation,
  useRedoDraftEditMutation,
} from '~~/generated/graphql'
import type { useDraftEditor } from '~/composables/useDraftEditor'

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

async function undoOnce(): Promise<boolean> {
  const data = await props.editor.mutate(async (vars) => {
    const res = await undoDraftEdit({
      id: vars.draftId,
      editor: vars.editor,
      expectedVersion: vars.expectedVersion,
    })
    return res?.data
  })
  if (data?.undoDraftEdit) {
    props.editor.applyResult(data.undoDraftEdit)
    return true
  }
  return false
}

async function onUndo() {
  if (await undoOnce()) {
    await props.editor.refetchEdits()
    await refetch()
    emit('changed')
  }
}

async function onRedo() {
  const data = await props.editor.mutate(async (vars) => {
    const res = await redoDraftEdit({
      id: vars.draftId,
      editor: vars.editor,
      expectedVersion: vars.expectedVersion,
    })
    return res?.data
  })
  if (data?.redoDraftEdit) {
    props.editor.applyResult(data.redoDraftEdit)
    await props.editor.refetchEdits()
    await refetch()
    emit('changed')
  }
}

function topActiveSeq(): number | null {
  let max: number | null = null
  for (const e of result.value?.draftEdits ?? []) {
    if (!e.undone && (max == null || e.seq > max)) max = e.seq
  }
  return max
}

async function undoToHere(seq: number) {
  // undo while the newest active edit is above this row
  let guard = 0
  while (guard++ < 500) {
    const top = topActiveSeq()
    if (top == null || top <= seq) break
    if (!(await undoOnce())) break
  }
  await props.editor.refetchEdits()
  await refetch()
  emit('changed')
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
