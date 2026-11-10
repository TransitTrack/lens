<script setup lang="ts">
import {useEditorIdentity} from '~/composables/useEditorIdentity'

const props = defineProps<{ selectedIds: string[]; applying?: boolean }>()
const emit = defineEmits<{
  apply: [payload: { label: string | null; editor: string }]
}>()

const {name: editorName} = useEditorIdentity()

const label = ref('')
const editor = ref('')

watch(
  () => editorName.value,
  (v) => (editor.value = v ?? editor.value),
  {immediate: true},
)

function submit() {
  const name = editor.value.trim()
  if (!name || !props.selectedIds.length) return
  editorName.value = name
  emit('apply', {label: label.value.trim() || null, editor: name})
}
</script>

<template>
  <aside class="sticky bottom-4 z-10 rounded-md border border-primary/30 bg-default/95 p-4 shadow-lg backdrop-blur sm:p-5">
    <div class="flex flex-col gap-4 lg:flex-row lg:items-center">
      <div class="flex min-w-44 items-center gap-3">
        <div class="grid size-10 place-items-center rounded-full bg-primary/10 text-primary">
          <UIcon name="i-lucide-list-checks" class="size-5" />
        </div>
        <div>
          <p class="text-sm font-semibold text-highlighted">{{ $t('optimize.ready') }}</p>
          <p class="text-sm text-muted">{{ $t('optimize.changesSelected', {count: selectedIds.length}, selectedIds.length) }}</p>
        </div>
      </div>
      <div class="grid flex-1 gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(10rem,1fr)_12rem_auto]">
        <UInput v-model="label" :placeholder="$t('optimize.labelPlaceholder')" />
        <UInput v-model="editor" :placeholder="$t('drafts.yourName')" />
        <UButton
          :loading="applying"
          :disabled="!selectedIds.length || !editor.trim()"
          :label="$t('optimize.applyToDraft')"
          icon="i-lucide-arrow-right"
          class="justify-center"
          @click="submit"
        />
      </div>
    </div>
  </aside>
</template>
