<script setup lang="ts">
import { nextTick, ref } from 'vue'
import { parseTimeInput, secToClock } from '~/utils/gtfsTime'

const props = defineProps<{
  arrivalSec: number | null
  departureSec: number | null
  readOnly: boolean
  isActive: boolean
}>()

const emit = defineEmits<{
  commit: [payload: { arrivalSec: number | null; departureSec: number | null }]
  activate: []
}>()

const editing = ref(false)
const shake = ref(false)
const draft = ref('')
const inputEl = ref<HTMLInputElement | null>(null)

function onClick() {
  emit('activate')
  if (props.readOnly || editing.value) return
  draft.value = secToClock(props.arrivalSec)
  editing.value = true
  void nextTick(() => {
    inputEl.value?.focus()
    inputEl.value?.select()
  })
}

function cancel() {
  editing.value = false
}

function commit() {
  const base = props.arrivalSec ?? props.departureSec ?? null
  const parsed = parseTimeInput(draft.value, base)
  if (parsed == null) {
    shake.value = true
    window.setTimeout(() => {
      shake.value = false
    }, 300)
    void nextTick(() => inputEl.value?.select())
    return
  }
  const dwell = (props.departureSec ?? parsed) - (props.arrivalSec ?? parsed)
  emit('commit', { arrivalSec: parsed, departureSec: parsed + dwell })
  editing.value = false
}
</script>

<template>
  <td
    class="grid-cell"
    :class="{ 'is-active': isActive, 'is-editable': !readOnly }"
    @click="onClick"
  >
    <input
      v-if="editing"
      ref="inputEl"
      v-model="draft"
      class="grid-cell-input"
      :class="{ shake }"
      type="text"
      @keydown.enter.prevent="commit"
      @keydown.esc.prevent="cancel"
      @blur="cancel"
      @click.stop
    >
    <template v-else-if="arrivalSec != null || departureSec != null">
      <span class="grid-cell-arr">{{ secToClock(arrivalSec) }}</span>
      <span
        v-if="arrivalSec !== departureSec"
        class="grid-cell-dep"
      >{{ secToClock(departureSec) }}</span>
    </template>
  </td>
</template>

<style scoped>
.grid-cell {
  width: 84px;
  min-width: 84px;
  max-width: 84px;
  height: 40px;
  padding: 2px 4px;
  border-right: 1px solid var(--ui-border);
  border-bottom: 1px solid var(--ui-border);
  text-align: center;
  font-variant-numeric: tabular-nums;
  font-size: 0.75rem;
  line-height: 1.1;
  vertical-align: middle;
  overflow: hidden;
}
.grid-cell.is-editable {
  cursor: text;
}
.grid-cell.is-active {
  outline: 2px solid var(--ui-primary);
  outline-offset: -2px;
}
.grid-cell-arr {
  display: block;
}
.grid-cell-dep {
  display: block;
  font-size: 0.65rem;
  color: var(--ui-text-dimmed);
}
.grid-cell-input {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  text-align: center;
  font: inherit;
  font-variant-numeric: tabular-nums;
  color: inherit;
}
.shake {
  animation: grid-cell-shake 0.3s;
}
@keyframes grid-cell-shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-3px); }
  75% { transform: translateX(3px); }
}
</style>
